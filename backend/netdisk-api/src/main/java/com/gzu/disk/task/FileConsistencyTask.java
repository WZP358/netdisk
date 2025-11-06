package com.gzu.disk.task;

import com.gzu.disk.service.IFileConsistencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文件一致性检查定时任务
 * 
 * @author netdisk
 */
@Component
@ConditionalOnProperty(prefix = "disk.consistency", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FileConsistencyTask {

    private static final Logger log = LoggerFactory.getLogger(FileConsistencyTask.class);

    @Autowired
    private IFileConsistencyService fileConsistencyService;

    /**
     * 定时检查并清理无效文件
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${disk.consistency.cron:0 0 3 * * ?}")
    public void checkAndCleanInvalidFiles() {
        log.info("========== 开始执行定时文件一致性检查任务 ==========");
        
        try {
            Map<String, Object> result = fileConsistencyService.checkAndCleanInvalidFiles(null);
            
            log.info("定时任务执行完成:");
            log.info("  - 检查文件总数: {}", result.get("totalFiles"));
            log.info("  - 有效文件: {}", result.get("validFiles"));
            log.info("  - 无效文件: {}", result.get("invalidFiles"));
            log.info("  - 已清理: {}", result.get("cleanedFiles"));
            log.info("  - 释放空间: {} bytes ({} MB)", 
                    result.get("reclaimedSpace"), 
                    (Long) result.get("reclaimedSpace") / 1024 / 1024);
            
        } catch (Exception e) {
            log.error("定时文件一致性检查任务执行失败", e);
        }
        
        log.info("========== 定时文件一致性检查任务结束 ==========");
    }
}

