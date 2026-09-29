package com.fc.v2;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisSubmitRowMapper;
import com.fc.v2.model.auto.TMgisSubmitRow;
import com.fc.v2.service.impl.TMgisSubmitRowServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 成果目录汇交核收行（整批提交）：空批、越限、失败明细、重复提交、计数归一。
 */
public class SubmitRowIT {

    private MgisDb db;
    private TMgisSubmitRowMapper mapper;
    private TMgisSubmitRowServiceImpl svc;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        mapper = db.mapper(TMgisSubmitRowMapper.class);
        svc = new TMgisSubmitRowServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisSubmitRowMapper", mapper);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisSubmitRow row(Integer rowNo, String code, String qty) {
        TMgisSubmitRow r = new TMgisSubmitRow();
        r.setRowNo(rowNo);
        r.setItemCode(code);
        if (qty != null) {
            r.setQty(new BigDecimal(qty));
        }
        return r;
    }

    private long count(String batch) {
        return mapper.selectCount(new QueryWrapper<TMgisSubmitRow>().eq("batch_no", batch));
    }

    @Test
    public void emptyBatchIsZero() {
        assertEquals(0, svc.submitBatch("B", new ArrayList<>()));
        assertEquals(0, svc.submitBatch("B", null));
    }

    @Test
    public void validAndInvalidBookedOnceAndCountMatches() {
        List<TMgisSubmitRow> rows = new ArrayList<>();
        rows.add(row(1, "MAP-1", "10"));
        rows.add(row(2, null, "5"));       // 非法：无代号
        rows.add(row(3, "MAP-3", "0"));    // 非法：数量非正
        rows.add(row(4, "MAP-4", "7"));

        int ok = svc.submitBatch("P2026", rows);
        assertEquals(2, ok, "成功行数须与实际入账一致");
        // 整批 4 条各记一处：2 条入账 + 2 条失败明细，不重复
        assertEquals(4, count("P2026"));
        assertEquals(2, mapper.selectCount(new QueryWrapper<TMgisSubmitRow>()
                .eq("batch_no", "P2026").eq("status", 1)).longValue());
        // 失败明细保留原始行号，按行号升序
        List<TMgisSubmitRow> errs = svc.listErrors("P2026");
        assertEquals(2, errs.size());
        assertEquals(2, errs.get(0).getRowNo().intValue());
        assertEquals(3, errs.get(1).getRowNo().intValue());
    }

    @Test
    public void duplicateBatchNotInsertedAgain() {
        List<TMgisSubmitRow> rows = new ArrayList<>();
        rows.add(row(1, "MAP-1", "10"));
        assertEquals(1, svc.submitBatch("DUP", rows));
        assertEquals(0, svc.submitBatch("DUP", rows));
        assertEquals(1, count("DUP"));
    }

    @Test
    public void overLimitRejectsWholeBatch() {
        List<TMgisSubmitRow> rows = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            rows.add(row(i, "MAP-" + i, "1"));
        }
        assertEquals(-1, svc.submitBatch("BIG", rows));
        assertEquals(0, count("BIG"));
    }
}
