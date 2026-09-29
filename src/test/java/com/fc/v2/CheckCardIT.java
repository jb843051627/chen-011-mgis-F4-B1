package com.fc.v2;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.impl.TMgisCheckCardServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 不合格成果整改回执单（状态机）：步进、终局、并发口径实测。
 */
public class CheckCardIT {

    private MgisDb db;
    private TMgisCheckCardMapper mapper;
    private TMgisCheckCardServiceImpl svc;

    @BeforeEach
    public void setUp() {
        db = MgisDb.start();
        mapper = db.mapper(TMgisCheckCardMapper.class);
        svc = new TMgisCheckCardServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mgisCheckCardMapper", mapper);
    }

    @AfterEach
    public void tearDown() {
        db.close();
    }

    private TMgisCheckCard seed(long id, Integer stage, Integer status, int delFlag) {
        TMgisCheckCard c = new TMgisCheckCard();
        c.setId(id);
        c.setBizNo("Z" + id);
        c.setStage(stage);
        c.setStatus(status);
        c.setDelFlag(delFlag);
        mapper.insert(c);
        return c;
    }

    // 推进必须是一档，不是两档
    @Test
    public void advanceGoesOneStep() {
        seed(1L, 0, 0, 0);
        TMgisCheckCard r = svc.advance(1L, "过口");
        assertEquals(1, r.getStage().intValue());
    }

    // 到顶即终局（已销号/封存），再推应被拒
    @Test
    public void terminalCannotAdvance() {
        seed(2L, 3, 2, 0);
        assertNull(svc.advance(2L, "再推"));
        assertEquals(3, mapper.selectById(2L).getStage().intValue());
    }

    // 推到顶那一档应落终态（已销号）
    @Test
    public void reachingTopSettlesTerminal() {
        seed(3L, 2, 0, 0);
        TMgisCheckCard r = svc.advance(3L, "收口");
        assertEquals(3, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    // 回退只退一档，不是一把归零
    @Test
    public void rollbackGoesOneStep() {
        seed(4L, 2, 0, 0);
        TMgisCheckCard r = svc.rollback(4L, "退一档");
        assertEquals(1, r.getStage().intValue());
    }

    // 已封存终局不得回退（终局上闩）
    @Test
    public void terminalCannotRollback() {
        seed(5L, 3, 2, 0);
        assertNull(svc.rollback(5L, "翻案"));
        assertEquals(3, mapper.selectById(5L).getStage().intValue());
    }

    // 已办结内容不得再改 / 不得删除
    @Test
    public void terminalContentLocked() {
        seed(6L, 3, 2, 0);
        assertFalse(svc.updateContent(6L, "想改"));
        assertFalse(svc.remove(6L));
        assertNotNull(mapper.selectById(6L));
    }

    // 台账只认未删单，且接得住状态筛选
    @Test
    public void listFiltersDeletedAndHonorsCondition() {
        seed(7L, 0, 0, 0);
        seed(8L, 1, 2, 0);
        seed(9L, 0, 0, 1);

        List<TMgisCheckCard> all = svc.selectTMgisCheckCardList(new QueryWrapper<TMgisCheckCard>());
        assertEquals(2, all.size());
        List<TMgisCheckCard> terminal = svc.selectTMgisCheckCardList(
                new QueryWrapper<TMgisCheckCard>().eq("status", 2));
        assertEquals(1, terminal.size());
        assertEquals("Z8", terminal.get(0).getBizNo());
    }

    // 按主键回查也要排除已删除（存量读法一把尺）
    @Test
    public void getByIdExcludesDeleted() {
        seed(10L, 0, 0, 1);
        assertNull(svc.selectTMgisCheckCardById(10L));
    }
}
