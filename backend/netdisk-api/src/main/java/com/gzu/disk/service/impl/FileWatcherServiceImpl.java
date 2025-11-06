package com.gzu.disk.service.impl;

import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.utils.StringUtils;
import com.gzu.disk.domain.DiskFile;
import com.gzu.disk.domain.DiskStorage;
import com.gzu.disk.service.IDiskFileService;
import com.gzu.disk.service.IDiskStorageService;
import com.gzu.disk.service.IFileWatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 文件监控服务实现
 * 使用 Java NIO WatchService 监控文件删除事件
 * 
 * @author netdisk
 */
@Service
@ConditionalOnProperty(prefix = "disk.file-watcher", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FileWatcherServiceImpl implements IFileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherServiceImpl.class);

    @Autowired
    private IDiskFileService diskFileService;

    @Autowired
    private IDiskStorageService diskStorageService;

    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean watching = false;
    
    // 文件路径 -> 文件ID 的映射
    private final Map<String, Long> filePathToIdMap = new ConcurrentHashMap<>();
    
    // WatchKey -> 目录路径 的映射
    private final Map<WatchKey, Path> keyToPathMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("========== 文件监控服务初始化 ==========");
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.executorService = Executors.newSingleThreadExecutor();
            
            // 构建文件路径映射
            buildFilePathMapping();
            
            // 启动监控
            startWatching();
            
            log.info("文件监控服务初始化完成，共监控 {} 个文件", filePathToIdMap.size());
        } catch (Exception e) {
            log.error("文件监控服务初始化失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("========== 文件监控服务关闭 ==========");
        stopWatching();
    }

    @Override
    public void startWatching() {
        if (watching) {
            log.warn("文件监控服务已经在运行中");
            return;
        }

        watching = true;
        executorService.submit(() -> {
            log.info("文件监控线程启动");
            
            while (watching) {
                try {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key == null) {
                        continue;
                    }

                    Path dir = keyToPathMap.get(key);
                    if (dir == null) {
                        log.warn("未知的 WatchKey");
                        key.reset();
                        continue;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();
                        Path fullPath = dir.resolve(filename);

                        // 只处理删除事件
                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            handleFileDeleted(fullPath);
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        keyToPathMap.remove(key);
                        log.warn("WatchKey 失效，移除监控: {}", dir);
                    }

                } catch (InterruptedException e) {
                    log.info("文件监控线程被中断");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("文件监控过程中出错", e);
                }
            }
            
            log.info("文件监控线程停止");
        });
    }

    @Override
    public void stopWatching() {
        watching = false;
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("关闭 WatchService 失败", e);
            }
        }
        
        keyToPathMap.clear();
        filePathToIdMap.clear();
        
        log.info("文件监控服务已停止");
    }

    @Override
    public void reloadWatchDirectories() {
        log.info("重新加载监控目录...");
        
        // 清除现有映射
        filePathToIdMap.clear();
        keyToPathMap.clear();
        
        // 重新构建
        buildFilePathMapping();
        
        log.info("监控目录重新加载完成，共监控 {} 个文件", filePathToIdMap.size());
    }

    @Override
    public boolean isWatching() {
        return watching;
    }

    /**
     * 构建文件路径到ID的映射，并注册目录监控
     */
    private void buildFilePathMapping() {
        try {
            // 获取所有有效文件
            List<DiskFile> allFiles = diskFileService.selectAll();
            
            log.info("开始构建文件路径映射，共 {} 个文件", allFiles.size());
            
            String profilePath = RuoYiConfig.getProfile();
            Map<String, Boolean> registeredDirs = new HashMap<>();
            
            for (DiskFile file : allFiles) {
                // 跳过目录
                if (file.getIsDir() != null && file.getIsDir() == 1) {
                    continue;
                }
                
                String url = file.getUrl();
                if (StringUtils.isEmpty(url)) {
                    continue;
                }
                
                // 构建完整文件路径
                String relativePath = StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX);
                String fullPath = profilePath + relativePath;
                File localFile = new File(fullPath);
                
                // 将路径标准化
                String normalizedPath = localFile.getAbsolutePath();
                filePathToIdMap.put(normalizedPath, file.getId());
                
                // 注册父目录监控
                String parentDir = localFile.getParent();
                if (parentDir != null && !registeredDirs.containsKey(parentDir)) {
                    try {
                        Path dirPath = Paths.get(parentDir);
                        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                            WatchKey key = dirPath.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_DELETE
                            );
                            keyToPathMap.put(key, dirPath);
                            registeredDirs.put(parentDir, true);
                            log.debug("注册目录监控: {}", parentDir);
                        }
                    } catch (IOException e) {
                        log.warn("注册目录监控失败: {}", parentDir, e);
                    }
                }
            }
            
            log.info("文件路径映射构建完成，监控 {} 个目录", registeredDirs.size());
            
        } catch (Exception e) {
            log.error("构建文件路径映射失败", e);
        }
    }

    /**
     * 处理文件删除事件
     */
    @Transactional
    protected void handleFileDeleted(Path deletedPath) {
        try {
            String deletedPathStr = deletedPath.toAbsolutePath().toString();
            Long fileId = filePathToIdMap.get(deletedPathStr);
            
            if (fileId == null) {
                log.debug("检测到文件删除，但未找到对应的数据库记录: {}", deletedPathStr);
                return;
            }
            
            // 查询文件信息
            DiskFile file = diskFileService.selectDiskFileById(fileId);
            if (file == null) {
                log.warn("文件ID {} 在数据库中不存在", fileId);
                filePathToIdMap.remove(deletedPathStr);
                return;
            }
            
            log.info("========== 检测到文件被删除 ==========");
            log.info("文件名: {}", file.getName());
            log.info("文件ID: {}", fileId);
            log.info("文件路径: {}", deletedPathStr);
            log.info("文件大小: {} bytes", file.getSize());
            log.info("所属用户: {}", file.getCreateId());
            
            // 删除数据库记录
            int deleted = diskFileService.deleteDiskFileByIds(new Long[]{fileId});
            
            if (deleted > 0) {
                log.info("✓ 已自动删除数据库记录");
                
                // 更新用户存储容量
                if (file.getSize() != null && file.getSize() > 0) {
                    DiskStorage storage = diskStorageService.selectDiskStorageByUserId(file.getCreateId());
                    if (storage != null) {
                        long newUsedCapacity = Math.max(0, storage.getUsedCapacity() - file.getSize());
                        storage.setUsedCapacity(newUsedCapacity);
                        diskStorageService.updateDiskStorage(storage);
                        log.info("✓ 已更新用户存储容量，减少: {} bytes", file.getSize());
                    }
                }
                
                // 从映射中移除
                filePathToIdMap.remove(deletedPathStr);
                
            } else {
                log.warn("✗ 删除数据库记录失败");
            }
            
            log.info("========== 文件删除处理完成 ==========");
            
        } catch (Exception e) {
            log.error("处理文件删除事件时出错: {}", deletedPath, e);
        }
    }
}

