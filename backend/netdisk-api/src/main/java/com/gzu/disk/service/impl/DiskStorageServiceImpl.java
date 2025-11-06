package com.gzu.disk.service.impl;

import java.util.List;
import java.util.Objects;

import cn.hutool.core.util.RandomUtil;
import com.gzu.common.core.domain.entity.SysUser;
import com.gzu.common.utils.DateUtils;
import com.gzu.system.service.ISysConfigService;
import com.gzu.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.gzu.disk.mapper.DiskStorageMapper;
import com.gzu.disk.domain.DiskStorage;
import com.gzu.disk.service.IDiskStorageService;

/**
 * 用户存储Service业务层处理
 * 
 * @author maple
 * @date 2024-04-11
 */
@Service
public class DiskStorageServiceImpl implements IDiskStorageService 
{
    @Autowired
    private DiskStorageMapper diskStorageMapper;

    @Autowired
    private ISysConfigService configService;
    
    @Autowired
    private ISysUserService sysUserService;

    /**
     * 查询用户存储
     * 
     * @param id 用户存储主键
     * @return 用户存储
     */
    @Override
    public DiskStorage selectDiskStorageById(Long id)
    {
        return diskStorageMapper.selectDiskStorageById(id);
    }

    /**
     * 查询用户存储列表
     * 
     * @param diskStorage 用户存储
     * @return 用户存储
     */
    @Override
    public List<DiskStorage> selectDiskStorageList(DiskStorage diskStorage)
    {
        return diskStorageMapper.selectDiskStorageList(diskStorage);
    }

    /**
     * 新增用户存储
     * 
     * @param diskStorage 用户存储
     * @return 结果
     */
    @Override
    public int insertDiskStorage(DiskStorage diskStorage)
    {
        DiskStorage diskStorage1 = diskStorageMapper.selectDiskStorageByUserId(diskStorage.getCreateId());
        if (Objects.nonNull(diskStorage1)) return updateDiskStorage(diskStorage);
        diskStorage.setCreateTime(DateUtils.getNowDate());
        diskStorage.setTotalCapacity(Long.valueOf(configService.selectConfigByKey("storage.capacity")));
        
        // 使用用户名作为基础目录，如果获取不到则使用随机字符串
        String baseDir;
        try {
            SysUser user = sysUserService.selectUserById(diskStorage.getCreateId());
            if (user != null && user.getUserName() != null) {
                // 使用用户名，移除特殊字符确保文件系统兼容
                baseDir = user.getUserName().replaceAll("[^a-zA-Z0-9_-]", "_");
            } else {
                // 如果获取不到用户信息，使用 user_ID 格式
                baseDir = "user_" + diskStorage.getCreateId();
            }
        } catch (Exception e) {
            // 异常情况下使用随机字符串
            baseDir = RandomUtil.randomString(6);
        }
        
        diskStorage.setBaseDir(baseDir);
        return diskStorageMapper.insertDiskStorage(diskStorage);
    }

    /**
     * 修改用户存储
     * 
     * @param diskStorage 用户存储
     * @return 结果
     */
    @Override
    public int updateDiskStorage(DiskStorage diskStorage)
    {
        diskStorage.setUpdateTime(DateUtils.getNowDate());
        return diskStorageMapper.updateDiskStorage(diskStorage);
    }

    /**
     * 批量删除用户存储
     * 
     * @param ids 需要删除的用户存储主键
     * @return 结果
     */
    @Override
    public int deleteDiskStorageByIds(Long[] ids)
    {
        return diskStorageMapper.deleteDiskStorageByIds(ids);
    }

    /**
     * 删除用户存储信息
     * 
     * @param id 用户存储主键
     * @return 结果
     */
    @Override
    public int deleteDiskStorageById(Long id)
    {
        return diskStorageMapper.deleteDiskStorageById(id);
    }

    @Override
    public DiskStorage selectDiskStorageByUserId(Long userId) {
        return diskStorageMapper.selectDiskStorageByUserId(userId);
    }

    @Override
    public int updateUsedCapacity(Long id, long usedCapacity) {
        return diskStorageMapper.updateUsedCapacity(id,usedCapacity);
    }

    @Override
    public boolean updateUsedCapacityByUserId(Long userId, long usedCapacity) {
        return diskStorageMapper.updateUsedCapacityByUserId(userId,usedCapacity)>0;
    }
}
