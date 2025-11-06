package com.gzu.disk.service.impl;

import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.utils.StringUtils;
import com.gzu.common.utils.file.FileUtils;
import com.gzu.common.utils.hdfs.HdfsUtils;
import com.gzu.disk.domain.DiskFile;
import com.gzu.disk.domain.DiskStorage;
import com.gzu.disk.service.IFileConsistencyService;
import com.gzu.disk.service.IDiskFileService;
import com.gzu.disk.service.IDiskStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件一致性检查服务实现
 * 
 * @author netdisk
 */
@Service
public class FileConsistencyServiceImpl implements IFileConsistencyService {

    private static final Logger log = LoggerFactory.getLogger(FileConsistencyServiceImpl.class);

    @Autowired
    private IDiskFileService diskFileService;

    @Autowired
    private IDiskStorageService diskStorageService;

    @Override
    @Transactional
    public Map<String, Object> checkAndCleanInvalidFiles(Long userId) {
        log.info("========== 开始检查文件一致性 ==========");
        log.info("用户ID: {}", userId == null ? "全部用户" : userId);
        
        Map<String, Object> result = new HashMap<>();
        
        // 获取所有需要检查的文件
        List<DiskFile> allFiles;
        if (userId != null) {
            allFiles = diskFileService.selectAllByUserId(userId);
        } else {
            allFiles = diskFileService.selectAll();
        }
        
        log.info("共需检查 {} 个文件记录", allFiles.size());
        
        List<DiskFile> invalidFiles = new ArrayList<>();
        List<DiskFile> validFiles = new ArrayList<>();
        long totalInvalidSize = 0;
        
        // 检查每个文件
        for (DiskFile file : allFiles) {
            // 跳过目录
            if (file.getIsDir() != null && file.getIsDir() == 1) {
                validFiles.add(file);
                continue;
            }
            
            String url = file.getUrl();
            if (StringUtils.isEmpty(url)) {
                log.warn("文件 {} (ID: {}) URL为空，标记为无效", file.getName(), file.getId());
                invalidFiles.add(file);
                totalInvalidSize += file.getSize() != null ? file.getSize() : 0;
                continue;
            }
            
            // 构建文件路径
            String localPath = RuoYiConfig.getProfile();
            String filePath = localPath + StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX);
            
            boolean exists = false;
            try {
                if (HdfsUtils.isHdfsEnabled()) {
                    // 检查HDFS文件
                    String hdfsPath = HdfsUtils.buildHdfsPath(StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX));
                    exists = HdfsUtils.exists(hdfsPath);
                    if (!exists) {
                        log.debug("HDFS文件不存在: {} -> {}", file.getName(), hdfsPath);
                    }
                } else {
                    // 检查本地文件
                    File localFile = new File(filePath);
                    exists = localFile.exists();
                    if (!exists) {
                        log.debug("本地文件不存在: {} -> {}", file.getName(), filePath);
                    }
                }
            } catch (Exception e) {
                log.error("检查文件存在性时出错: {}", file.getName(), e);
                exists = false;
            }
            
