package com.fc.v2;

import com.fc.v2.mapper.auto.TMgisDutyTaskMapper;
import com.fc.v2.model.auto.TMgisDutyTask;
import com.fc.v2.service.impl.TMgisDutyTaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 到期提醒派发条目：空表、恰好到期边界、只派未处理条目、失败跳过继续。
 */
public class DutyTaskIT {

    private MgisDb db;
    private TMgisDutyTaskMapper mapper;
    private TMgisDutyTaskServiceImpl svc;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        mapper = db.mapper(TMgisDutyTaskMapper.class);
        svc = new TMgisDutyTaskServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisDutyTaskMapper", mapper);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisDutyTask seed(long id, String due, String amount, int status) {
        TMgisDutyTask t = new TMgisDutyTask();
        t.setId(id);
        t.setItemNo("T" + id);
        if (due != null) {
            t.setDueAt(MgisDb.ts(due));
        }
        if (amount != null) {
            t.setAmount(new BigDecimal(amount));
        }
        t.setStatus(status);
        t.setDelFlag(0);
        mapper.insert(t);
        return t;
    }

    @Test
    public void emptyTableSafe() {
        Date at = MgisDb.ts("2026-09-28 09:00:00");
        assertTrue(svc.listDue(at).isEmpty());
        assertEquals(0, svc.runOnce(at));
    }

    // 恰好到期（同一刻）必须算到期，不能漏
    @Test
    public void exactlyDueIsIncluded() {
        seed(1L, "2026-09-28 09:00:00", "3", 0);
        List<TMgisDutyTask> due = svc.listDue(MgisDb.ts("2026-09-28 09:00:00"));
        assertEquals(1, due.size());
    }

    // 未到期 / 已处理 / 已删除 不入列
    @Test
    public void futureHandledDeletedExcluded() {
        seed(1L, "2026-09-28 09:00:01", "3", 0);
        seed(2L, "2026-09-28 09:00:00", "3", 1);
        TMgisDutyTask del = seed(3L, "2026-09-28 09:00:00", "3", 0);
        del.setDelFlag(1);
        mapper.updateById(del);
        List<TMgisDutyTask> due = svc.listDue(MgisDb.ts("2026-09-28 09:00:00"));
        assertTrue(due.isEmpty());
    }

    // 派发：成功的落「已提醒」，非法（负日数）跳过继续，返回成功条数
    @Test
    public void runOnceMarksAndSkipsBad() {
        seed(1L, "2026-09-28 09:00:00", "3", 0);
        seed(2L, "2026-09-28 09:00:00", "-1", 0);
        seed(3L, "2026-09-28 09:00:00", "2", 0);
        int ok = svc.runOnce(MgisDb.ts("2026-09-28 09:00:00"));
        assertEquals(2, ok);
        assertEquals(1, mapper.selectById(1L).getStatus().intValue());
        assertEquals(2, mapper.selectById(2L).getStatus().intValue(), "送不出去落失败态");
        assertEquals(1, mapper.selectById(3L).getStatus().intValue());
    }
}
