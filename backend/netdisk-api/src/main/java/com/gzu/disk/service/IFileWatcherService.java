package com.gzu.disk.service;

/**
 * 文件监控服务接口
 * 
 * @author netdisk
 */
public interface IFileWatcherService {
    
    /**
     * 启动文件监控
     */
    void startWatching();
    
    /**
     * 停止文件监控
     */
    void stopWatching();
    
    /**
     * 重新加载监控目录
     */
    void reloadWatchDirectories();
    
    /**
     * 检查监控服务是否运行
     * 
     * @return true-运行中，false-已停止
     */
    boolean isWatching();
}

