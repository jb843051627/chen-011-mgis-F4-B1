package com.fc.v2;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisScopeCardMapper;
import com.fc.v2.mapper.auto.TMgisUnitDocMapper;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.service.impl.TMgisScopeCardServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 作业范围标签建档卡：把值守人员反映的现象逐条实测钉死。
 */
public class ScopeCardIT {

    private MgisDb db;
    private TMgisScopeCardServiceImpl svc;
    private TMgisUnitDocMapper unitDocMapper;
    private TMgisScopeCardMapper cardMapper;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        unitDocMapper = db.mapper(TMgisUnitDocMapper.class);
        cardMapper = db.mapper(TMgisScopeCardMapper.class);
        svc = new TMgisScopeCardServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "baseMapper", cardMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisUnitDocMapper", unitDocMapper);

        TMgisUnitDoc active = doc(0L, "ZZ00", 0);
        TMgisUnitDoc canceled = doc(1L, "ZZ01", 1);
        unitDocMapper.insert(active);
        unitDocMapper.insert(canceled);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisUnitDoc doc(long id, String no, int status) {
        TMgisUnitDoc d = new TMgisUnitDoc();
        d.setId(id);
        d.setSiteNo(no);
        d.setSiteName("档案" + no);
        d.setTh1Max(new BigDecimal("30"));
        d.setTh2Max(new BigDecimal("120"));
        d.setTh3Max(new BigDecimal("360"));
        d.setStatus(status);
        d.setDelFlag(0);
        return d;
    }

    private TMgisScopeCard card(String billNo, long siteId, String qty) {
        TMgisScopeCard c = new TMgisScopeCard();
        c.setBillNo(billNo);
        c.setSiteId((int) siteId);
        if (qty != null) {
            c.setQty(new BigDecimal(qty));
        }
        return c;
    }

