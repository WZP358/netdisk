package com.gzu.disk.service.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.exception.ServiceException;
import com.gzu.common.utils.DateUtils;
import com.gzu.common.utils.SecurityUtils;
import com.gzu.common.utils.StringUtils;
import com.gzu.common.utils.file.FileUploadUtils;
import com.gzu.common.utils.file.FileUtils;
import com.gzu.common.utils.file.MimeTypeUtils;
import com.gzu.common.utils.hdfs.HdfsUtils;
import com.gzu.disk.domain.DiskStorage;
import com.gzu.disk.service.IDiskSensitiveWordService;
import com.gzu.disk.service.IDiskStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.gzu.disk.mapper.DiskFileMapper;
import com.gzu.disk.domain.DiskFile;
import com.gzu.disk.service.IDiskFileService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件Service业务层处理
 * 
 * @author maple
 * @date 2024-04-11
 */
@Service

public class DiskFileServiceImpl implements IDiskFileService 
{
    private static final Logger log = LoggerFactory.getLogger(DiskRecoveryFileServiceImpl.class);

    @Autowired
    private DiskFileMapper diskFileMapper;

    @Autowired
    private IDiskStorageService diskStorageService;

    @Autowired
    private IDiskSensitiveWordService diskSensitiveWordService;

    /**
     * 查询文件
     * 
     * @param id 文件主键
     * @return 文件
     */
    @Override
    public DiskFile selectDiskFileById(Long id)
    {
        return diskFileMapper.selectDiskFileById(id);
    }

    /**
     * 查询文件列表
     * 
     * @param diskFile 文件
     * @return 文件
     */
    @Override
    public List<DiskFile> selectDiskFileList(DiskFile diskFile)
    {
        return diskFileMapper.selectDiskFileList(diskFile);
    }

    /**
     * 新增文件
     * 
     * @param diskFile 文件
     * @return 结果
     */
    @Override
    public int insertDiskFile(DiskFile diskFile)
    {
        diskFile.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isEmpty(diskFile.getDelFlag())) {
            diskFile.setDelFlag("0");
        }
        validEntityBeforeSave(diskFile);
        
        // 检查是否存在同名文件
        int existCount = diskFileMapper.verify(diskFile.getName(), diskFile.getParentId(), null, SecurityUtils.getUserId(), "0");
        if (existCount > 0) {
            // 存在同名文件，查找并删除（移到回收站）
            log.info("发现同名文件，准备覆盖: {}", diskFile.getName());
            DiskFile queryFile = new DiskFile();
            queryFile.setName(diskFile.getName());
            queryFile.setParentId(diskFile.getParentId());
            queryFile.setCreateId(SecurityUtils.getUserId());
            queryFile.setDelFlag("0");
            
            List<DiskFile> existFiles = diskFileMapper.selectDiskFileList(queryFile);
            if (!existFiles.isEmpty()) {
                DiskFile oldFile = existFiles.get(0);
                // 先逻辑删除旧文件
                this.removeDiskFileByIds(new Long[]{oldFile.getId()});
                log.info("已删除旧文件: {}, ID: {}", oldFile.getName(), oldFile.getId());
            }
        }
        
        // 检查回收站中是否有同名文件（警告但不阻止）
        int recycleCount = diskFileMapper.verify(diskFile.getName(), diskFile.getParentId(), null, SecurityUtils.getUserId(), "2");
        if (recycleCount > 0) {
            log.warn("回收站中存在同名文件: {}", diskFile.getName());
        }
        
