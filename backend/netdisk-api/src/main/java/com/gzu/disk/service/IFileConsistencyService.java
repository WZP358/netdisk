package com.gzu.disk.service;

import com.gzu.disk.domain.DiskFile;

import java.util.List;
import java.util.Map;

/**
 * 文件一致性检查服务接口
 * 
 * @author netdisk
 */
public interface IFileConsistencyService {
    
    /**
     * 检查并清理无效的文件记录（文件在数据库中但HDFS中不存在）
     * 
     * @param userId 用户ID，为null时检查所有用户
     * @return 清理结果统计
     */
    Map<String, Object> checkAndCleanInvalidFiles(Long userId);
    
    /**
     * 检查单个文件是否存在
     * 
     * @param fileId 文件ID
     * @return true-文件存在，false-文件不存在
     */
    boolean checkFileExists(Long fileId);
    
    /**
     * 获取所有无效的文件记录
     * 
     * @param userId 用户ID
     * @return 无效文件列表
     */
    List<DiskFile> getInvalidFiles(Long userId);
    
    /**
     * 批量检查文件是否存在
     * 
     * @param fileIds 文件ID列表
     * @return 文件ID和存在状态的映射
     */
    Map<Long, Boolean> batchCheckFilesExist(List<Long> fileIds);
}

