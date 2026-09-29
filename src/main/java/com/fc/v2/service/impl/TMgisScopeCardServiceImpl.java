package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TMgisScopeCardMapper;
import com.fc.v2.mapper.auto.TMgisUnitDocMapper;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.service.ITMgisScopeCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作业范围标签建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMgisScopeCardServiceImpl extends ServiceImpl<TMgisScopeCardMapper, TMgisScopeCard> implements ITMgisScopeCardService {

    /** 标签卡号：字母/数字开头，仅允许字母数字、下划线、连字符，3..64 位 */
    private static final Pattern BILL_NO_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$");

    @Autowired
    private TMgisUnitDocMapper mgisUnitDocMapper;

    @Override
    public TMgisScopeCard selectTMgisScopeCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TMgisScopeCard> selectTMgisScopeCardList(Wrapper<TMgisScopeCard> queryWrapper) {
        // 分页一律由 Controller 的 startPage() 决定，此处不得再私设页码；
        // 条件以调用方传入的为准，统一补上「未删除」口径。
        QueryWrapper<TMgisScopeCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TMgisScopeCard>) queryWrapper
                : new QueryWrapper<>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTMgisScopeCard(TMgisScopeCard record) {
        if (record == null) {
            return 0;
        }

        TMgisUnitDoc arch = validateAndFill(record, true);
        if (arch == null) {
            return 0;
        }
        if (existsBillNo(record.getBillNo(), null)) {
            return 0;
        }
        record.setGradeLevel(gradeOf(arch, record.getQty()));
        record.setDelFlag(0);
        try {
            return this.baseMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发同号：唯一索引兜底，绝不允许重号并排入库
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTMgisScopeCard(TMgisScopeCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TMgisScopeCard old = this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (old == null) {
            return 0;
        }
        // 支持部分字段提交（如只改备注）：未提交的关键字段以库内原值补齐后再校验。
        // 业务日期只在建档时设定，编辑不改不校（避免历史卡被今日新约束误拦）。
        if (StringUtils.isEmpty(record.getBillNo())) {
            record.setBillNo(old.getBillNo());
        }
        if (record.getSiteId() == null) {
            record.setSiteId(old.getSiteId());
        }
        if (record.getQty() == null) {
            record.setQty(old.getQty());
        }

        TMgisUnitDoc arch = validateAndFill(record, false);
        if (arch == null) {
            return 0;
        }
        // 卡号相对库内原值确有变更才查重；原样未动直接放行
        if (StringUtils.isNotEmpty(record.getBillNo())
                && !record.getBillNo().equals(old.getBillNo())
                && existsBillNo(record.getBillNo(), record.getId())) {
            return 0;
        }

        TMgisScopeCard patch = new TMgisScopeCard();
        patch.setBillNo(record.getBillNo());
        patch.setBillDate(record.getBillDate());
        patch.setSiteId(record.getSiteId());
        patch.setSiteNo(record.getSiteNo());
        patch.setQty(record.getQty());
        patch.setFineAmt(record.getFineAmt());
        patch.setGradeLevel(gradeOf(arch, record.getQty()));
        patch.setStatus(record.getStatus());
        patch.setRemark(record.getRemark());

        try {
            return this.baseMapper.update(patch, new UpdateWrapper<TMgisScopeCard>()
                    .eq("id", record.getId())
                    .eq("del_flag", 0));
        } catch (DuplicateKeyException e) {
            return 0;
        }
    }

    @Override
    public int deleteTMgisScopeCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTMgisScopeCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    /**
     * 建档/改卡共用的入口校验，通过则把卡号、siteNo 归一后回填：
     * 卡号必填且合规；底档必须在册（未删除、未注销/停用）；挂用类别总数须为正。
     * 业务日期「不得早于昨日」只在建档（checkBillDate=true）时卡。不通过返回 null。
     */
    private TMgisUnitDoc validateAndFill(TMgisScopeCard record, boolean checkBillDate) {
        String billNo = record.getBillNo() == null ? null : record.getBillNo().trim();
        if (StringUtils.isEmpty(billNo) || !BILL_NO_PATTERN.matcher(billNo).matches()) {
            return null;
        }
        record.setBillNo(billNo);

        if (record.getSiteId() == null) {
            return null;
        }
        TMgisUnitDoc arch = mgisUnitDocMapper.selectOne(new QueryWrapper<TMgisUnitDoc>()
                .eq("id", record.getSiteId())
                .eq("del_flag", 0));
        // 档案不存在，或已注销（停用）：一律不得挂用
        if (arch == null || (arch.getStatus() != null && arch.getStatus() == 1)) {
            return null;
        }

        if (record.getQty() == null || record.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (checkBillDate && record.getBillDate() != null && record.getBillDate().before(yesterdayStart())) {
            return null;
        }

        // siteNo 以底档为准，页面传什么都不算数
        record.setSiteNo(arch.getSiteNo());
        return arch;
    }

    private static java.util.Date yesterdayStart() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private boolean existsBillNo(String billNo, Long excludeId) {
        QueryWrapper<TMgisScopeCard> qw = new QueryWrapper<TMgisScopeCard>()
                .eq("bill_no", billNo)
                .eq("del_flag", 0);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        Integer cnt = this.baseMapper.selectCount(qw);
        return cnt != null && cnt > 0;
    }

    /**
     * 分档：取值「达到」上限即按高一档折算（与底档种子口径一致），故各档皆为严格小于。
     * qty &lt; th1 一档；&lt; th2 二档；&lt; th3 三档；其余四档。
     */
    private Integer gradeOf(TMgisUnitDoc arch, BigDecimal qty) {
        if (arch.getTh1Max() == null || arch.getTh2Max() == null || arch.getTh3Max() == null) {
            return null;
        }
        if (qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (qty.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
