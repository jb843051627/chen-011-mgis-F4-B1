package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.exception.BusinessException;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.mapper.auto.TMgisDutyTaskMapper;
import com.fc.v2.mapper.auto.TMgisQualLineMapper;
import com.fc.v2.mapper.auto.TMgisScopeCardMapper;
import com.fc.v2.mapper.auto.TMgisSubmitRowMapper;
import com.fc.v2.mapper.auto.TMgisUnitDocMapper;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.model.auto.TMgisDutyTask;
import com.fc.v2.model.auto.TMgisQualLine;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisSubmitRow;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.model.auto.TMgisUseBill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * mgis 一串缺陷的回归测试（纯 Mockito，不起容器、不碰库）。
 *
 * @author fuce
 */
class TMgisBugFixTest {

    @Mock
    private TMgisScopeCardMapper scopeCardMapper;
    @Mock
    private TMgisUnitDocMapper unitDocMapper;
    @Mock
    private TMgisCheckCardMapper checkCardMapper;
    @Mock
    private TMgisUseBillMapper useBillMapper;
    @Mock
    private TMgisSubmitRowMapper submitRowMapper;
    @Mock
    private TMgisDutyTaskMapper dutyTaskMapper;
    @Mock
    private TMgisQualLineMapper qualLineMapper;

    private TMgisScopeCardServiceImpl scopeService;
    private TMgisCheckCardServiceImpl checkService;
    private TMgisUseBillServiceImpl useBillService;
    private TMgisSubmitRowServiceImpl submitService;
    private TMgisDutyTaskServiceImpl dutyService;
    private TMgisQualLineServiceImpl qualService;

    private static TMgisUnitDoc arch(int status) {
        TMgisUnitDoc a = new TMgisUnitDoc();
        a.setId(7L);
        a.setSiteNo("ZZ00");
        a.setSiteName("江南省测绘院");
        a.setStatus(status);
        a.setDelFlag(0);
        a.setTh1Max(new BigDecimal("30"));
        a.setTh2Max(new BigDecimal("120"));
        a.setTh3Max(new BigDecimal("360"));
        return a;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scopeService = new TMgisScopeCardServiceImpl();
        ReflectionTestUtils.setField(scopeService, "baseMapper", scopeCardMapper);
        ReflectionTestUtils.setField(scopeService, "mgisUnitDocMapper", unitDocMapper);

        checkService = new TMgisCheckCardServiceImpl();
        ReflectionTestUtils.setField(checkService, "mgisCheckCardMapper", checkCardMapper);

        useBillService = new TMgisUseBillServiceImpl();
        ReflectionTestUtils.setField(useBillService, "mgisUseBillMapper", useBillMapper);

        submitService = new TMgisSubmitRowServiceImpl();
        ReflectionTestUtils.setField(submitService, "mgisSubmitRowMapper", submitRowMapper);

        dutyService = new TMgisDutyTaskServiceImpl();
        ReflectionTestUtils.setField(dutyService, "mgisDutyTaskMapper", dutyTaskMapper);

        qualService = new TMgisQualLineServiceImpl();
        ReflectionTestUtils.setField(qualService, "mgisQualLineMapper", qualLineMapper);
    }

    // ---------- 作业范围标签卡 ----------

