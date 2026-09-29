package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.ITMgisCheckCardService;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 不合格成果整改回执单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "不合格成果整改回执单")
@Controller
@RequestMapping("/mgisCheckCard")
public class MgisCheckCardController extends BaseController {

    private final String prefix = "admin/mgisCheckCard";

    @Autowired
    private ITMgisCheckCardService mgisCheckCardService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("mgisCheckCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "不合格成果整改回执单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("mgisCheckCard:list")
    @ResponseBody
    public ResultTable list(TMgisCheckCard record) {
        QueryWrapper<TMgisCheckCard> queryWrapper = new QueryWrapper<TMgisCheckCard>();
        // 存量读取统一口径：只看未删单据
        queryWrapper.eq("del_flag", 0);
        // 落定状态、当前关口（进度）两个筛口都要真正起作用
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (record.getStage() != null) {
            queryWrapper.eq("stage", record.getStage());
        }
        if (record.getBizNo() != null && !record.getBizNo().trim().isEmpty()) {
            queryWrapper.like("biz_no", record.getBizNo().trim());
        }
        queryWrapper.orderByDesc("create_time");
        startPage();
        PageInfo<TMgisCheckCard> page =
                new PageInfo<TMgisCheckCard>(mgisCheckCardService.selectTMgisCheckCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "不合格成果整改回执单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("mgisCheckCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        TMgisCheckCard updated = mgisCheckCardService.advance(id, remark);
        if (updated != null) {
            return success();
        }
        return AjaxResult.error("推进被拒：单据不存在、已办结，或已被他人先行处理，请刷新后重试");
    }

    @Log(title = "不合格成果整改回执单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("mgisCheckCard:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        TMgisCheckCard updated = mgisCheckCardService.rollback(id, remark);
        if (updated != null) {
            return success();
        }
        return AjaxResult.error("回退被拒：单据不存在、已办结，或已被他人先行处理，请刷新后重试");
    }
}
