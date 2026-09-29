package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisSubmitRowMapper;
import com.fc.v2.model.auto.TMgisSubmitRow;
import com.fc.v2.service.ITMgisSubmitRowService;

/**
 * 成果目录汇交核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisSubmitRowServiceImpl implements ITMgisSubmitRowService {

    private static final int MAX_ROWS = 500;
    /** 行进展：待销 */
    private static final int STATUS_WAIT = 0;
    /** 行进展：已入账 */
    private static final int STATUS_OK = 1;
    /** 行进展：挂驳回 */
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMgisSubmitRowMapper mgisSubmitRowMapper;

    @Override
    public TMgisSubmitRow selectTMgisSubmitRowById(Long id) {
        return this.mgisSubmitRowMapper.selectOne(new QueryWrapper<TMgisSubmitRow>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMgisSubmitRow> rows) {
        // 空批次：0，不动库
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 批号以入参为准，全批归一，不许各行各记
        String no = batchNo == null ? null : batchNo.trim();
        if (no.isEmpty()) {
            for (TMgisSubmitRow r : rows) {
                if (r.getBatchNo() != null && !r.getBatchNo().trim().isEmpty()) {
                    no = r.getBatchNo().trim();
                    break;
                }
            }
        }
        if (no == null || no.isEmpty()) {
            throw new IllegalArgumentException("成果目录汇交册号不能为空");
        }

        // 同一批次重复提交：不重复入库，交回原批次已入账行数
        Integer existed = this.mgisSubmitRowMapper.selectCount(new QueryWrapper<TMgisSubmitRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer okCnt = this.mgisSubmitRowMapper.selectCount(new QueryWrapper<TMgisSubmitRow>()
                    .eq("batch_no", no).eq("status", STATUS_OK).eq("del_flag", 0));
            return okCnt == null ? 0 : okCnt;
        }

        // 超上限：-1，整批不入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        int seq = 0;
        int ok = 0;
        for (TMgisSubmitRow r : rows) {
            seq++;
            // 原始行次：表单带了用表单的，没带按入册序补，失败明细才对得上原册
            if (r.getRowNo() == null) {
                r.setRowNo(seq);
            }
            r.setBatchNo(no);
            r.setDelFlag(0);
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                // 非法行：挂驳回，留明细，整批继续——但合法行照样要入账
                r.setStatus(STATUS_FAIL);
            } else {
                r.setItemCode(r.getItemCode().trim());
                r.setStatus(STATUS_OK);
                ok++;
            }
            this.mgisSubmitRowMapper.insert(r);
        }
        // 返回的成功行数与实际入账行数严格相等（数须归一）
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
}
