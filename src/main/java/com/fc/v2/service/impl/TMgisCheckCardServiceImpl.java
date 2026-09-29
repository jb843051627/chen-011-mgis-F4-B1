package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.ITMgisCheckCardService;

/**
 * 不合格成果整改回执单 Service业务层处理（state-machine 形状：单据流转）
 *
 * 推进 / 回退是同一桩过口动作的两面，收口在同一条原子 CAS 流转上：
 * 以库内当前关口与落定状态为前置条件做条件更新，并发两口同时下手时，
 * 条件不成立者影响行数为 0、返回 null，不允许读-改-写互相覆盖。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisCheckCardServiceImpl implements ITMgisCheckCardService {

    /** 最高关口（关口自 0 起，0..5） */
    private static final int MAX_STAGE = 5;
    /** 回执落定：在办 */
    private static final int STATUS_RUNNING = 0;
    /** 回执落定：已销号 */
    private static final int STATUS_CLOSED = 1;
    /** 回执落定：已封存，任何过口动作都不再受理 */
    private static final int STATUS_SEALED = 2;

    @javax.annotation.Resource
    private TMgisCheckCardMapper mgisCheckCardMapper;

    @Override
    public TMgisCheckCard selectTMgisCheckCardById(Long id) {
        return this.mgisCheckCardMapper.selectOne(new QueryWrapper<TMgisCheckCard>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    public List<TMgisCheckCard> selectTMgisCheckCardList(QueryWrapper<TMgisCheckCard> queryWrapper) {
        return this.mgisCheckCardMapper.selectList(queryWrapper);
    }

    @Override
    public TMgisCheckCard advance(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null || (r.getStatus() != null && r.getStatus() != STATUS_RUNNING)) {
            // 已销号 / 已封存不得再推进
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        if (from >= MAX_STAGE) {
            return null;
        }
        int to = from + 1;
        int toStatus = to >= MAX_STAGE ? STATUS_CLOSED : STATUS_RUNNING;
        int rows = this.mgisCheckCardMapper.update(null, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("stage", from)
                .eq("status", STATUS_RUNNING)
                .setSql("stage = " + to)
                .set("status", toStatus)
                .set("last_action", remark));
        return rows > 0 ? selectTMgisCheckCardById(id) : null;
    }

    @Override
    public TMgisCheckCard rollback(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null) {
            return null;
        }
        // 与推进同一道门：已销号 / 已封存一律不退，只在在办单上回一档
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        if (from <= 0) {
            return null;
        }
        int to = from - 1;
        int rows = this.mgisCheckCardMapper.update(null, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("stage", from)
                .eq("status", STATUS_RUNNING)
                .setSql("stage = " + to)
                .set("last_action", remark));
        return rows > 0 ? selectTMgisCheckCardById(id) : null;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null) {
            return false;
        }
        // 已办结（销号 / 封存）不得再改记事
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return false;
        }
        return this.mgisCheckCardMapper.update(null, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("status", STATUS_RUNNING)
                .set("content", remark)) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMgisCheckCard r = selectTMgisCheckCardById(id);
        if (r == null) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            // 已办结不得删除
            return false;
        }
        return this.mgisCheckCardMapper.update(null, new UpdateWrapper<TMgisCheckCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("status", STATUS_RUNNING)
                .set("del_flag", 1)) > 0;
    }
}