        return diskFileMapper.insertDiskFile(diskFile);
    }

    private void validEntityBeforeSave(DiskFile diskFile) {
        boolean b = diskSensitiveWordService.filterSensitiveWord(diskFile.getName());
        if (b) throw new ServiceException("检测到敏感词，请重试");
    }

    /**
     * 修改文件
     * 
     * @param diskFile 文件
     * @return 结果
     */
    @Override
    @Transactional
    public int updateDiskFile(DiskFile diskFile)
    {
        diskFile.setUpdateTime(DateUtils.getNowDate());
        validEntityBeforeSave(diskFile);
        
        if (diskFile.getId() != null && StringUtils.isNotEmpty(diskFile.getName())) {
            DiskFile old = diskFileMapper.selectDiskFileById(diskFile.getId());
            if (old != null && StringUtils.isNotEmpty(old.getUrl())
                    && !diskFile.getName().equals(old.getName())) {
                
                String oldUrl = old.getUrl();
                String relative = StringUtils.substringAfter(oldUrl, Constants.RESOURCE_PREFIX);
                
                if (StringUtils.isNotEmpty(relative)) {
                    // 判断是文件还是文件夹
                    if (old.getIsDir() != null && old.getIsDir() == 1) {
                        // ========== 文件夹重命名 ==========
                        log.info("========== 开始重命名文件夹 ==========");
                        log.info("文件夹ID: {}", diskFile.getId());
                        log.info("旧名称: {} -> 新名称: {}", old.getName(), diskFile.getName());
                        log.info("旧URL: {}", oldUrl);
                        
                        try {
                            // 1. 解析路径（relative 格式: /upload/admin/文件夹名）
                            // 提取文件夹名和父路径
                            String oldFolderPath = relative; // 例如: /upload/admin/旧文件夹名
                            String parentPath = "";
                            
                            if (oldFolderPath.contains("/")) {
                                int lastSlash = oldFolderPath.lastIndexOf('/');
                                parentPath = oldFolderPath.substring(0, lastSlash); // 例如: /upload/admin
                            }
                            
                            String newFolderName = diskFile.getName();
                            String newFolderPath = StringUtils.isNotEmpty(parentPath) 
                                    ? parentPath + "/" + newFolderName 
                                    : "/" + newFolderName;
                            
                            log.info("旧文件夹路径: {}", oldFolderPath);
                            log.info("新文件夹路径: {}", newFolderPath);
                            
                            // 2. 重命名物理文件夹
                            if (HdfsUtils.isHdfsEnabled()) {
                                String src = HdfsUtils.buildHdfsPath(oldFolderPath);
                                String dst = HdfsUtils.buildHdfsPath(newFolderPath);
                                log.info("HDFS重命名: {} -> {}", src, dst);
                                boolean ok = HdfsUtils.rename(src, dst);
                                if (!ok) {
                                    throw new ServiceException("重命名HDFS文件夹失败");
                                }
                            } else {
                                String localBase = RuoYiConfig.getProfile();
                                // 直接拼接，因为 relative 已经包含开头的 /
                                String oldAbs = localBase + oldFolderPath;
                                String newAbs = localBase + newFolderPath;
                                log.info("本地重命名: {} -> {}", oldAbs, newAbs);
                                
                                java.io.File oldFolder = new java.io.File(oldAbs);
                                java.io.File newFolder = new java.io.File(newAbs);
                                
                                if (!oldFolder.exists()) {
                                    log.warn("文件夹不存在，可能已被删除: {}", oldAbs);
                                } else {
                                    // 确保父目录存在
                                    if (newFolder.getParentFile() != null && !newFolder.getParentFile().exists()) {
                                        newFolder.getParentFile().mkdirs();
                                    }
                                    boolean ok = oldFolder.renameTo(newFolder);
                                    if (!ok) {
                                        throw new ServiceException("重命名本地文件夹失败: " + oldAbs + " -> " + newAbs);
                                    }
                                    log.info("✓ 物理文件夹重命名成功");
                                }
                            }
                            
                            // 3. 更新文件夹本身的URL
                            // URL格式: /profile/upload/admin/文件夹名
                            String newUrl = Constants.RESOURCE_PREFIX + newFolderPath;
                            diskFile.setUrl(newUrl);
                            log.info("更新文件夹URL: {} -> {}", oldUrl, newUrl);
                            
                            // 4. 获取所有子文件和子文件夹
                            List<DiskFile> allFiles = this.selectAllByUserIdIgnoreDel(SecurityUtils.getUserId());
                            List<DiskFile> allChildren = new ArrayList<>();
                            this.getChildPerms(allFiles, allChildren, diskFile.getId());
                            
                            log.info("找到 {} 个子文件/文件夹需要更新路径", allChildren.size());
                            
                            // 5. 批量更新所有子项的URL（将旧路径前缀替换为新路径前缀）
                            // 注意：需要精确匹配路径，避免误替换
                            String oldUrlPrefix = oldUrl; // 例如: /profile/upload/admin/旧文件夹名
                            String newUrlPrefix = newUrl; // 例如: /profile/upload/admin/新文件夹名
                            
                            int updatedCount = 0;
                            for (DiskFile child : allChildren) {
                                if (StringUtils.isNotEmpty(child.getUrl())) {
                                    String oldChildUrl = child.getUrl();
                                    // 精确匹配：子项的URL必须以旧URL开头，且下一个字符是/或者是URL结尾
                                    if (oldChildUrl.startsWith(oldUrlPrefix)) {
                                        // 检查是否是直接的子项（路径精确匹配）
                                        String remaining = oldChildUrl.substring(oldUrlPrefix.length());
                                        if (remaining.isEmpty() || remaining.startsWith("/")) {
                                            String newChildUrl = newUrlPrefix + remaining;
                                            
                                            // 更新数据库
                                            DiskFile updateChild = new DiskFile();
                                            updateChild.setId(child.getId());
                                            updateChild.setUrl(newChildUrl);
                                            diskFileMapper.updateDiskFile(updateChild);
                                            updatedCount++;
                                            
                                            log.debug("更新子项URL: {} -> {}", oldChildUrl, newChildUrl);
                                        }
                                    }
                                }
                            }
                            
                            log.info("✓ 已更新 {} 个子文件/文件夹的路径", updatedCount);
                            log.info("========== 文件夹重命名完成 ==========");
                            
                        } catch (ServiceException se) {
                            throw se;
                        } catch (Exception e) {
                            log.error("重命名文件夹失败", e);
                            throw new ServiceException("重命名文件夹失败: " + e.getMessage());
                        }
                        
                    } else if (old.getIsDir() != null && old.getIsDir() == 0) {
                        // ========== 文件重命名 ==========
                        log.info("========== 开始重命名文件 ==========");
                        log.info("文件ID: {}", diskFile.getId());
                        log.info("旧名称: {} -> 新名称: {}", old.getName(), diskFile.getName());
                        
                        if (relative.contains("/")) {
                            // relative 格式: /upload/admin/文件名
                            String dirRel = relative.substring(0, relative.lastIndexOf('/'));
                            String newFileName = diskFile.getName();
                            String newRelative = dirRel + "/" + newFileName;

                            try {
                                if (HdfsUtils.isHdfsEnabled()) {
                                    String src = HdfsUtils.buildHdfsPath(relative);
                                    String dst = HdfsUtils.buildHdfsPath(newRelative);
                                    boolean ok = HdfsUtils.rename(src, dst);
                                    if (!ok) {
                                        throw new ServiceException("重命名HDFS文件失败");
                                    }
                                } else {
                                    String localBase = RuoYiConfig.getProfile();
                                    // 直接拼接，因为 relative 已经包含开头的 /
                                    String oldAbs = localBase + relative;
                                    String newAbs = localBase + newRelative;
                                    java.io.File srcFile = new java.io.File(oldAbs);
                                    java.io.File dstFile = new java.io.File(newAbs);
                                    // 确保父目录存在
                                    if (dstFile.getParentFile() != null && !dstFile.getParentFile().exists()) {
                                        dstFile.getParentFile().mkdirs();
                                    }
                                    boolean ok = srcFile.renameTo(dstFile);
                                    if (!ok) {
                                        throw new ServiceException("重命名本地文件失败");
                                    }
                                }
                                // 更新url为新的相对路径
                                diskFile.setUrl(Constants.RESOURCE_PREFIX + newRelative);

                                // 根据后缀重算type
                                String newName = newFileName;
                                String ext = newName.contains(".") ? newName.substring(newName.lastIndexOf('.') + 1) : "";
                                if (StringUtils.isNotEmpty(ext)) {
                                    Integer newType = this.getType(ext);
                                    diskFile.setType(newType);
                                }
                                
                                log.info("========== 文件重命名完成 ==========");
                            } catch (ServiceException se) {
                                throw se;
                            } catch (Exception e) {
                                log.error("重命名文件失败", e);
                                throw new ServiceException("重命名文件失败: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        
        // 检查是否存在同名文件（排除自己）
        int existCount = diskFileMapper.verify(diskFile.getName(), diskFile.getParentId(), diskFile.getId(), SecurityUtils.getUserId(), "0");
        if (existCount > 0) {
            throw new ServiceException("名称重复");
        }
        
        // 检查回收站中是否有同名文件（警告但不阻止）
        int recycleCount = diskFileMapper.verify(diskFile.getName(), diskFile.getParentId(), null, SecurityUtils.getUserId(), "2");
        if (recycleCount > 0) {
            log.warn("回收站中存在同名文件: {}", diskFile.getName());
        }
        
        return diskFileMapper.updateDiskFile(diskFile);
    }

    /**
     * 批量删除文件
     * 
     * @param ids 需要删除的文件主键
     * @return 结果
     */
    @Override
    public int deleteDiskFileByIds(Long[] ids)
    {
        return diskFileMapper.deleteDiskFileByIds(ids);
    }

    /**
     * 删除文件信息
     * 
     * @param id 文件主键
     * @return 结果
     */
    @Override
    public int deleteDiskFileById(Long id)
    {
        return diskFileMapper.deleteDiskFileById(id);
    }

    @Override
    public List<Long> selectDiskFileByParentIds(Long[] ids) {
        return diskFileMapper.selectDiskFileByParentIds(ids);
    }

    @Override
    public int removeDiskFileByIds(Long[] ids) {
        return diskFileMapper.removeDiskFileByIds(ids);
    }

    @Override
    public Integer getType(String extension) {
        if (FileUploadUtils.isAllowedExtension(extension, MimeTypeUtils.IMAGE_EXTENSION)) {
            return 0;
        } else if (FileUploadUtils.isAllowedExtension(extension, MimeTypeUtils.VIDEO_EXTENSION)) {
            return 1;
        } else if (FileUploadUtils.isAllowedExtension(extension, MimeTypeUtils.DOC_EXTENSION)) {
            return 2;
        } else if (FileUploadUtils.isAllowedExtension(extension, MimeTypeUtils.MEDIA_EXTENSION)) {
            return 3;
        } else {
            return 4;
        }
    }

    @Override
    public List<DiskFile> selectDiskFileListByIds(Long[] ids) {
        return diskFileMapper.selectDiskFileListByIds(ids);
    }

    @Override
    public List<DiskFile> selectDiskFileListByIds(Long[] ids, Long userId) {
        return diskFileMapper.selectDiskFileListByIdsAndUserId(ids,userId);
    }

    @Override
    public List<DiskFile> selectDiskFileListByIdsIgnoreDel(Long[] ids) {
        return diskFileMapper.selectDiskFileListByIdsIgnoreDel(ids);
    }

    @Override
    public List<DiskFile> selectAll() {
        return diskFileMapper.selectAll();
    }

    @Override
    public List<DiskFile> selectAllByUserId(Long userId) {
        return diskFileMapper.selectAllByUserId(userId);
    }

    @Override
    public List<DiskFile> selectAllByUserIdIgnoreDel(Long userId) {
        return diskFileMapper.selectAllByUserIdIgnoreDel(userId);
    }

    @Override
    public List<Map<String, Object>> typeCapacityStats(Long userId) {
        return diskFileMapper.typeCapacityStats(userId);
    }

    @Override
    public List<Map<String, Object>> fileTypeNumStats(Long userId) {
        return diskFileMapper.fileTypeNumStats(userId);
    }

    @Override
    public String getTypeName(Integer type) {
        switch (type) {
            case 0:
                return "图片";
            case 1:
                return "视频";
            case 2:
                return "文档";
            case 3:
                return "音乐";
            case 5:
                return "文件夹";
            default:
                return "其他";
        }
    }

    /**
     * 根据父节点的ID获取所有子节点
     *
     * @param list 分类表
     * @param allList 汇总
     * @param parentId 传入的父节点ID
     * @return String
     */
    @Override
    public List<DiskFile> getChildPerms(List<DiskFile> list, List<DiskFile> allList, Long parentId)
    {
        List<DiskFile> returnList = new ArrayList<>();
        for (Iterator<DiskFile> iterator = list.iterator(); iterator.hasNext();)
        {
            DiskFile t = iterator.next();
            // 一、根据传入的某个父节点ID,遍历该父节点的所有子节点
            if (t.getParentId().equals(parentId))
            {
                recursionFn(list,allList, t);
                returnList.add(t);
            }
        }
        return returnList;
    }

    @Override
    public int refresh(Long[] ids) {
        return diskFileMapper.refresh(ids);
    }

    @Override
    @Transactional
    public int save(DiskFile diskFile, DiskStorage diskStorage) {
        int i = this.insertDiskFile(diskFile);
        if (i==0) throw new ServiceException("上传失败");
        //更新已经用的容量
        i = diskStorageService.updateUsedCapacity(diskStorage.getId(), diskStorage.getUsedCapacity() + diskFile.getSize());
        if (i==0) throw new ServiceException("上传失败");
        return i;
    }

    /**
     * 根据父节点的ID获取所有子节点
     *
     * @param list 分类表
     * @param parentId 传入的父节点ID
     * @return String
     */
    public List<DiskFile> getChildPerms(List<DiskFile> list, int parentId)
    {
        List<DiskFile> returnList = new ArrayList<>();
        for (Iterator<DiskFile> iterator = list.iterator(); iterator.hasNext();)
        {
            DiskFile t = iterator.next();
            // 一、根据传入的某个父节点ID,遍历该父节点的所有子节点
            if (t.getParentId() == parentId)
            {
                recursionFn(list, t);
                returnList.add(t);
            }
        }
        return returnList;
    }

    /**
     * 递归列表
     *
     * @param list 分类表
     * @param t 子节点
     */
    private void recursionFn(List<DiskFile> list,List<DiskFile> allList, DiskFile t)
    {
        // 得到子节点列表
        List<DiskFile> childList = getChildList(list, t);
        t.setChildren(childList);
        allList.addAll(childList);
        allList.add(t);
        for (DiskFile tChild : childList)
        {
            if (hasChild(list, tChild))
            {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 递归列表
     *
     * @param list 分类表
     * @param t 子节点
     */
    private void recursionFn(List<DiskFile> list, DiskFile t)
    {
        // 得到子节点列表
        List<DiskFile> childList = getChildList(list, t);
        t.setChildren(childList);
        for (DiskFile tChild : childList)
        {
            if (hasChild(list, tChild))
            {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    public List<DiskFile> getChildList(List<DiskFile> list, DiskFile t)
    {
        List<DiskFile> tlist = new ArrayList<>();
        Iterator<DiskFile> it = list.iterator();
        while (it.hasNext())
        {
            DiskFile n = (DiskFile) it.next();
            if (n.getParentId().longValue() == t.getId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    public boolean hasChild(List<DiskFile> list, DiskFile t)
    {
        return getChildList(list, t).size() > 0;
    }

    @Override
    public int deleteDiskFileByIdsAndRemoveFile(List<Long> delFileIds) {
        log.info("=== 开始删除文件及其子文件 ===");
        log.info("待删除的文件ID列表: {}", delFileIds);
        
        List<DiskFile> allDelFiles = this.selectDiskFileListByIdsIgnoreDel(delFileIds.toArray(new Long[0]));
        List<DiskFile> allDiskFiles = this.selectAllByUserIdIgnoreDel(SecurityUtils.getUserId());
        delFileIds.forEach(parentId -> this.getChildPerms(allDiskFiles,allDelFiles,parentId));
        
        log.info("包含子文件后共需删除 {} 个文件", allDelFiles.size());
        
        allDelFiles.forEach(diskFile -> {
            log.info("--- 处理文件: {} (ID: {})", diskFile.getName(), diskFile.getId());
            log.info("文件URL: {}", diskFile.getUrl());
            log.info("是否为目录: {}", diskFile.getIsDir());
            
            // 本地资源路径
            String localPath = RuoYiConfig.getProfile();
            log.info("Profile路径: {}", localPath);
            
            // 数据库资源地址
            String downloadPath = localPath + StringUtils.substringAfter(diskFile.getUrl(), Constants.RESOURCE_PREFIX);
            log.info("构造的完整路径: {}", downloadPath);
            
            try {
                // 使用FileUtils.deleteFile()以支持HDFS删除
                boolean deleted = FileUtils.deleteFile(downloadPath);
                if (deleted) {
                    log.info("✓ 文件删除成功: {}", diskFile.getName());
                } else {
                    log.warn("✗ 文件删除失败或文件不存在: {}", diskFile.getName());
                }
            } catch (Exception e) {
                log.error("✗ 文件删除异常: {}", diskFile.getName(), e);
            }
        });
        
        int result = this.deleteDiskFileByIds(allDelFiles.stream().map(DiskFile::getId).toArray(Long[]::new));
        log.info("=== 数据库记录删除完成，共删除 {} 条记录 ===", result);
        return result;
    }

    @Override
    public List<Long> selectAllIdsByUserId(Long userId) {
        return diskFileMapper.selectAllIdsByUserId(userId);
    }
}
