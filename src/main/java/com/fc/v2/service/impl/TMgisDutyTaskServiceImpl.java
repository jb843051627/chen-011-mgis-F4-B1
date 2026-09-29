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

    /** 条目情形 0待提醒 1已提醒 2送不出去 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMgisDutyTaskMapper mgisDutyTaskMapper;

    @Override
    public TMgisDutyTask selectTMgisDutyTaskById(Long id) {
        return this.mgisDutyTaskMapper.selectOne(new QueryWrapper<TMgisDutyTask>()
                .eq("id", id).eq("del_flag", 0));
    }

    /**
     * 该时刻可处理的条目：未删除、尚未处理、且已到期（截止时刻 <= at，恰好到期也算）。
     * 直接以 java.util.Date 作绑定参数，交给 JDBC 驱动按参数类型传值，不做墙钟字符串拼接。
     */
    @Override
    public List<TMgisDutyTask> listDue(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMgisDutyTask>();
        }
        return this.mgisDutyTaskMapper.selectList(new QueryWrapper<TMgisDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .le("due_at", at));
    }

    /**
     * 执行一次：成功的落「已提醒」；提前日数非法（为空或负）的落「送不出去」；
     * 单条失败不影响其余条目，返回成功条数。
     */
    @Override
    public int runOnce(Date at) {
        List<TMgisDutyTask> due = listDue(at);
        int ok = 0;
        for (TMgisDutyTask r : due) {
            boolean valid = r.getAmount() != null && r.getAmount().signum() >= 0;
            int target = valid ? STATUS_DONE : STATUS_FAIL;
            try {
                int rows = this.mgisDutyTaskMapper.update(null, new UpdateWrapper<TMgisDutyTask>()
                        .eq("id", r.getId())
                        .eq("del_flag", 0)
                        .eq("status", STATUS_WAIT)
                        .set("status", target));
                if (rows > 0 && valid) {
                    ok++;
                }
            } catch (RuntimeException ignore) {
                // 单条失败跳过继续
            }
        }
        return ok;
    }
}