            if (!exists) {
                invalidFiles.add(file);
                totalInvalidSize += file.getSize() != null ? file.getSize() : 0;
            } else {
                validFiles.add(file);
            }
        }
        
        log.info("检查完成: 有效文件 {}, 无效文件 {}", validFiles.size(), invalidFiles.size());
        
        // 清理无效文件记录
        int cleanedCount = 0;
        if (!invalidFiles.isEmpty()) {
            log.info("开始清理无效文件记录...");
            
            // 按用户分组清理
            Map<Long, List<DiskFile>> filesByUser = invalidFiles.stream()
                    .collect(Collectors.groupingBy(DiskFile::getCreateId));
            
            for (Map.Entry<Long, List<DiskFile>> entry : filesByUser.entrySet()) {
                Long fileUserId = entry.getKey();
                List<DiskFile> userInvalidFiles = entry.getValue();
                long userTotalSize = userInvalidFiles.stream()
                        .mapToLong(f -> f.getSize() != null ? f.getSize() : 0)
                        .sum();
                
                log.info("用户 {} 有 {} 个无效文件，总大小: {} bytes", fileUserId, userInvalidFiles.size(), userTotalSize);
                
                // 物理删除文件记录（因为文件已经不存在了）
                Long[] fileIds = userInvalidFiles.stream()
                        .map(DiskFile::getId)
                        .toArray(Long[]::new);
                
                try {
                    int deleted = diskFileService.deleteDiskFileByIds(fileIds);
                    cleanedCount += deleted;
                    log.info("已物理删除 {} 条无效记录", deleted);
                    
                    // 更新用户存储容量
                    DiskStorage storage = diskStorageService.selectDiskStorageByUserId(fileUserId);
                    if (storage != null) {
                        long newUsedCapacity = Math.max(0, storage.getUsedCapacity() - userTotalSize);
                        storage.setUsedCapacity(newUsedCapacity);
                        diskStorageService.updateDiskStorage(storage);
                        log.info("已更新用户 {} 的存储容量，减少: {} bytes", fileUserId, userTotalSize);
                    }
                } catch (Exception e) {
                    log.error("清理用户 {} 的无效文件时出错", fileUserId, e);
                }
            }
        }
        
        // 返回结果
        result.put("totalFiles", allFiles.size());
        result.put("validFiles", validFiles.size());
        result.put("invalidFiles", invalidFiles.size());
        result.put("cleanedFiles", cleanedCount);
        result.put("reclaimedSpace", totalInvalidSize);
        result.put("invalidFileList", invalidFiles.stream()
                .map(f -> {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("id", f.getId());
                    fileInfo.put("name", f.getName());
                    fileInfo.put("url", f.getUrl());
                    fileInfo.put("size", f.getSize());
                    fileInfo.put("userId", f.getCreateId());
                    return fileInfo;
                })
                .collect(Collectors.toList())
        );
        
        log.info("========== 文件一致性检查完成 ==========");
        log.info("总计: {} 个文件, 有效: {}, 无效: {}, 已清理: {}, 释放空间: {} bytes", 
                allFiles.size(), validFiles.size(), invalidFiles.size(), cleanedCount, totalInvalidSize);
        
        return result;
    }

    @Override
    public boolean checkFileExists(Long fileId) {
        DiskFile file = diskFileService.selectDiskFileById(fileId);
        if (file == null) {
            return false;
        }
        
        // 目录始终返回true
        if (file.getIsDir() != null && file.getIsDir() == 1) {
            return true;
        }
        
        String url = file.getUrl();
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        
        try {
            String localPath = RuoYiConfig.getProfile();
            String filePath = localPath + StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX);
            
            if (HdfsUtils.isHdfsEnabled()) {
                String hdfsPath = HdfsUtils.buildHdfsPath(StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX));
                return HdfsUtils.exists(hdfsPath);
            } else {
                File localFile = new File(filePath);
                return localFile.exists();
            }
        } catch (Exception e) {
            log.error("检查文件 {} 存在性时出错", fileId, e);
            return false;
        }
    }

    @Override
    public List<DiskFile> getInvalidFiles(Long userId) {
        List<DiskFile> allFiles;
        if (userId != null) {
            allFiles = diskFileService.selectAllByUserId(userId);
        } else {
            allFiles = diskFileService.selectAll();
        }
        
        List<DiskFile> invalidFiles = new ArrayList<>();
        
        for (DiskFile file : allFiles) {
            // 跳过目录
            if (file.getIsDir() != null && file.getIsDir() == 1) {
                continue;
            }
            
            if (!checkFileExists(file.getId())) {
                invalidFiles.add(file);
            }
        }
        
        return invalidFiles;
    }

    @Override
    public Map<Long, Boolean> batchCheckFilesExist(List<Long> fileIds) {
        Map<Long, Boolean> result = new HashMap<>();
        
        for (Long fileId : fileIds) {
            result.put(fileId, checkFileExists(fileId));
        }
        
        return result;
    }
}

