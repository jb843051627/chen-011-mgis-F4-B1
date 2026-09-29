package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.ITMgisCheckCardService;

/**
 * 不合格成果整改回执单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisCheckCardServiceImpl implements ITMgisCheckCardService {

    /** 关口上限：0..3，共四档 */
    private static final int MAX_STAGE = 3;
    /** 回执落定：0在办 1已销号 2已封存；除「在办」外皆为终态 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_SETTLED = 1;

    @javax.annotation.Resource
    private TMgisCheckCardMapper mgisCheckCardMapper;

    @Override
    public TMgisCheckCard selectTMgisCheckCardById(Long id) {
        return this.mgisCheckCardMapper.selectOne(new QueryWrapper<TMgisCheckCard>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    public List<TMgisCheckCard> selectTMgisCheckCardList(QueryWrapper<TMgisCheckCard> queryWrapper) {
        QueryWrapper<TMgisCheckCard> wrapper = queryWrapper == null ? new QueryWrapper<>() : queryWrapper;
        wrapper.eq("del_flag", 0);
        return this.mgisCheckCardMapper.selectList(wrapper);
    }

    /**
     * 推进一档：每次只走一关；走到顶关即封存终态；已终态再推拒绝。
     * 以「关口 + 终态」为乐观条件做原子更新，杜绝并发两头同时过口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisCheckCard advance(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        if (from >= MAX_STAGE) {
            return null;
        }
        int to = from + 1;
        // 走到顶关即销号落定
        int toStatus = to >= MAX_STAGE ? STATUS_SETTLED : STATUS_RUNNING;

        int rows = step(id, from, to, toStatus, remark);
        return rows > 0 ? selectTMgisCheckCardById(id) : null;
    }

    /**
     * 回退一档：每次只退一关；已封存终态不得翻案；第 0 关无处可退。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisCheckCard rollback(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        if (from <= 0) {
            return null;
        }
        int to = from - 1;
        int rows = step(id, from, to, STATUS_RUNNING, remark);
        return rows > 0 ? selectTMgisCheckCardById(id) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContent(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        TMgisCheckCard patch = new TMgisCheckCard();
        patch.setContent(remark);
        return this.mgisCheckCardMapper.update(patch, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id).eq("del_flag", 0).eq("status", STATUS_RUNNING)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null || isTerminal(r)) {
            return false;
        }
        // 软删：仅在办可删
        return this.mgisCheckCardMapper.delete(new QueryWrapper<TMgisCheckCard>()
                .eq("id", id).eq("del_flag", 0).eq("status", STATUS_RUNNING)) > 0;
    }

    private boolean isTerminal(TMgisCheckCard r) {
        // 已销号(1)、已封存(2)均不得再流转/改/删
        return r.getStatus() != null && r.getStatus() != STATUS_RUNNING;
    }

    /**
     * 单关流转的原子更新：where 上钉住「原关口、在办、未删除」，
     * 两个并发动作只有一个能匹配到行，另一个 rows=0 被拒。
     */
    private int step(Long id, int expectStage, int toStage, int toStatus, String remark) {
        TMgisCheckCard patch = new TMgisCheckCard();
        patch.setStage(toStage);
        patch.setStatus(toStatus);
        patch.setLastAction(remark);
        return this.mgisCheckCardMapper.update(patch, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("stage", expectStage)
                .eq("status", STATUS_RUNNING));
    }
}