    @Test
    void insert_rejectsRevokedUnit() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(1));
        TMgisScopeCard r = new TMgisScopeCard();
        r.setBillNo("B001");
        r.setSiteId(7);
        r.setQty(new BigDecimal("10"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> scopeService.insertTMgisScopeCard(r));
        assertTrue(ex.getMessage().contains("已注销"));
        verify(scopeCardMapper, never()).insert(any());
    }

    @Test
    void insert_rejectsBlankOrZeroOrNegativeQtyAndBillNo() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        TMgisScopeCard noBill = new TMgisScopeCard();
        noBill.setSiteId(7);
        noBill.setQty(new BigDecimal("1"));
        assertThrows(BusinessException.class, () -> scopeService.insertTMgisScopeCard(noBill));

        TMgisScopeCard zero = new TMgisScopeCard();
        zero.setBillNo("B002");
        zero.setSiteId(7);
        zero.setQty(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> scopeService.insertTMgisScopeCard(zero));

        TMgisScopeCard neg = new TMgisScopeCard();
        neg.setBillNo("B003");
        neg.setSiteId(7);
        neg.setQty(new BigDecimal("-5"));
        assertThrows(BusinessException.class, () -> scopeService.insertTMgisScopeCard(neg));
        verify(scopeCardMapper, never()).insert(any());
    }

    @Test
    void insert_fineAmtMustNotExceedQty() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        TMgisScopeCard r = new TMgisScopeCard();
        r.setBillNo("B004");
        r.setSiteId(7);
        r.setQty(new BigDecimal("10"));
        r.setFineAmt(new BigDecimal("11"));
        assertThrows(BusinessException.class, () -> scopeService.insertTMgisScopeCard(r));
    }

    @Test
    void insert_boundaryThresholdGoesUpOneTier() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        when(scopeCardMapper.selectCount(any())).thenReturn(0);
        when(scopeCardMapper.insert(any())).thenReturn(1);
        TMgisScopeCard r = new TMgisScopeCard();
        r.setBillNo("B005");
        r.setSiteId(7);
        // 正顶一档上限 30，依底档注释应折二档，不再矮一档
        r.setQty(new BigDecimal("30"));
        scopeService.insertTMgisScopeCard(r);
        assertEquals(2, r.getGradeLevel().intValue());
    }

    @Test
    void insert_creatorIsNeverOverwrittenByBillNo() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        when(scopeCardMapper.selectCount(any())).thenReturn(0);
        ArgumentCaptor<TMgisScopeCard> cap = ArgumentCaptor.forClass(TMgisScopeCard.class);
        when(scopeCardMapper.insert(cap.capture())).thenReturn(1);
        TMgisScopeCard r = new TMgisScopeCard();
        r.setBillNo("B006");
        r.setSiteId(7);
        r.setQty(new BigDecimal("5"));
        r.setCreateBy("B006"); // 旧代码把创建人写成卡号
        scopeService.insertTMgisScopeCard(r);
        assertNull(cap.getValue().getCreateBy(), "创建人应留给登录经办人填充，不得是卡号");
    }

    @Test
    void insert_bizDateBeforeYesterdayRejected() {
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        TMgisScopeCard r = new TMgisScopeCard();
        r.setBillNo("B007");
        r.setSiteId(7);
        r.setQty(new BigDecimal("5"));
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -3);
        r.setCreateTime(c.getTime());
        assertThrows(BusinessException.class, () -> scopeService.insertTMgisScopeCard(r));
    }

    @Test
    void update_unchangedBillNoDoesNotFlagDuplicate() {
        TMgisScopeCard existing = new TMgisScopeCard();
        existing.setId(100L);
        existing.setBillNo("B008");
        existing.setSiteId(7);
        when(scopeCardMapper.selectOne(any())).thenReturn(existing);
        when(unitDocMapper.selectOne(any())).thenReturn(arch(0));
        when(scopeCardMapper.update(any(), any())).thenReturn(1);

        TMgisScopeCard r = new TMgisScopeCard();
        r.setId(100L);
        r.setBillNo("B008"); // 卡号原样未动
        r.setSiteId(7);
        r.setQty(new BigDecimal("9"));
        assertEquals(1, scopeService.updateTMgisScopeCard(r));
        verify(scopeCardMapper, never()).selectCount(any());
    }

    @Test
    void update_revokedOriginalSiteStillEditable() {
        TMgisScopeCard existing = new TMgisScopeCard();
        existing.setId(101L);
        existing.setBillNo("B009");
        existing.setSiteId(1);
        when(scopeCardMapper.selectOne(any())).thenReturn(existing);
        when(unitDocMapper.selectOne(any())).thenReturn(arch(1)); // 沿用原单位，已注销
        when(scopeCardMapper.update(any(), any())).thenReturn(1);

        TMgisScopeCard r = new TMgisScopeCard();
        r.setId(101L);
        r.setBillNo("B009");
        r.setQty(new BigDecimal("9"));
        assertEquals(1, scopeService.updateTMgisScopeCard(r));
    }

    // ---------- 整改回执单：状态机 / 并发 ----------

    private TMgisCheckCard card(long id, Integer stage, Integer status) {
        TMgisCheckCard c = new TMgisCheckCard();
        c.setId(id);
        c.setStage(stage);
        c.setStatus(status);
        c.setDelFlag(0);
        return c;
    }

    @Test
    void advance_movesExactlyOneStage() {
        when(checkCardMapper.selectOne(any())).thenReturn(card(1L, 0, 0), card(1L, 1, 0));
        when(checkCardMapper.update(any(), any())).thenReturn(1);
        TMgisCheckCard out = checkService.advance(1L, "过口");
        assertEquals(1, out.getStage().intValue());
    }

    @Test
    void advance_terminalRejected() {
        when(checkCardMapper.selectOne(any())).thenReturn(card(2L, 5, 1));
        assertNull(checkService.advance(2L, "x"));
        verify(checkCardMapper, never()).update(any(), any());
    }

    @Test
    void rollback_movesExactlyOneStageNotZero() {
        when(checkCardMapper.selectOne(any())).thenReturn(card(3L, 3, 0), card(3L, 2, 0));
        when(checkCardMapper.update(any(), any())).thenReturn(1);
        TMgisCheckCard out = checkService.rollback(3L, "退口");
        assertEquals(2, out.getStage().intValue());
    }

    @Test
    void rollback_sealedRejected() {
        when(checkCardMapper.selectOne(any())).thenReturn(card(4L, 2, 2));
        assertNull(checkService.rollback(4L, "x"));
        verify(checkCardMapper, never()).update(any(), any());
    }

    // ---------- 逐级核准单 ----------

    private TMgisUseBill bill(long id, Integer node, Integer mode, Integer need, Integer signed, Integer status) {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(id);
        b.setNodeNo(node);
        b.setSignMode(mode);
        b.setNeedCount(need);
        b.setSignCount(signed);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    @Test
    void reject_actuallyVetoes() {
        when(useBillMapper.selectOne(any())).thenReturn(bill(1L, 0, 0, 1, 0, 0),
                bill(1L, 0, 0, 1, 0, 2));
        when(useBillMapper.update(any(), any())).thenReturn(1);
        TMgisUseBill out = useBillService.reject(1L, "张三", "不合规");
        assertEquals(2, out.getStatus().intValue());
    }

    @Test
    void reject_onFinalRejectedTwice() {
        when(useBillMapper.selectOne(any())).thenReturn(bill(2L, 0, 0, 1, 0, 2));
        assertNull(useBillService.reject(2L, "张三", "x"));
        verify(useBillMapper, never()).update(any(), any());
    }

    @Test
    void approve_orModePassesOneLayer() {
        when(useBillMapper.selectOne(any())).thenReturn(bill(3L, 0, 0, 1, 0, 0),
                bill(3L, 1, 0, 1, 0, 0));
        when(useBillMapper.update(any(), any())).thenReturn(1);
        TMgisUseBill out = useBillService.approve(3L, "张三", "同意");
        assertEquals(1, out.getNodeNo().intValue());
        // 加签与过层两笔落库
        verify(useBillMapper, times(2)).update(any(), any());
    }

    @Test
    void approve_andModeWaitsForSecondSignature() {
        when(useBillMapper.selectOne(any())).thenReturn(bill(4L, 0, 1, 2, 0, 0),
                bill(4L, 0, 1, 2, 1, 0));
        when(useBillMapper.update(any(), any())).thenReturn(1);
        TMgisUseBill out = useBillService.approve(4L, "张三", "同意");
        // 两名点齐：只加签不过层
        assertEquals(0, out.getNodeNo().intValue());
        verify(useBillMapper, times(1)).update(any(), any());
    }

    // ---------- 批量汇交 ----------

    private TMgisSubmitRow row(String code, String qty) {
        TMgisSubmitRow r = new TMgisSubmitRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void submit_emptyOrNullBatchReturnsZero() {
        assertEquals(0, submitService.submitBatch("P1", null));
        assertEquals(0, submitService.submitBatch("P1", new ArrayList<>()));
        verify(submitRowMapper, never()).insert(any());
    }

    @Test
    void submit_overLimitReturnsMinusOneNoInsert() {
        when(submitRowMapper.selectCount(any())).thenReturn(0);
        List<TMgisSubmitRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("C" + i, "1"));
        }
        assertEquals(-1, submitService.submitBatch("P2", rows));
        verify(submitRowMapper, never()).insert(any());
    }

    @Test
    void submit_validRowsInIllegalRowsRecordedAndCountMatches() {
        when(submitRowMapper.selectCount(any())).thenReturn(0);
        when(submitRowMapper.insert(any())).thenReturn(1);
        List<TMgisSubmitRow> rows = Arrays.asList(row("C1", "1"), row(null, "1"), row("C3", "2"));
        int ok = submitService.submitBatch("P3", rows);
        assertEquals(2, ok);
        // 非法行不再导致整批作废：三条都留账
        verify(submitRowMapper, times(3)).insert(any());
        assertEquals(1, rows.get(0).getStatus().intValue());
        assertEquals(2, rows.get(1).getStatus().intValue());
        assertEquals(1, rows.get(2).getStatus().intValue());
    }

    @Test
    void submit_duplicateBatchNotReinserted() {
        when(submitRowMapper.selectCount(any())).thenReturn(5, 3);
        int ok = submitService.submitBatch("P4", Arrays.asList(row("C1", "1")));
        assertEquals(3, ok);
        verify(submitRowMapper, never()).insert(any());
    }

    // ---------- 到期提醒 ----------

    @Test
    void runOnce_emptyListNoNpe() {
        when(dutyTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertEquals(0, dutyService.runOnce(new Date()));
    }

    @Test
    void listDue_includesExactlyDueMoment() throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date at = f.parse("2026-09-28 09:00:00");
        TMgisDutyTask t = new TMgisDutyTask();
        t.setId(1L);
        t.setStatus(0);
        t.setAmount(new BigDecimal("2"));
        t.setDueAt(at); // 恰好到期
        when(dutyTaskMapper.selectList(any())).thenReturn(Arrays.asList(t));
        List<TMgisDutyTask> due = dutyService.listDue(at);
        assertEquals(1, due.size());
    }

    @Test
    void runOnce_badAmountMarkedFailAndSkipped() {
        TMgisDutyTask bad = new TMgisDutyTask();
        bad.setId(2L);
        bad.setStatus(0);
        bad.setAmount(new BigDecimal("-1"));
        bad.setDueAt(new Date(System.currentTimeMillis() - 1000));
        when(dutyTaskMapper.selectList(any())).thenReturn(Arrays.asList(bad));
        when(dutyTaskMapper.update(any(), any())).thenReturn(1);
        assertEquals(0, dutyService.runOnce(new Date()));
    }

    // ---------- 质检判定线 ----------

    private TMgisQualLine line(String code, Integer priority, String th1, String start, String end, int status)
            throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TMgisQualLine l = new TMgisQualLine();
        l.setRuleCode(code);
        l.setPriority(priority);
        l.setTh1Max(new BigDecimal(th1));
        l.setTh2Max(new BigDecimal("50"));
        l.setTh3Max(new BigDecimal("80"));
        l.setEffStart(f.parse(start));
        if (end != null) {
            l.setEffEnd(f.parse(end));
        }
        l.setStatus(status);
        l.setDelFlag(0);
        return l;
    }

    @Test
    void evaluateTop_picksHighestPriorityAndBoundaryGoesUp() throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date at = f.parse("2026-09-28 09:00:00");
        // 低优先级 th1=10；高优先级 th1=5，入参 10 对高优先级应落到 2 档之外
        when(qualLineMapper.selectList(any())).thenReturn(Arrays.asList(
                line("LOW", 1, "10", "2026-01-01 00:00:00", null, 0),
                line("TOP", 9, "5", "2026-01-01 00:00:00", null, 0)));
        // 边界：入参正顶高优先级一档上限 5 → 高二档
        assertEquals(2, qualService.evaluateTop(new BigDecimal("5"), at));
    }

    @Test
    void usable_matchesListWindowRule() throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date at = f.parse("2026-09-28 09:00:00");
        TMgisQualLine retired = line("R", 1, "10", "2026-01-01 00:00:00", null, 1);
        when(qualLineMapper.selectById(9L)).thenReturn(retired);
        assertEquals(false, qualService.usable(9L, at));

        TMgisQualLine active = line("A", 1, "10", "2026-01-01 00:00:00", null, 0);
        when(qualLineMapper.selectById(8L)).thenReturn(active);
        assertTrue(qualService.usable(8L, at));
    }
}
