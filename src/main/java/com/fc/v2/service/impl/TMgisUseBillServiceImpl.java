package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.service.ITMgisUseBillService;

/**
 * 成果提供使用逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisUseBillServiceImpl implements ITMgisUseBillService {

    /** 最后一层：层 0..3，共四层 */
    private static final int MAX_NODE = 3;
    /** 报批情形 0在核 1已核讫 2已打回；除「在核」外皆为终态 */
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

    /**
     * 签批一票：要求签批人非空；每次只进一层、已签人数加一；在最后一层签批落「已核讫」。
     * 已终态（核讫/打回）不得再签。where 钉住「原层 + 在核」做原子更新防并发。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill approve(Long id, String approver, String comment) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null || !isRunning(r)) {
            return null;
        }
        int from = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (from > MAX_NODE) {
            return null;
        }
        int to = Math.min(from + 1, MAX_NODE);
        int toStatus = to >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING;
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;

        TMgisUseBill patch = new TMgisUseBill();
        patch.setNodeNo(to);
        patch.setStatus(toStatus);
        patch.setSignCount(signed);
        int rows = this.mgisUseBillMapper.update(patch, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("del_flag", 0)
                .eq("node_no", from).eq("status", STATUS_RUNNING));
        return rows > 0 ? selectTMgisUseBillById(id) : null;
    }

    /**
     * 否决：在核中的单据直接落「已打回」终态；已终态不得再否决。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill reject(Long id, String approver, String comment) {
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null || !isRunning(r)) {
            return null;
        }
        TMgisUseBill patch = new TMgisUseBill();
        patch.setStatus(STATUS_VETO);
        int rows = this.mgisUseBillMapper.update(patch, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("del_flag", 0).eq("status", STATUS_RUNNING));
        return rows > 0 ? selectTMgisUseBillById(id) : null;
    }

    /**
     * 退回上一环节：每次只退一层；第 0 层无处可退；已终态不得退。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill rollback(Long id, String comment) {
        TMgisUseBill r = selectTMgisUseBillById(id);
        if (r == null || !isRunning(r)) {
            return null;
        }
        int from = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (from <= 0) {
            return null;
        }
        int to = from - 1;
        TMgisUseBill patch = new TMgisUseBill();
        patch.setNodeNo(to);
        patch.setStatus(STATUS_RUNNING);
        int rows = this.mgisUseBillMapper.update(patch, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("del_flag", 0)
                .eq("node_no", from).eq("status", STATUS_RUNNING));
        return rows > 0 ? selectTMgisUseBillById(id) : null;
    }

    private boolean isRunning(TMgisUseBill r) {
        return r.getStatus() != null && r.getStatus() == STATUS_RUNNING;
    }
}
