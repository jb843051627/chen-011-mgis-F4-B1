package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisSubmitRowMapper;
import com.fc.v2.model.auto.TMgisSubmitRow;
import com.fc.v2.service.ITMgisSubmitRowService;
import com.fc.v2.util.StringUtils;

/**
 * 成果目录汇交核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisSubmitRowServiceImpl implements ITMgisSubmitRowService {

    /** 单批最多行数，超过整批不收 */
    private static final int MAX_ROWS = 500;
    /** 行进展 0待销 1已入账 2挂驳回（失败明细） */
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMgisSubmitRowMapper mgisSubmitRowMapper;

    @Override
    public TMgisSubmitRow selectTMgisSubmitRowById(Long id) {
        return this.mgisSubmitRowMapper.selectById(id);
    }

    /**
     * 整批提交：
     * 空批返回 0；超上限返回 -1 且一条不入；同批次重复提交直接忽略（返回 0）。
     * 逐行校验——合法的入「已入账」，非法的记「挂驳回」失败明细并保留原始行号；
     * 每行只落一次账，返回成功行数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMgisSubmitRow> rows) {
        if (StringUtils.isEmpty(batchNo) || rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        // 同一批次不得重复入账（含失败明细在内的既有记录）
        Integer existed = this.mgisSubmitRowMapper.selectCount(new QueryWrapper<TMgisSubmitRow>()
                .eq("batch_no", batchNo));
        if (existed != null && existed > 0) {
            return 0;
        }

        int seq = 0;
        int ok = 0;
        for (TMgisSubmitRow r : rows) {
            seq++;
            r.setId(null);
            r.setBatchNo(batchNo);
            // 未给行号时按册内顺序补；给了则原样保留，便于失败行对回原册
            if (r.getRowNo() == null) {
                r.setRowNo(seq);
            }
            r.setDelFlag(0);
            if (isValid(r)) {
                r.setStatus(STATUS_OK);
                ok++;
            } else {
                r.setStatus(STATUS_FAIL);
            }
            this.mgisSubmitRowMapper.insert(r);
        }
        return ok;
    }

    @Override
    public List<TMgisSubmitRow> listErrors(String batchNo) {
        return this.mgisSubmitRowMapper.selectList(new QueryWrapper<TMgisSubmitRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    private boolean isValid(TMgisSubmitRow r) {
        return StringUtils.isNotEmpty(r.getItemCode())
                && r.getQty() != null
                && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
    }
}
