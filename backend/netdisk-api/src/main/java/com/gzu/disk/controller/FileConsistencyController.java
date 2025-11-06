package com.gzu.disk.controller;

import com.gzu.common.annotation.Log;
import com.gzu.common.core.controller.BaseController;
import com.gzu.common.core.domain.AjaxResult;
import com.gzu.common.enums.BusinessType;
import com.gzu.common.utils.SecurityUtils;
import com.gzu.disk.service.IFileConsistencyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件一致性检查Controller
 * 
 * @author netdisk
 */
@Api("文件一致性检查")
@RestController
@RequestMapping("/disk/consistency")
public class FileConsistencyController extends BaseController {

    @Autowired
    private IFileConsistencyService fileConsistencyService;

    /**
     * 检查并清理当前用户的无效文件
     */
    @ApiOperation("检查并清理当前用户的无效文件")
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @Log(title = "文件一致性检查", businessType = BusinessType.CLEAN)
    @PostMapping("/cleanMyFiles")
    public AjaxResult cleanMyInvalidFiles() {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> result = fileConsistencyService.checkAndCleanInvalidFiles(userId);
        return AjaxResult.success("检查完成", result);
    }

    /**
     * 检查并清理所有用户的无效文件（管理员权限）
     */
    @ApiOperation("检查并清理所有用户的无效文件")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @Log(title = "文件一致性检查", businessType = BusinessType.CLEAN)
    @PostMapping("/cleanAllFiles")
    public AjaxResult cleanAllInvalidFiles() {
        Map<String, Object> result = fileConsistencyService.checkAndCleanInvalidFiles(null);
        return AjaxResult.success("检查完成", result);
    }

    /**
     * 获取当前用户的无效文件列表（不清理）
     */
    @ApiOperation("获取当前用户的无效文件列表")
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @GetMapping("/getInvalidFiles")
    public AjaxResult getMyInvalidFiles() {
        Long userId = SecurityUtils.getUserId();
        return AjaxResult.success(fileConsistencyService.getInvalidFiles(userId));
    }

    /**
     * 检查单个文件是否存在
     */
    @ApiOperation("检查单个文件是否存在")
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @GetMapping("/checkFile/{fileId}")
    public AjaxResult checkFileExists(@PathVariable Long fileId) {
        boolean exists = fileConsistencyService.checkFileExists(fileId);
        return AjaxResult.success(exists);
    }

    /**
     * 批量检查文件是否存在
     */
    @ApiOperation("批量检查文件是否存在")
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @PostMapping("/batchCheckFiles")
    public AjaxResult batchCheckFilesExist(@RequestBody List<Long> fileIds) {
        Map<Long, Boolean> result = fileConsistencyService.batchCheckFilesExist(fileIds);
        return AjaxResult.success(result);
    }
}

