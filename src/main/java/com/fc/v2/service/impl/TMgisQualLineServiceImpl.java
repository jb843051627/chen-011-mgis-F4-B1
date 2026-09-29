package com.fc.v2.service.impl;

import java.math.BigDecimal;
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

    /** 线的情形 0当家 1已让位 */
    private static final int STATUS_ACTIVE = 0;
    /** 合法量测输入区间（米）：(0,100] */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    @javax.annotation.Resource
    private TMgisQualLineMapper mgisQualLineMapper;

    @Override
    public TMgisQualLine selectTMgisQualLineById(Long id) {
        return this.mgisQualLineMapper.selectOne(new QueryWrapper<TMgisQualLine>()
                .eq("id", id).eq("del_flag", 0));
    }

    @Override
    public List<TMgisQualLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMgisQualLine>();
        }
        return this.mgisQualLineMapper.selectList(baseAvailableWrapper(at)
                .orderByDesc("priority", "rule_code"));
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null
                || !inRange(input)) {
            return 0;
        }
        TMgisQualLine rule = findRule(ruleCode, at);
        return rule == null ? 0 : levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || !inRange(input)) {
            return 0;
        }
        List<TMgisQualLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        // listAvailable 已按优先级降序、ruleCode 降序排好
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        QueryWrapper<TMgisQualLine> qw = baseAvailableWrapper(at).eq("id", id);
        Integer cnt = this.mgisQualLineMapper.selectCount(qw);
        return cnt != null && cnt > 0;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return this.mgisQualLineMapper.selectCount(baseAvailableWrapper(at));
    }

    /**
     * 可用规则的统一口径：未删除、当家（status=0）、at 落在生效窗口内
     * （启用之日含、交棒之日不含；两端为空表示不限）。按抢档顺位降序、代号降序。
     */
    private QueryWrapper<TMgisQualLine> baseAvailableWrapper(Date at) {
        return new QueryWrapper<TMgisQualLine>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE)
                .and(w -> w.isNull("eff_start").or().le("eff_start", at))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", at));
    }

    /** 同代号在 at 时刻生效的唯一版本（窗口口径与列表一致，多版本取顺位/代号最高者） */
    private TMgisQualLine findRule(String ruleCode, Date at) {
        List<TMgisQualLine> hit = this.mgisQualLineMapper.selectList(baseAvailableWrapper(at)
                .eq("rule_code", ruleCode)
                .orderByDesc("priority", "rule_code"));
        return hit.isEmpty() ? null : hit.get(0);
    }

    private boolean inRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) > 0 && input.compareTo(INPUT_MAX) <= 0;
    }

    /**
     * 分档：取值「达到」上限即按高一档折算（与底档种子口径一致），故各档皆为严格小于。
     * input &lt; th1 一档；&lt; th2 二档；&lt; th3 三档；其余四档。
     * 阈值缺失的档位视为不存在，向上顺延。
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
