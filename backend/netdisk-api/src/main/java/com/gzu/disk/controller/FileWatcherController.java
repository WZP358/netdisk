package com.gzu.disk.controller;

import com.gzu.common.core.controller.BaseController;
import com.gzu.common.core.domain.AjaxResult;
import com.gzu.disk.service.IFileWatcherService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 文件监控Controller
 * 
 * @author netdisk
 */
@Api("文件实时监控")
@RestController
@RequestMapping("/disk/watcher")
@ConditionalOnBean(IFileWatcherService.class)
public class FileWatcherController extends BaseController {

    @Autowired
    private IFileWatcherService fileWatcherService;

    /**
     * 获取监控状态
     */
    @ApiOperation("获取监控状态")
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @GetMapping("/status")
    public AjaxResult getStatus() {
        boolean watching = fileWatcherService.isWatching();
        return AjaxResult.success()
            .put("watching", watching)
            .put("message", watching ? "监控服务运行中" : "监控服务已停止");
    }

    /**
     * 启动监控
     */
    @ApiOperation("启动监控")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @PostMapping("/start")
    public AjaxResult start() {
        fileWatcherService.startWatching();
        return AjaxResult.success("监控服务已启动");
    }

    /**
     * 停止监控
     */
    @ApiOperation("停止监控")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @PostMapping("/stop")
    public AjaxResult stop() {
        fileWatcherService.stopWatching();
        return AjaxResult.success("监控服务已停止");
    }

    /**
     * 重新加载监控目录
     */
    @ApiOperation("重新加载监控目录")
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @PostMapping("/reload")
    public AjaxResult reload() {
        fileWatcherService.reloadWatchDirectories();
        return AjaxResult.success("监控目录已重新加载");
    }
}

