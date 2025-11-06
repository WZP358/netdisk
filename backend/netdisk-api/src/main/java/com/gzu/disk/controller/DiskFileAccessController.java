package com.gzu.disk.controller;

import com.gzu.common.annotation.Log;
import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.core.controller.BaseController;
import com.gzu.common.core.domain.AjaxResult;
import com.gzu.common.enums.BusinessType;
import com.gzu.common.utils.StringUtils;
import com.gzu.disk.domain.DiskFile;
import com.gzu.disk.service.IFileConsistencyService;
import com.gzu.disk.service.IDiskFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 文件访问增强Controller
 * 在访问文件前验证文件是否存在
 * 
 * @author netdisk
 */
@Api("文件访问增强")
@RestController
@RequestMapping("/disk/file")
public class DiskFileAccessController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DiskFileAccessController.class);

    @Autowired
    private IDiskFileService diskFileService;

    @Autowired
    private IFileConsistencyService fileConsistencyService;

    /**
     * 验证文件是否可访问（在打开文件前调用）
     */
    @ApiOperation("验证文件是否可访问")
    @PreAuthorize("@ss.hasPermi('disk:file:query')")
    @GetMapping("/verifyAccess/{id}")
    public AjaxResult verifyFileAccess(@PathVariable("id") Long id) {
        DiskFile file = diskFileService.selectDiskFileById(id);
        
        if (file == null) {
            return AjaxResult.error("文件不存在");
        }
        
        // 如果是目录，直接返回可访问
        if (file.getIsDir() != null && file.getIsDir() == 1) {
            return AjaxResult.success("目录可访问", file);
        }
        
        // 检查物理文件是否存在
        boolean exists = fileConsistencyService.checkFileExists(id);
        
        if (!exists) {
            log.warn("文件记录存在但物理文件不存在: {} (ID: {})", file.getName(), id);
            
            // 异步清理无效记录
            new Thread(() -> {
                try {
                    log.info("开始异步清理无效文件记录: {}", id);
                    diskFileService.removeDiskFileByIds(new Long[]{id});
                    fileConsistencyService.checkAndCleanInvalidFiles(file.getCreateId());
                } catch (Exception e) {
                    log.error("清理无效文件记录失败", e);
                }
            }).start();
            
            return AjaxResult.error("文件不存在或已被删除");
        }
        
        return AjaxResult.success("文件可访问", file);
    }

    /**
     * 获取文件详细信息（增强版，包含存在性检查）
     */
    @ApiOperation("获取文件详细信息（增强版）")
    @PreAuthorize("@ss.hasPermi('disk:file:query')")
    @GetMapping(value = "/enhanced/{id}")
    public AjaxResult getEnhancedInfo(@PathVariable("id") Long id) {
        DiskFile file = diskFileService.selectDiskFileById(id);
        
        if (file == null) {
            return AjaxResult.error("文件不存在");
        }
        
        // 检查文件是否真实存在
        boolean exists = fileConsistencyService.checkFileExists(id);
        
        AjaxResult result = AjaxResult.success(file);
        result.put("physicalExists", exists);
        
        if (!exists) {
            result.put("warning", "文件记录存在但物理文件已丢失");
        }
        
        return result;
    }
}

