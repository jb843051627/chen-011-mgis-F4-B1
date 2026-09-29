package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.mapper.auto.TMgisUnitDocMapper;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.service.ITMgisScopeCardService;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 作业范围标签建档卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "作业范围标签建档卡")
@Controller
@RequestMapping("/MgisScopeCardController")
public class MgisScopeCardController extends BaseController {

    private final String prefix = "admin/mgisScopeCard";

    @Autowired
    private ITMgisScopeCardService mgisScopeCardService;

    @Autowired
    private TMgisUnitDocMapper mgisUnitDocMapper;

    /**
     * 建档页所属单位下拉：只给在册（未注销、未删除）的资质单位，
     * 已注销档案在挑选这一关就挑不中，而不是等保存时才拦。
     */
    @ApiOperation(value = "在册资质单位下拉", notes = "在册资质单位下拉")
    @GetMapping("/activeSites")
    @RequiresPermissions("mgis:mgisScopeCard:view")
    @ResponseBody
    public AjaxResult activeSites() {
        return AjaxResult.successData(200, mgisUnitDocMapper.selectList(new QueryWrapper<TMgisUnitDoc>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .orderByAsc("site_no")));
    }

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("mgis:mgisScopeCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "作业范围标签建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("mgis:mgisScopeCard:list")
    @ResponseBody
    public ResultTable list(TMgisScopeCard record) {
        QueryWrapper<TMgisScopeCard> queryWrapper = new QueryWrapper<TMgisScopeCard>();
        // 存量读取统一口径：只看未删卡，进展（进度）按筛选走，不再写死 status=0
        queryWrapper.eq("del_flag", 0);
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            queryWrapper.like("bill_no", record.getBillNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (record.getGradeLevel() != null) {
            queryWrapper.eq("grade_level", record.getGradeLevel());
        }
        if (record.getSiteId() != null) {
            queryWrapper.eq("site_id", record.getSiteId());
        }
        queryWrapper.orderByDesc("create_time");
        startPage();
        PageInfo<TMgisScopeCard> page =
                new PageInfo<TMgisScopeCard>(mgisScopeCardService.selectTMgisScopeCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "作业范围标签建档卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("mgis:mgisScopeCard:add")
    @ResponseBody
    public AjaxResult add(TMgisScopeCard record) {
        return toAjax(mgisScopeCardService.insertTMgisScopeCard(record));
    }

    @Log(title = "作业范围标签建档卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("mgis:mgisScopeCard:edit")
    @ResponseBody
    public AjaxResult editSave(TMgisScopeCard record) {
        return toAjax(mgisScopeCardService.updateTMgisScopeCard(record));
    }

    @Log(title = "作业范围标签建档卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("mgis:mgisScopeCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(mgisScopeCardService.deleteTMgisScopeCardByIds(ids));
    }
}
