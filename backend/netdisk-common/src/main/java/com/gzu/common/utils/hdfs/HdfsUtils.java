package com.gzu.common.utils.hdfs;

import com.gzu.common.config.HdfsConfig;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * HDFS工具类
 * 
 * @author netdisk
 */
@Component
public class HdfsUtils {

    private static final Logger log = LoggerFactory.getLogger(HdfsUtils.class);

    @Autowired(required = false)
    private FileSystem fileSystem;

    @Autowired
    private HdfsConfig hdfsConfig;

    private static FileSystem staticFileSystem;
    private static HdfsConfig staticHdfsConfig;

    @PostConstruct
    public void init() {
        staticFileSystem = fileSystem;
        staticHdfsConfig = hdfsConfig;
    }

    /**
     * 判断HDFS是否启用
     */
    public static boolean isHdfsEnabled() {
        return staticHdfsConfig != null && staticHdfsConfig.getEnable() && staticFileSystem != null;
    }

    /**
     * 创建目录
     */
    public static boolean mkdirs(String path) throws IOException {
        if (!isHdfsEnabled()) {
            return false;
        }
        Path hdfsPath = new Path(path);
        return staticFileSystem.mkdirs(hdfsPath);
    }

    /**
     * 上传文件到HDFS
     * 
     * @param file 要上传的文件
     * @param destPath HDFS目标路径
     * @return 是否上传成功
     */
    public static boolean uploadFile(MultipartFile file, String destPath) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path path = new Path(destPath);
        
        // 确保父目录存在
        Path parent = path.getParent();
        if (parent != null && !staticFileSystem.exists(parent)) {
            staticFileSystem.mkdirs(parent);
        }

        try (InputStream inputStream = file.getInputStream();
             FSDataOutputStream outputStream = staticFileSystem.create(path, true)) {
            IOUtils.copyBytes(inputStream, outputStream, 4096, false);
            log.info("文件上传到HDFS成功: {}", destPath);
            return true;
        } catch (IOException e) {
            log.error("文件上传到HDFS失败: {}", destPath, e);
            throw e;
        }
    }

    /**
     * 上传字节数组到HDFS
     */
    public static boolean uploadBytes(byte[] bytes, String destPath) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path path = new Path(destPath);
        
        // 确保父目录存在
        Path parent = path.getParent();
        if (parent != null && !staticFileSystem.exists(parent)) {
            staticFileSystem.mkdirs(parent);
        }

        try (FSDataOutputStream outputStream = staticFileSystem.create(path, true)) {
            outputStream.write(bytes);
            log.info("字节数据上传到HDFS成功: {}", destPath);
            return true;
        } catch (IOException e) {
            log.error("字节数据上传到HDFS失败: {}", destPath, e);
            throw e;
        }
    }

    /**
     * 从HDFS下载文件到输出流
     */
    public static void downloadFile(String srcPath, OutputStream outputStream) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path path = new Path(srcPath);
        if (!staticFileSystem.exists(path)) {
            throw new IOException("文件不存在: " + srcPath);
        }

        try (FSDataInputStream inputStream = staticFileSystem.open(path)) {
            IOUtils.copyBytes(inputStream, outputStream, 4096, false);
            log.info("从HDFS下载文件成功: {}", srcPath);
        } catch (IOException e) {
            log.error("从HDFS下载文件失败: {}", srcPath, e);
            throw e;
        }
    }

    /**
     * 读取HDFS文件内容为字节数组
     */
    public static byte[] readFileBytes(String srcPath) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path path = new Path(srcPath);
        if (!staticFileSystem.exists(path)) {
            throw new IOException("文件不存在: " + srcPath);
        }

        try (FSDataInputStream inputStream = staticFileSystem.open(path);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            IOUtils.copyBytes(inputStream, outputStream, 4096, false);
            return outputStream.toByteArray();
        }
    }

    /**
     * 删除文件或目录
     */
    public static boolean deleteFile(String path) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path hdfsPath = new Path(path);
        if (staticFileSystem.exists(hdfsPath)) {
            boolean result = staticFileSystem.delete(hdfsPath, true);
            log.info("删除HDFS文件: {}, 结果: {}", path, result);
            return result;
        }
        return false;
    }

    /**
     * 检查文件是否存在
     */
    public static boolean exists(String path) throws IOException {
        if (!isHdfsEnabled()) {
            return false;
        }
        Path hdfsPath = new Path(path);
        return staticFileSystem.exists(hdfsPath);
    }

    /**
     * 获取文件状态
     */
    public static FileStatus getFileStatus(String path) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }
        Path hdfsPath = new Path(path);
        return staticFileSystem.getFileStatus(hdfsPath);
    }

    /**
     * 列出目录下的文件
     */
    public static List<FileStatus> listFiles(String path) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        List<FileStatus> fileList = new ArrayList<>();
        Path hdfsPath = new Path(path);
        
        if (staticFileSystem.exists(hdfsPath)) {
            FileStatus[] fileStatuses = staticFileSystem.listStatus(hdfsPath);
            for (FileStatus fileStatus : fileStatuses) {
                fileList.add(fileStatus);
            }
        }
        
        return fileList;
    }

    /**
     * 重命名文件
     */
    public static boolean rename(String srcPath, String destPath) throws IOException {
        if (!isHdfsEnabled()) {
            throw new IOException("HDFS未启用");
        }

        Path src = new Path(srcPath);
        Path dest = new Path(destPath);
        
        return staticFileSystem.rename(src, dest);
    }

    /**
     * 获取文件大小
     */
    public static long getFileSize(String path) throws IOException {
        if (!isHdfsEnabled()) {
            return 0;
        }

        Path hdfsPath = new Path(path);
        if (staticFileSystem.exists(hdfsPath)) {
            return staticFileSystem.getFileStatus(hdfsPath).getLen();
        }
        return 0;
    }

    /**
     * 构建HDFS完整路径
     */
    public static String buildHdfsPath(String relativePath) {
        if (staticHdfsConfig == null) {
            return relativePath;
        }
        String basePath = staticHdfsConfig.getBasePath();
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        return basePath + relativePath;
    }
}

