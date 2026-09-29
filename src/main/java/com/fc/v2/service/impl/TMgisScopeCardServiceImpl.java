package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.BusinessException;
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

/**
 * 作业范围标签建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMgisScopeCardServiceImpl extends ServiceImpl<TMgisScopeCardMapper, TMgisScopeCard> implements ITMgisScopeCardService {

    /** 档案情形：在册 */
    private static final int ARCH_ACTIVE = 0;
    /** 标签卡号列长（与 t_mgis_scope_card.bill_no 对齐） */
    private static final int BILL_NO_MAX_LEN = 64;

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
        // 条件、分页一律由调用方给口径，本处不再写死页容/进展
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTMgisScopeCard(TMgisScopeCard record) {
        if (record == null) {
            throw new BusinessException("标签卡内容不能为空");
        }

        String billNo = normalizeBillNo(record.getBillNo());
        record.setBillNo(billNo);
        // 新卡必须挂在在册（未注销、未删除）单位上
        TMgisUnitDoc refArch = loadArch(record.getSiteId() == null ? null : record.getSiteId().longValue(), true);
        record.setSiteId(refArch.getId() == null ? null : refArch.getId().intValue());
        // 表单若带了单位编号，必须与底档一致，对不上茬不收
        if (StringUtils.isNotEmpty(record.getSiteNo())
                && !record.getSiteNo().trim().equals(refArch.getSiteNo())) {
            throw new BusinessException("资质单位编号与底档不一致，请重新选择单位");
        }
        record.setSiteNo(refArch.getSiteNo());

        validateQty(record);
        validateBizDate(record.getCreateTime());
        record.setGradeLevel(levelOf(record.getQty(), refArch));

        // 创建人由 MetaObjectHandler 按当前登录经办人自动填充，不得用卡号顶替
        record.setCreateBy(null);
        record.setDelFlag(0);
        try {
            return this.baseMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发同号：库内唯一索引兜底，与先查后插的拦截同一种收场
            throw new BusinessException("作业范围标签卡号已存在：" + billNo);
        }
    }

    @Override
    public int updateTMgisScopeCard(TMgisScopeCard record) {
        if (record == null || record.getId() == null) {
            throw new BusinessException("缺少标签卡主键");
        }
        TMgisScopeCard existing = selectTMgisScopeCardById(record.getId());
        if (existing == null) {
            throw new BusinessException("标签卡不存在或已删除");
        }

        String billNo = normalizeBillNo(record.getBillNo());
        record.setBillNo(billNo);

        // 表单未回传单位时沿用原卡单位，不把未传误判成没选
        Long siteId = record.getSiteId() != null ? record.getSiteId().longValue()
                : existing.getSiteId() == null ? null : existing.getSiteId().longValue();
        // 改挂别的单位必须挂在册单位；沿用原单位（哪怕其后已注销）不拦编辑
        boolean changedSite = existing.getSiteId() == null
                ? siteId != null
                : !existing.getSiteId().equals(siteId == null ? null : siteId.intValue());
        TMgisUnitDoc refArch = loadArch(siteId, changedSite);
        record.setSiteId(refArch.getId() == null ? null : refArch.getId().intValue());
        if (StringUtils.isNotEmpty(record.getSiteNo())
                && !record.getSiteNo().trim().equals(refArch.getSiteNo())) {
            throw new BusinessException("资质单位编号与底档不一致，请重新选择单位");
        }
        record.setSiteNo(refArch.getSiteNo());

        validateQty(record);

        // 卡号原样未动就不查重；只有真改了号才与别的卡比，避免旧卡自己撞自己
        if (!billNo.equals(existing.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMgisScopeCard>()
                    .eq("bill_no", billNo).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                throw new BusinessException("作业范围标签卡号已存在：" + billNo);
            }
        }
        record.setGradeLevel(levelOf(record.getQty(), refArch));

        // 建档痕迹不随编辑被表单覆盖
        record.setCreateBy(existing.getCreateBy());
        record.setCreateTime(existing.getCreateTime());
        record.setDelFlag(0);
        try {
            return this.baseMapper.update(record, new UpdateWrapper<TMgisScopeCard>()
                    .eq("id", record.getId())
                    .eq("del_flag", 0));
        } catch (DuplicateKeyException e) {
            throw new BusinessException("作业范围标签卡号已存在：" + billNo);
        }
    }

    @Override
    public int deleteTMgisScopeCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        // 统一软删，卡号唯一索引只在在册卡之间生效
        return this.baseMapper.update(null, new UpdateWrapper<TMgisScopeCard>()
                .in("id", Arrays.asList(idArr))
                .eq("del_flag", 0)
                .set("del_flag", 1));
    }

    @Override
    public int deleteTMgisScopeCardById(Long id) {
        return this.baseMapper.update(null, new UpdateWrapper<TMgisScopeCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("del_flag", 1));
    }

    /** 卡号去空格、必填、限长；页面想填啥便收啥的口子在此收住 */
    private String normalizeBillNo(String billNo) {
        if (StringUtils.isEmpty(billNo) || billNo.trim().isEmpty()) {
            throw new BusinessException("作业范围标签卡号不能为空");
        }
        String no = billNo.trim();
        if (no.length() > BILL_NO_MAX_LEN) {
            throw new BusinessException("作业范围标签卡号长度不能超过 " + BILL_NO_MAX_LEN + " 位");
        }
        return no;
    }

    /**
     * 取资质单位底档。
     * requireActive=true（新卡 / 改挂单位）：档案不存在、已删、已注销一律拦下；
     * requireActive=false（沿用原单位的旧卡编辑）：只要求档案还在，注销不拦。
     */
    private TMgisUnitDoc loadArch(Long siteId, boolean requireActive) {
        if (siteId == null) {
            throw new BusinessException("请选择所属资质单位基础档案");
        }
        TMgisUnitDoc arch = mgisUnitDocMapper.selectOne(new QueryWrapper<TMgisUnitDoc>()
                .eq("id", siteId).eq("del_flag", 0));
        if (arch == null) {
            throw new BusinessException("资质单位基础档案不存在，请重新选择");
        }
        if (requireActive && arch.getStatus() != null && arch.getStatus() != ARCH_ACTIVE) {
            throw new BusinessException("资质单位「" + arch.getSiteName() + "」已注销，不得新挂标签卡");
        }
        return arch;
    }

    /** 挂用类别总数：必填、为正；本年度新增数不得为负、不得超过总数（两处记的数一一对账） */
    private void validateQty(TMgisScopeCard record) {
        BigDecimal qty = record.getQty();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("本卡挂用专业类别总数必须大于 0");
        }
        BigDecimal fine = record.getFineAmt();
        if (fine != null) {
            if (fine.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("本年度新增挂用类别数不能为负");
            }
            if (fine.compareTo(qty) > 0) {
                throw new BusinessException("本年度新增挂用类别数不能超过挂用类别总数");
            }
        }
    }

    /** 业务日期（建档日期）不得早于昨日零点：昨日、今日方收 */
    private void validateBizDate(Date bizDate) {
        if (bizDate == null) {
            return;
        }
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (bizDate.before(c.getTime())) {
            throw new BusinessException("业务日期不能早于昨日");
        }
    }

    /**
     * 依底档阈值定档：取值达到上限即按高一档折算（与种子档注释一致），
     * 故各档比较一律用严格小于，边界记录不再矮一档。
     */
    private int levelOf(BigDecimal val, TMgisUnitDoc arch) {
        if (arch.getTh1Max() != null && val.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (arch.getTh2Max() != null && val.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (arch.getTh3Max() != null && val.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
