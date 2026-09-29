package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BusinessException;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.service.ITMgisUseBillService;

/**
 * 成果提供使用逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 签批 / 否决 / 退回与整改回执的过口动作同一接法：一律以库内当前层、
 * 当前已签数、报批状态为前置条件做条件更新（CAS），并发两口同时下手时
 * 抢输者影响行数为 0、返回 null，杜绝读-改-写互相顶掉。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisUseBillServiceImpl implements ITMgisUseBillService {

    /** 末层（层号自 0 起，0..3） */
    private static final int MAX_NODE = 3;
    /** 同层核准方式：任一签认即可 */
    private static final int MODE_OR = 0;
    /** 同层核准方式：两名点齐 */
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMgisUseBillMapper mgisUseBillMapper;

    @Override
    public TMgisUseBill selectTMgisUseBillById(Long id) {
        return this.mgisUseBillMapper.selectOne(new QueryWrapper<TMgisUseBill>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill approve(Long id, String approver, String comment) {
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            // 已核讫 / 已打回不得再签
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int required = requiredSigns(r);

        // 原子加签：层、已签数、状态三者同时为旧值才落库，并发加签只可能成一笔
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("node_no", node)
                .eq("sign_count", signed)
                .eq("status", STATUS_RUNNING)
                .setSql("sign_count = sign_count + 1"));
        if (rows == 0) {
            return null;
        }

        // 齐签的那一笔负责过层（或末层核讫）；条件带上 sign_count = required，
        // 保证过层与加签出自同一笔账，不会重复过层
        if (signed + 1 >= required) {
            boolean lastLayer = node >= MAX_NODE;
            int moved = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                    .eq("id", id)
                    .eq("del_flag", 0)
                    .eq("node_no", node)
                    .eq("status", STATUS_RUNNING)
                    .eq("sign_count", required)
                    .set(lastLayer, "status", STATUS_PASS)
                    .set(!lastLayer, "node_no", node + 1)
                    .set(!lastLayer, "sign_count", 0)
                    .set("remark", comment));
            if (moved == 0) {
                // 加签已落、过层未成（并发被人抢先）：整笔回滚，不许留下"签了没过层"的半截账
                throw new BusinessException("核准状态已变更，请刷新后重试");
            }
        }
        return selectTMgisUseBillById(id);
    }

    @Override
    public TMgisUseBill reject(Long id, String approver, String comment) {
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            // 已核讫 / 已打回不得再否决
            return null;
        }
        // 否决必须真把状态落成"已打回"，不得空走一趟
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("status", STATUS_RUNNING)
                .set("status", STATUS_VETO)
                .set("remark", comment));
        return rows > 0 ? selectTMgisUseBillById(id) : null;
    }

    @Override
    public TMgisUseBill rollback(Long id, String comment) {
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            // 与签批 / 否决同一道门：终态单不退
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("node_no", node)
                .eq("status", STATUS_RUNNING)
                .setSql("node_no = node_no - 1")
                .set("sign_count", 0)
                .set("remark", comment));
        return rows > 0 ? selectTMgisUseBillById(id) : null;
    }

    /** 本层应签人数：两名点齐看 need_count（至少 2），其余任一人签认即过 */
    private int requiredSigns(TMgisUseBill r) {
        if (r.getSignMode() != null && r.getSignMode() == MODE_AND) {
            Integer need = r.getNeedCount();
            return need != null && need > 1 ? need : 2;
        }
        return 1;
    }
}