    // 现象①：底档已注销，新卡仍能挂用并入了库
    @Test
    public void canceledArchMustNotBeSelectable() {
        int rows = svc.insertTMgisScopeCard(card("B-0001", 1L, "10"));
        assertEquals(0, rows, "已注销底档不得挂用");
        assertNull(cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", "B-0001")));
    }

    // 现象②：卡号为空 / 随便填也照收
    @Test
    public void blankOrIllegalBillNoRejected() {
        assertEquals(0, svc.insertTMgisScopeCard(card(null, 0L, "10")));
        assertEquals(0, svc.insertTMgisScopeCard(card("  ", 0L, "10")));
        assertEquals(0, svc.insertTMgisScopeCard(card("随便@@@", 0L, "10")));
    }

    // 现象③：siteNo 必须与底档归一，不能由页面说了算
    @Test
    public void siteNoComesFromArch() {
        TMgisScopeCard c = card("B-0002", 0L, "10");
        c.setSiteNo("HACKED");
        assertEquals(1, svc.insertTMgisScopeCard(c));
        TMgisScopeCard saved = cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", "B-0002"));
        assertEquals("ZZ00", saved.getSiteNo());
    }

    // 现象④：qty=0 / 负数竟入库
    @Test
    public void nonPositiveQtyRejected() {
        assertEquals(0, svc.insertTMgisScopeCard(card("B-ZERO", 0L, "0")));
        assertEquals(0, svc.insertTMgisScopeCard(card("B-NEG", 0L, "-5")));
    }

    // 现象⑤：恰好顶在档位上限上，应按注释「达到上限按高一档」，结果矮了一档
    @Test
    public void thresholdHitsPromoteOneLevel() {
        assertLevel("11", 1);
        assertLevel("30", 2);   // 顶一档上限 -> 二档
        assertLevel("120", 3);  // 顶二档上限 -> 三档
        assertLevel("360", 4);  // 顶三档上限 -> 四档
        assertLevel("361", 4);
    }

    private void assertLevel(String qty, int expected) {
        String bill = "B-L" + qty;
        assertEquals(1, svc.insertTMgisScopeCard(card(bill, 0L, qty)));
        assertEquals(expected, cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", bill)).getGradeLevel().intValue());
    }

    // 现象⑥：同一卡号两条并排躺清单
    @Test
    public void duplicateBillNoRejected() {
        assertEquals(1, svc.insertTMgisScopeCard(card("B-DUP", 0L, "10")));
        assertEquals(0, svc.insertTMgisScopeCard(card("B-DUP", 0L, "10")));
    }

    // 现象⑦：旧卡卡号没动，点保存反被重号拦下
    @Test
    public void unchangedBillNoOnEditMustPass() {
        TMgisScopeCard c = card("B-EDIT", 0L, "10");
        assertEquals(1, svc.insertTMgisScopeCard(c));
        TMgisScopeCard saved = cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", "B-EDIT"));

        TMgisScopeCard form = new TMgisScopeCard();
        form.setId(saved.getId());
        form.setBillNo("B-EDIT");
        form.setRemark("仅改备注");
        assertEquals(1, svc.updateTMgisScopeCard(form), "卡号未变不应判重");
    }

    // 现象⑧：创建人记的不是真正经办人（被写成卡号）
    @Test
    public void createByIsOperatorNotBillNo() {
        assertEquals(1, svc.insertTMgisScopeCard(card("B-WHO", 0L, "10")));
        TMgisScopeCard saved = cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", "B-WHO"));
        assertNotEquals("B-WHO", saved.getCreateBy());
        assertEquals("建档员甲", saved.getCreateBy());
    }

    // 业务日期早于昨日，建档被拦；昨日/今日放行
    @Test
    public void billDateBeforeYesterdayRejectedOnCreate() {
        java.util.Calendar cal = java.util.Calendar.getInstance();

        cal.add(java.util.Calendar.DAY_OF_MONTH, -2);
        TMgisScopeCard old2 = card("B-D2", 0L, "10");
        old2.setBillDate(cal.getTime());
        assertEquals(0, svc.insertTMgisScopeCard(old2));

        cal.add(java.util.Calendar.DAY_OF_MONTH, 1); // 回到昨日
        TMgisScopeCard y = card("B-Y", 0L, "10");
        y.setBillDate(cal.getTime());
        assertEquals(1, svc.insertTMgisScopeCard(y));
    }

    // 历史卡只改备注，不因旧业务日期被今日新约束拦下
    @Test
    public void editKeepsOldBillDateWithoutRecheck() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, -30);
        TMgisScopeCard c = card("B-HIST", 0L, "10");
        c.setBillDate(cal.getTime());
        c.setSiteNo("ZZ00");
        c.setGradeLevel(1);
        c.setDelFlag(0);
        cardMapper.insert(c);   // 直接造一条存量历史卡
        TMgisScopeCard saved = cardMapper.selectOne(new QueryWrapper<TMgisScopeCard>().eq("bill_no", "B-HIST"));

        TMgisScopeCard form = new TMgisScopeCard();
        form.setId(saved.getId());
        form.setBillNo("B-HIST");
        form.setRemark("改备注");
        assertEquals(1, svc.updateTMgisScopeCard(form));
    }

    // 唯一索引兜底：绕过 service 直接并插同号，第二条必被数据库拒绝
    @Test
    public void uniqueIndexRejectsDuplicateAtDbLevel() {
        TMgisScopeCard a = card("B-UNIQ", 0L, "10");
        TMgisScopeCard b = card("B-UNIQ", 0L, "10");
        assertEquals(1, cardMapper.insert(a));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> cardMapper.insert(b));
    }

    // 现象⑨：列表被强钉 status=0，且自带 startPage(1,10)，筛选/页大小全失灵
    @Test
    public void listHonorsConditionWithoutHardPaging() {
        svc.insertTMgisScopeCard(card("B-S0", 0L, "10"));
        TMgisScopeCard c2 = card("B-S1", 0L, "10");
        c2.setStatus(1);
        svc.insertTMgisScopeCard(c2);

        // 不带条件：正常卡都在
        List<TMgisScopeCard> all = svc.selectTMgisScopeCardList(new QueryWrapper<TMgisScopeCard>());
        assertEquals(2, all.size());
        // 带 status=1：应只回 1 条（旧实现永远只给 status=0）
        List<TMgisScopeCard> done = svc.selectTMgisScopeCardList(
                new QueryWrapper<TMgisScopeCard>().eq("status", 1));
        assertEquals(1, done.size());
        assertEquals("B-S1", done.get(0).getBillNo());
    }
}
