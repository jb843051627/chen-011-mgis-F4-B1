package com.fc.v2;

import com.fc.v2.mapper.auto.TMgisQualLineMapper;
import com.fc.v2.model.auto.TMgisQualLine;
import com.fc.v2.service.impl.TMgisQualLineServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测绘成果质检判定线：生效窗口、抢档顺位、顶格归高档、状态口径。
 */
public class QualLineIT {

    private MgisDb db;
    private TMgisQualLineMapper mapper;
    private TMgisQualLineServiceImpl svc;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        mapper = db.mapper(TMgisQualLineMapper.class);
        svc = new TMgisQualLineServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisQualLineMapper", mapper);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisQualLine line(long id, String code, int prio, int status,
                               String start, String end) {
        TMgisQualLine r = new TMgisQualLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName("线" + code);
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("20"));
        r.setTh3Max(new BigDecimal("30"));
        r.setPriority(prio);
        r.setStatus(status);
        if (start != null) {
            r.setEffStart(MgisDb.ts(start));
        }
        if (end != null) {
            r.setEffEnd(MgisDb.ts(end));
        }
        r.setDelFlag(0);
        mapper.insert(r);
        return r;
    }

    // 顶格值按高档（与种子注释「达到上限按高一档」一致）
    @Test
    public void thresholdHitsPromote() {
        line(1L, "R1", 10, 0, null, null);
        assertEquals(2, svc.evaluate("R1", new BigDecimal("10"), MgisDb.ts("2026-09-28 00:00:00")));
        assertEquals(3, svc.evaluate("R1", new BigDecimal("20"), MgisDb.ts("2026-09-28 00:00:00")));
        assertEquals(4, svc.evaluate("R1", new BigDecimal("30"), MgisDb.ts("2026-09-28 00:00:00")));
    }

    // 已让位（status=1）的规则不得再参与求值
    @Test
    public void retiredRuleNotUsable() {
        line(2L, "R2", 10, 1, null, null);
        assertEquals(0, svc.evaluate("R2", new BigDecimal("5"), MgisDb.ts("2026-09-28 00:00:00")));
        assertFalse(svc.usable(2L, MgisDb.ts("2026-09-28 00:00:00")));
    }

    // 生效窗口：启用之日含、交棒之日不含
    @Test
    public void effectiveWindowBoundaries() {
        line(3L, "R3", 10, 0, "2026-09-01 00:00:00", "2026-09-30 00:00:00");
        assertTrue(svc.usable(3L, MgisDb.ts("2026-09-01 00:00:00")));
        assertTrue(svc.usable(3L, MgisDb.ts("2026-09-29 23:59:59")));
        assertFalse(svc.usable(3L, MgisDb.ts("2026-09-30 00:00:00")));
        assertFalse(svc.usable(3L, MgisDb.ts("2026-08-31 23:59:59")));
    }

    // 多版本：at 时刻生效的版本才参与；多规则按优先级取最高
    @Test
    public void topPicksHighestPriorityAmongEffective() {
        line(4L, "A", 5, 0, null, null);
        line(5L, "B", 9, 0, null, null);
        List<TMgisQualLine> avail = svc.listAvailable(MgisDb.ts("2026-09-28 00:00:00"));
        assertEquals(2, avail.size());
        assertEquals("B", avail.get(0).getRuleCode());
        // B 档界为 10/20/30，evaluateTop 输入 10 -> 高档 2
        assertEquals(2, svc.evaluateTop(new BigDecimal("10"), MgisDb.ts("2026-09-28 00:00:00")));
        assertEquals(2, svc.countAvailable(MgisDb.ts("2026-09-28 00:00:00")));
    }

    // 同代号多版本：取 at 时刻落在窗口内的那版（启用日含、交棒日不含）
    @Test
    public void sameCodePicksEffectiveVersion() {
        TMgisQualLine oldV = line(6L, "R6", 10, 0, "2026-01-01 00:00:00", "2026-09-01 00:00:00");
        oldV.setTh1Max(new BigDecimal("5"));   // 旧版一档 5：输入 10 在旧版算二档以上
        mapper.updateById(oldV);
        line(7L, "R6", 10, 0, "2026-09-01 00:00:00", null); // 新版一档 10：输入 10 顶格->2

        // 交棒日当天取新版
        assertEquals(2, svc.evaluate("R6", new BigDecimal("10"), MgisDb.ts("2026-09-01 00:00:00")));
        // 交棒日前取旧版：10 > 5 且 <=20 -> 二档
        assertEquals(2, svc.evaluate("R6", new BigDecimal("10"), MgisDb.ts("2026-08-31 00:00:00")));
        // 输入 6：旧版 6>5<=20 -> 二档；新版 6<=10 -> 一档。以此证明确实按窗口取版
        assertEquals(1, svc.evaluate("R6", new BigDecimal("6"), MgisDb.ts("2026-09-01 00:00:00")));
        assertEquals(2, svc.evaluate("R6", new BigDecimal("6"), MgisDb.ts("2026-08-31 00:00:00")));
    }
}
