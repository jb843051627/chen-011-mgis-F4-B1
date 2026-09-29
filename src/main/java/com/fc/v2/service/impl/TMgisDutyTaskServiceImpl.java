package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisDutyTaskMapper;
import com.fc.v2.model.auto.TMgisDutyTask;
import com.fc.v2.service.ITMgisDutyTaskService;

/**
 * 到期提醒派发条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisDutyTaskServiceImpl implements ITMgisDutyTaskService {

    /** 条目情形：待提醒 */
    private static final int STATUS_WAIT = 0;
    /** 条目情形：已提醒 */
    private static final int STATUS_DONE = 1;
    /** 条目情形：送不出去 */
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMgisDutyTaskMapper mgisDutyTaskMapper;

    @Override
    public TMgisDutyTask selectTMgisDutyTaskById(Long id) {
        return this.mgisDutyTaskMapper.selectOne(new QueryWrapper<TMgisDutyTask>()
                .eq("id", id).eq("del_flag", 0));
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TMgisDutyTask> listDue(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMgisDutyTask>();
        }
        List<TMgisDutyTask> all = this.mgisDutyTaskMapper.selectList(new QueryWrapper<TMgisDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TMgisDutyTask> due = new java.util.ArrayList<TMgisDutyTask>();
        String now = ts(at);
        for (TMgisDutyTask r : all) {
            // 到期含"恰好到期"这一刻：compareTo <= 0；已提醒 / 送不出去的不再进窗口
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(now) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TMgisDutyTask> due = listDue(at);
        // 无到期条目：0，不得再对空表取首行
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TMgisDutyTask r : due) {
            // 逐条独立派发：某条超时 / 落库失败只回滚它自己，跳过继续，不殃及同批其余条目
            try {
                // 提前提醒自然日数为负 / 缺失的条目送不出去
                int result = (r.getAmount() != null && r.getAmount().signum() >= 0)
                        ? STATUS_DONE : STATUS_FAIL;
                int rows = this.mgisDutyTaskMapper.update(null, new UpdateWrapper<TMgisDutyTask>()
                        .eq("id", r.getId())
                        .eq("del_flag", 0)
                        .eq("status", STATUS_WAIT)
                        .set("status", result));
                if (rows > 0 && result == STATUS_DONE) {
                    ok++;
                }
            } catch (RuntimeException e) {
                // 与批量汇交同一种收场：本条记下、跳过，后续条目照派
                org.slf4j.LoggerFactory.getLogger(TMgisDutyTaskServiceImpl.class)
                        .warn("到期提醒条目 {} 派发失败，跳过：{}", r.getId(), e.getMessage());
            }
        }
        return ok;
    }
}
