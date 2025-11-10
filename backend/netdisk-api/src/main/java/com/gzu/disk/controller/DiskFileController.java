package com.gzu.disk.controller;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ZipUtil;
import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.exception.ServiceException;
import com.gzu.common.utils.SecurityUtils;
import com.gzu.common.utils.StringUtils;
import com.gzu.common.utils.file.FileUploadUtils;
import com.gzu.common.utils.file.FileUtils;
import com.gzu.common.utils.hdfs.HdfsUtils;
import com.gzu.disk.domain.*;
import com.gzu.disk.domain.bo.DownloadBo;
import com.gzu.disk.service.*;
import com.gzu.framework.config.ServerConfig;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.gzu.common.annotation.Log;
import com.gzu.common.core.controller.BaseController;
import com.gzu.common.core.domain.AjaxResult;
import com.gzu.common.enums.BusinessType;
import com.gzu.common.utils.poi.ExcelUtil;
import com.gzu.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件Controller
 * 
 * @author maple
 * @date 2024-04-11
 */
@RestController
@RequestMapping("/disk/file")
public class DiskFileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(DiskFileController.class);

    @Autowired
    private IDiskFileService diskFileService;

    @Autowired
    private IDiskStorageService diskStorageService;

    @Autowired
    private IDiskRecoveryFileService diskRecoveryFileService;

    @Autowired
    private IDiskShareFileService diskShareFileService;

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private IDiskSensitiveWordService diskSensitiveWordService;
    
    @Autowired(required = false)
    private IFileWatcherService fileWatcherService;

    private static final String FILE_DELIMETER = ",";

    @Autowired
    private IFileConsistencyService fileConsistencyService;

    /**
     * 查询文件列表
     * @param autoClean 是否自动清理无效文件（true=自动过滤并清理不存在的文件）
     */
    @PreAuthorize("@ss.hasPermi('disk:file:list')")
    @GetMapping("/list")
    public TableDataInfo list(DiskFile diskFile, Boolean autoClean)
    {
        startPage("id desc");
        diskFile.setCreateId(getUserId());
        DiskStorage diskStorage = new DiskStorage();
        diskStorage.setCreateId(getUserId());
        diskStorageService.insertDiskStorage(diskStorage);
        List<DiskFile> list = diskFileService.selectDiskFileList(diskFile);
        List<DiskFile> allDiskFiles = diskFileService.selectAll();
        
        // 如果启用自动清理，验证文件存在性
        if (autoClean != null && autoClean) {
            log.info("开始验证文件列表的有效性...");
            List<DiskFile> validFiles = new ArrayList<>();
            List<Long> invalidFileIds = new ArrayList<>();
            
            for (DiskFile f : list) {
                // 目录始终保留
                if (f.getIsDir() == 1) {
                    validFiles.add(f);
                } else {
                    // 检查文件是否存在
                    if (fileConsistencyService.checkFileExists(f.getId())) {
                        validFiles.add(f);
                    } else {
                        log.warn("发现无效文件: {} (ID: {})", f.getName(), f.getId());
                        invalidFileIds.add(f.getId());
                    }
                }
            }
            
            // 异步清理无效文件记录
            if (!invalidFileIds.isEmpty()) {
                log.info("发现 {} 个无效文件，开始清理...", invalidFileIds.size());
                new Thread(() -> {
                    try {
                        fileConsistencyService.checkAndCleanInvalidFiles(getUserId());
                        log.info("无效文件清理完成");
                    } catch (Exception e) {
                        log.error("清理无效文件时出错", e);
                    }
                }).start();
            }
            
            list = validFiles;
        }
        
        list.forEach(f -> {
            if (f.getIsDir()==1) {
                List<DiskFile> allChildFiles = new ArrayList<>();
                diskFileService.getChildPerms(allDiskFiles,allChildFiles,f.getId());
                f.setSize(allChildFiles.stream().map(DiskFile::getSize)
                        .reduce(0L,Long::sum));
            }
        });
        return getDataTable(list);
    }

    /**
     * 导出文件列表
     */
    @PreAuthorize("@ss.hasPermi('disk:file:export')")
    @Log(title = "文件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DiskFile diskFile)
    {
        List<DiskFile> list = diskFileService.selectDiskFileList(diskFile);
        ExcelUtil<DiskFile> util = new ExcelUtil<DiskFile>(DiskFile.class);
        util.exportExcel(response, list, "文件数据");
    }

    /**
     * 获取文件详细信息
     */
    @PreAuthorize("@ss.hasPermi('disk:file:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(diskFileService.selectDiskFileById(id));
    }

    /**
     * 新增文件
     */
    @PreAuthorize("@ss.hasPermi('disk:file:add')")
    @Log(title = "文件", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DiskFile diskFile)
    {
        diskFile.setCreateId(getUserId());
        // 获取当前用户本人的存储目录
        DiskStorage diskStorage = diskStorageService.selectDiskStorageByUserId(SecurityUtils.getUserId());
        if (Objects.isNull(diskStorage)) throw new ServiceException("空间未初始化");
        if (diskFile.getIsDir()==1) {
            //是文件夹，设置url
            // 上传文件路径
            String url = Constants.RESOURCE_PREFIX;
            String[] localPaths = RuoYiConfig.getUploadPath().split("/");
            if (diskFile.getParentId()==0) {
                url = url+"/"+localPaths[localPaths.length-1]+"/"+diskStorage.getBaseDir()+"/"+diskFile.getName();
            }else {
                DiskFile parentIdFile = diskFileService.selectDiskFileById(diskFile.getParentId());
                if (Objects.isNull(parentIdFile)) throw new ServiceException("父文件夹不存在");
                String[] parentPaths = parentIdFile.getUrl().split("/");
                url = url+"/"+localPaths[localPaths.length-1]+"/"+diskStorage.getBaseDir()
                        +"/"+parentPaths[parentPaths.length-1]+"/"+diskFile.getName();
            }
            diskFile.setUrl(url);
            // 本地资源路径
            String localPath = RuoYiConfig.getProfile();
            String path = StringUtils.substringAfter(diskFile.getUrl(), Constants.RESOURCE_PREFIX);
            // 数据库资源地址
            String filePath = localPath + path;
            
            // 判断是否使用HDFS存储
            if (HdfsUtils.isHdfsEnabled()) {
                // 在HDFS中创建目录
                try {
                    String hdfsPath = HdfsUtils.buildHdfsPath(path);
                    HdfsUtils.mkdirs(hdfsPath);
                    log.info("HDFS中成功创建目录: {}", hdfsPath);
                } catch (IOException e) {
                    log.error("HDFS创建目录失败: {}", e.getMessage(), e);
                    throw new ServiceException("创建文件夹失败: " + e.getMessage());
                }
            } else {
                // 本地文件系统创建目录
                FileUtil.mkdir(filePath);
            }
            
            diskFile.setType(5);
        }
        
        int result = diskFileService.insertDiskFile(diskFile);
        
        // 重新加载文件监控（如果启用）
        if (result > 0 && fileWatcherService != null && fileWatcherService.isWatching()) {
            try {
                fileWatcherService.reloadWatchDirectories();
                log.info("已重新加载文件监控");
            } catch (Exception e) {
                log.warn("重新加载文件监控失败", e);
            }
        }
        
        return toAjax(result);
    }

    /**
     * 修改文件
     */
    @PreAuthorize("@ss.hasPermi('disk:file:edit')")
    @Log(title = "文件", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DiskFile diskFile)
    {
        int result = diskFileService.updateDiskFile(diskFile);
        
        // 重新加载文件监控（如果启用），因为文件夹重命名会改变路径
        if (result > 0 && fileWatcherService != null && fileWatcherService.isWatching()) {
            try {
                fileWatcherService.reloadWatchDirectories();
                log.info("已重新加载文件监控（文件夹/文件重命名后）");
            } catch (Exception e) {
                log.warn("重新加载文件监控失败", e);
            }
        }
        
        return toAjax(result);
    }

    /**
     * 删除文件
     */
    @PreAuthorize("@ss.hasPermi('disk:file:remove')")
    @Log(title = "文件", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        int j = removeByParentIds(ids);
        for (Long id : ids) {
            DiskRecoveryFile diskRecoveryFile = new DiskRecoveryFile();
            diskRecoveryFile.setFileId(id);
            diskRecoveryFile.setCreateId(getUserId());
            diskRecoveryFileService.insertDiskRecoveryFile(diskRecoveryFile);
        }
        return toAjax(j);
    }

    private int removeByParentIds(Long[] ids) {
        int j = diskFileService.removeDiskFileByIds(ids);
        List<Long> idsList = diskFileService.selectDiskFileByParentIds(ids);
        Long[] itmeIds = new Long[idsList.size()];
        idsList.toArray(itmeIds);
        if (itmeIds.length>0) {
            removeByParentIds(itmeIds);
        }else {
            return j;
        }
        return j;
    }

    /**
     * 通用上传请求（单个）
     */
    @PostMapping("/upload/{parentId}")
    public AjaxResult uploadFile(MultipartFile file,@PathVariable Long parentId)
    {
        try
        {
            log.info("========== 文件上传开始 ==========");
            log.info("原始文件名: {}", file.getOriginalFilename());
            log.info("文件大小: {} bytes", file.getSize());
            log.info("父文件夹ID: {}", parentId);
            
            // 上传文件路径
            String filePath = RuoYiConfig.getUploadPath();
            log.info("基础上传路径: {}", filePath);
            
            // 获取当前用户本人的存储目录
            DiskStorage diskStorage = diskStorageService.selectDiskStorageByUserId(SecurityUtils.getUserId());
            if (Objects.isNull(diskStorage)) {
                log.error("存储空间未初始化");
                throw new ServiceException("未初始化存储空间");
            }
            log.info("用户存储目录: {}", diskStorage.getBaseDir());
            log.info("存储空间: 已用={}, 总容量={}", diskStorage.getUsedCapacity(), diskStorage.getTotalCapacity());
            
            if (diskStorage.getTotalCapacity()-diskStorage.getUsedCapacity()<=0) {
                log.error("存储空间不足");
                throw new ServiceException("存储空间不足");
            }
            
            if (parentId.equals(0L)) {
                filePath = filePath+"/"+diskStorage.getBaseDir();
            } else {
                DiskFile parentIdFile = diskFileService.selectDiskFileById(parentId);
                if (Objects.isNull(parentIdFile)) {
                    log.error("父文件夹不存在: {}", parentId);
                    throw new ServiceException("父文件夹不存在");
                }
                String[] localPaths = RuoYiConfig.getUploadPath().split("/");
                filePath = filePath+"/"+diskStorage.getBaseDir()+parentIdFile.getUrl()
                        .replace(Constants.RESOURCE_PREFIX,"").replace(localPaths[localPaths.length-1],"")
                        .replace("/"+diskStorage.getBaseDir(),"");
            }
            log.info("最终上传路径: {}", filePath);
            
            diskSensitiveWordService.filterSensitiveWord(file.getOriginalFilename());
            DiskFile diskFile = new DiskFile();
            // 使用原始文件名，不添加随机前缀
            String fileName = file.getOriginalFilename();
            diskFile.setName(fileName);
            log.info("使用原始文件名: {}", fileName);
            
            // 上传并返回新文件名称
            log.info("开始上传文件到存储系统...");
            fileName = FileUploadUtils.upload(filePath,false, file,fileName);
            log.info("文件上传成功，返回路径: {}", fileName);
            
            String url = serverConfig.getUrl()  + fileName;
            diskFile.setCreateId(getUserId());
            diskFile.setOldName(file.getOriginalFilename());
            diskFile.setIsDir(0);
            diskFile.setOrderNum(0);
            diskFile.setParentId(parentId);
            diskFile.setUrl(fileName);
            diskFile.setSize(file.getSize());
            String extension = FileUploadUtils.getExtension(file);
            diskFile.setType(diskFileService.getType(extension));
            
            log.info("保存文件记录到数据库...");
            diskFileService.save(diskFile,diskStorage);
            log.info("文件记录保存成功");
            
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            ajax.put("size", file.getSize());
            ajax.put("type", extension);
            
            log.info("========== 文件上传完成 ==========");
            return ajax;
        }
        catch (Exception e)
        {
            log.error("========== 文件上传失败 ==========", e);
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误信息: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 查询文件列表
     */
    @GetMapping("/listFileByUUIDAndsecretKey/{parentId}")
    public AjaxResult listFileByUUIDAndsecretKey(DiskShareFile diskShareFile,@PathVariable("parentId") Long parentId)
    {
        DiskShareFile diskShareFile1 = diskShareFileService.get(diskShareFile.getUuid().trim());
        diskShareFileService.verify(diskShareFile,diskShareFile1);
        DiskFile diskFile = new DiskFile();
        diskFile.setParentId(parentId);
        List<DiskFile> diskFiles = diskFileService.selectDiskFileList(diskFile);
        Set<String> avoidWordSet = new HashSet<>(Arrays.asList(diskShareFile1.getAllFileIds().split(",")));

        List<DiskFile> filteredList = diskFiles.stream()
                .filter(word -> avoidWordSet.contains(String.valueOf(word.getId())))
                .collect(Collectors.toList());
        return AjaxResult.success(filteredList);
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/zip")
    public void hadoopDownload(DownloadBo downloadBo, HttpServletResponse response) {
        List<DiskFile> diskFiles;
        String dest = RuoYiConfig.getProfile()+"/";
        if (StringUtils.isNotEmpty(downloadBo.getUuid())&&StringUtils.isNotEmpty(downloadBo.getSecretKey())) {
            diskFiles = diskFileService.selectDiskFileListByIds(Arrays.stream(downloadBo.getIds().split(","))
                    .map(String::trim)
                    .map(Long::valueOf)
                    .toArray(Long[]::new));
            dest = dest + downloadBo.getUuid();
        } else {
            diskFiles = diskFileService.selectDiskFileListByIds(Arrays.stream(downloadBo.getIds().split(","))
                    .map(String::trim)
                    .map(Long::valueOf)
                    .toArray(Long[]::new),getUserId());
            dest = dest + RandomUtil.randomString(6);
        }
        FileUtil.mkdir(dest);

        try {
            String finalDest = dest;
            // 从HDFS或本地复制文件到临时目录
            diskFiles.forEach(diskFile -> {
                try {
                    // 本地资源路径
                    String localPath = RuoYiConfig.getProfile();
                    // 数据库资源地址
                    String filePath = localPath + StringUtils.substringAfter(diskFile.getUrl(), Constants.RESOURCE_PREFIX);
                    
                    // 使用FileUtils.writeBytes，它会自动判断HDFS还是本地
                    String destFilePath = finalDest + "/" + diskFile.getName();
                    try (FileOutputStream fos = new FileOutputStream(destFilePath)) {
                        FileUtils.writeBytes(filePath, fos);
                    }
                } catch (Exception e) {
                    log.error("复制文件失败: {}", diskFile.getName(), e);
                }
            });
            
            // 调用zip方法进行压缩
            String downloadPath = dest + ".zip";
            ZipUtil.zip(dest, downloadPath);
            byte[] data = FileUtil.readBytes(FileUtil.file(downloadPath));
            response.reset();
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setHeader("Content-Disposition", "attachment; filename=\"ruoyi.zip\"");
            response.addHeader("Content-Length", "" + data.length);
            response.setContentType("application/octet-stream; charset=UTF-8");
            IOUtils.write(data, response.getOutputStream());
            
            // 清理临时文件
            FileUtil.del(dest);
            FileUtils.deleteFile(downloadPath);
        } catch (IOException e) {
            log.error("diskFile 下载文件失败", e);
            // 确保清理临时文件
            try {
                FileUtil.del(dest);
            } catch (Exception ex) {
                log.error("清理临时文件失败", ex);
            }
        }
    }

}
