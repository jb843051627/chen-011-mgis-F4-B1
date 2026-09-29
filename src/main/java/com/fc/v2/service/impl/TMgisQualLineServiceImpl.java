package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMgisQualLineMapper;
import com.fc.v2.model.auto.TMgisQualLine;
import com.fc.v2.service.ITMgisQualLineService;

/**
 * 测绘成果质检判定线 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisQualLineServiceImpl implements ITMgisQualLineService {

    /** 线的情形：当家 */
    private static final int LINE_ACTIVE = 0;

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TMgisQualLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMgisQualLine>() {
        @Override
        public int compare(TMgisQualLine a, TMgisQualLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMgisQualLineMapper mgisQualLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TMgisQualLine selectTMgisQualLineById(Long id) {
        return this.mgisQualLineMapper.selectById(id);
    }

    @Override
    public List<TMgisQualLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMgisQualLine>();
        }
        String now = ts(at);
        // 存量读取一把尺：未删 + 当家 + 启用之日已到 + 交棒之日未到（交棒日不含）
        List<TMgisQualLine> rows = this.mgisQualLineMapper.selectList(new QueryWrapper<TMgisQualLine>()
                .eq("del_flag", 0)
                .eq("status", LINE_ACTIVE)
                .le("eff_start", now)
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now)));
        rows.sort(PRIORITY_THEN_CODE_DESC);
        return rows;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        // 中误差不得为负
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }
        TMgisQualLine rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TMgisQualLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        // listAvailable 已按优先级降序、ruleCode 降序排好，队首即抢档赢家
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMgisQualLine r = this.mgisQualLineMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 口径与 listAvailable / countAvailable 完全一致，不得只看主键在不在
        return isEffective(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 定位 ruleCode 在 at 时刻唯一生效的版本 */
    private TMgisQualLine findRule(String ruleCode, Date at) {
        String now = ts(at);
        List<TMgisQualLine> hit = this.mgisQualLineMapper.selectList(new QueryWrapper<TMgisQualLine>()
                .eq("del_flag", 0)
                .eq("status", LINE_ACTIVE)
                .eq("rule_code", ruleCode)
                .le("eff_start", now)
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now)));
        return hit.isEmpty() ? null : hit.get(0);
    }

    /** 与列表同一套生效口径 */
    private boolean isEffective(TMgisQualLine r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != LINE_ACTIVE) {
            return false;
        }
        String now = ts(at);
        if (r.getEffStart() == null || ts(r.getEffStart()).compareTo(now) > 0) {
            return false;
        }
        return r.getEffEnd() == null || ts(r.getEffEnd()).compareTo(now) > 0;
    }

    /**
     * 分档：取值正顶在上限上时按高一档折算（等于上限取高一档），
     * 故各档比较一律严格小于，边界记录不再矮一档。
     */
    private int levelOf(TMgisQualLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
