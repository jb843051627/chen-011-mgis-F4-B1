package com.fc.v2;

import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.service.impl.TMgisUseBillServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 成果提供使用逐级核准单（审批链）：步进、终局、并发口径实测。
 */
public class UseBillIT {

    private MgisDb db;
    private TMgisUseBillMapper mapper;
    private TMgisUseBillServiceImpl svc;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        mapper = db.mapper(TMgisUseBillMapper.class);
        svc = new TMgisUseBillServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisUseBillMapper", mapper);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisUseBill seed(long id, Integer node, Integer status, int signCount) {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(id);
        b.setBillNo("H" + id);
        b.setNodeNo(node);
        b.setStatus(status);
        b.setSignCount(signCount);
        b.setDelFlag(0);
        mapper.insert(b);
        return b;
    }

    // 签批一次只进一层、已签人数加一
    @Test
    public void approveGoesOneNode() {
        seed(1L, 0, 0, 0);
        TMgisUseBill r = svc.approve(1L, "甲", "同意");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(1, r.getSignCount().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    // 顶到最后一层落「已核讫」终态
    @Test
    public void reachingTopPasses() {
        seed(2L, 3, 0, 3);
        TMgisUseBill r = svc.approve(2L, "甲", "收口");
        assertEquals(3, r.getNodeNo().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    // 已核讫 / 已打回，门已上闩：再签、再退都被拒
    @Test
    public void terminalIsLockedBothWays() {
        seed(3L, 3, 1, 4);
        assertNull(svc.approve(3L, "甲", "再签"));
        assertNull(svc.rollback(3L, "翻案"));

        seed(4L, 1, 2, 1);
        assertNull(svc.approve(4L, "甲", "复活"));
        assertNull(svc.rollback(4L, "翻案"));
    }

    // 否决落「已打回」终态
    @Test
    public void rejectSettlesVeto() {
        seed(5L, 1, 0, 1);
        TMgisUseBill r = svc.reject(5L, "乙", "不行");
        assertEquals(2, r.getStatus().intValue());
    }

    // 退回一次只退一层；第 0 层无处可退
    @Test
    public void rollbackOneNodeAndFloor() {
        seed(6L, 2, 0, 2);
        assertEquals(1, svc.rollback(6L, "退").getNodeNo().intValue());
        seed(7L, 0, 0, 0);
        assertNull(svc.rollback(7L, "再退"));
    }

    // 无签批人不得签
    @Test
    public void approveRequiresApprover() {
        seed(8L, 0, 0, 0);
        assertNull(svc.approve(8L, "  ", "空"));
        assertEquals(0, mapper.selectById(8L).getNodeNo().intValue());
    }
}
