package com.gzu.common.utils.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.utils.DateUtils;
import com.gzu.common.utils.StringUtils;
import com.gzu.common.utils.uuid.IdUtils;
import com.gzu.common.utils.hdfs.HdfsUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * 文件处理工具类
 * 
 * @author ruoyi
 */
public class FileUtils
{
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
    
    public static String FILENAME_PATTERN = "[a-zA-Z0-9_\\-\\|\\.\\u4e00-\\u9fa5]+";

    /**
     * 输出指定文件的byte数组
     * 
     * @param filePath 文件路径
     * @param os 输出流
     * @return
     */
    public static void writeBytes(String filePath, OutputStream os) throws IOException
    {
        FileInputStream fis = null;
        try
        {
            // 判断是否使用HDFS
            if (HdfsUtils.isHdfsEnabled()) {
                // 尝试从HDFS读取
                try {
                    // 从文件路径中提取HDFS路径
                    String hdfsPath = convertToHdfsPath(filePath);
                    log.debug("尝试从HDFS读取文件: {}", hdfsPath);
                    // 检查HDFS中文件是否存在
                    if (HdfsUtils.exists(hdfsPath)) {
                        log.info("从HDFS下载文件: {}", hdfsPath);
                        // 从HDFS读取文件并写入输出流
                        HdfsUtils.downloadFile(hdfsPath, os);
                        return;
                    } else {
                        log.debug("HDFS中文件不存在，尝试本地: {}", hdfsPath);
                    }
                } catch (Exception e) {
                    // HDFS读取失败，尝试本地文件系统
                    log.warn("HDFS读取失败，尝试本地文件系统: {}", e.getMessage());
                }
            }
            
            // 使用本地文件系统
            File file = new File(filePath);
            if (!file.exists())
            {
                throw new FileNotFoundException(filePath);
            }
            log.debug("从本地读取文件: {}", filePath);
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int length;
            while ((length = fis.read(b)) > 0)
            {
                os.write(b, 0, length);
            }
        }
        catch (IOException e)
        {
            throw e;
        }
        finally
        {
            IOUtils.close(os);
            IOUtils.close(fis);
        }
    }

    /**
     * 将文件路径转换为HDFS路径
     */
    private static String convertToHdfsPath(String filePath) {
        log.debug("转换路径 - 输入: {}", filePath);
        log.debug("Profile路径: {}", RuoYiConfig.getProfile());
        log.debug("RESOURCE_PREFIX: {}", Constants.RESOURCE_PREFIX);
        
        // 如果是本地绝对路径，提取相对路径
        String relativePath = filePath;
        if (filePath.startsWith(RuoYiConfig.getProfile())) {
            relativePath = filePath.substring(RuoYiConfig.getProfile().length());
            log.debug("去除Profile后: {}", relativePath);
        }
        // 如果包含RESOURCE_PREFIX，提取后面的部分
        if (relativePath.contains(Constants.RESOURCE_PREFIX)) {
            int index = relativePath.indexOf(Constants.RESOURCE_PREFIX);
            relativePath = relativePath.substring(index + Constants.RESOURCE_PREFIX.length());
            log.debug("去除RESOURCE_PREFIX后: {}", relativePath);
        }
        String hdfsPath = HdfsUtils.buildHdfsPath(relativePath);
        log.debug("最终HDFS路径: {}", hdfsPath);
        return hdfsPath;
    }

    /**
     * 写数据到文件中
     *
     * @param data 数据
     * @return 目标文件
     * @throws IOException IO异常
     */
    public static String writeImportBytes(byte[] data) throws IOException
    {
        return writeBytes(data, RuoYiConfig.getImportPath());
    }

    /**
     * 写数据到文件中
     *
     * @param data 数据
     * @param uploadDir 目标文件
     * @return 目标文件
     * @throws IOException IO异常
     */
    public static String writeBytes(byte[] data, String uploadDir) throws IOException
    {
        String pathName = "";
        String extension = getFileExtendName(data);
        pathName = DateUtils.datePath() + "/" + IdUtils.fastUUID() + "." + extension;
        
        // 判断是否使用HDFS
        if (HdfsUtils.isHdfsEnabled()) {
            // 使用HDFS存储
            String relativePath = uploadDir;
            if (uploadDir.startsWith(RuoYiConfig.getProfile())) {
                relativePath = uploadDir.substring(RuoYiConfig.getProfile().length());
            }
            if (!relativePath.startsWith("/")) {
                relativePath = "/" + relativePath;
            }
            String hdfsPath = HdfsUtils.buildHdfsPath(relativePath + "/" + pathName);
            HdfsUtils.uploadBytes(data, hdfsPath);
        } else {
            // 使用本地存储
            FileOutputStream fos = null;
            try
            {
                File file = FileUploadUtils.getAbsoluteFile(uploadDir, pathName);
                fos = new FileOutputStream(file);
                fos.write(data);
            }
            finally
            {
                IOUtils.close(fos);
            }
        }
        return FileUploadUtils.getPathFileName(uploadDir, pathName);
    }

    /**
     * 删除文件
     * 
     * @param filePath 文件
     * @return
     */
    public static boolean deleteFile(String filePath)
    {
        log.info("=== 开始删除文件 ===");
        log.info("输入路径: {}", filePath);
        log.info("HDFS是否启用: {}", HdfsUtils.isHdfsEnabled());
        log.info("是否包含RESOURCE_PREFIX: {}", filePath.contains(Constants.RESOURCE_PREFIX));
        log.info("是否以Profile开头: {}", filePath.startsWith(RuoYiConfig.getProfile()));
        
        // 判断是否使用HDFS
        if (HdfsUtils.isHdfsEnabled() && (filePath.contains(Constants.RESOURCE_PREFIX) || filePath.startsWith(RuoYiConfig.getProfile()))) {
            try {
                String hdfsPath = convertToHdfsPath(filePath);
                log.info("转换后的HDFS路径: {}", hdfsPath);
                boolean result = HdfsUtils.deleteFile(hdfsPath);
                log.info("HDFS删除结果: {}", result);
                return result;
            } catch (IOException e) {
                log.error("HDFS删除失败", e);
                return false;
            }
        } else {
            // 使用本地文件系统
            log.info("使用本地文件系统删除");
            boolean flag = false;
            File file = new File(filePath);
            // 路径为文件且不为空则进行删除
            if (file.isFile() && file.exists())
            {
                flag = file.delete();
                log.info("本地文件删除结果: {}", flag);
            } else {
                log.warn("本地文件不存在或不是文件: {}", filePath);
            }
            return flag;
        }
    }

    /**
     * 文件名称验证
     * 
     * @param filename 文件名称
     * @return true 正常 false 非法
     */
    public static boolean isValidFilename(String filename)
    {
        return filename.matches(FILENAME_PATTERN);
    }

    /**
     * 检查文件是否可下载
     * 
     * @param resource 需要下载的文件
     * @return true 正常 false 非法
     */
    public static boolean checkAllowDownload(String resource)
    {
        // 禁止目录上跳级别
        if (StringUtils.contains(resource, ".."))
        {
            return false;
        }

        // 检查允许下载的文件规则
        if (ArrayUtils.contains(MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, FileTypeUtils.getFileType(resource)))
        {
            return true;
        }

        // 不在允许下载的文件规则
        return false;
    }

    /**
     * 下载文件名重新编码
     * 
     * @param request 请求对象
     * @param fileName 文件名
     * @return 编码后的文件名
     */
    public static String setFileDownloadHeader(HttpServletRequest request, String fileName) throws UnsupportedEncodingException
    {
        final String agent = request.getHeader("USER-AGENT");
        String filename = fileName;
        if (agent.contains("MSIE"))
        {
            // IE浏览器
            filename = URLEncoder.encode(filename, "utf-8");
            filename = filename.replace("+", " ");
        }
        else if (agent.contains("Firefox"))
        {
            // 火狐浏览器
            filename = new String(fileName.getBytes(), "ISO8859-1");
        }
        else if (agent.contains("Chrome"))
        {
            // google浏览器
            filename = URLEncoder.encode(filename, "utf-8");
        }
        else
        {
            // 其它浏览器
            filename = URLEncoder.encode(filename, "utf-8");
        }
        return filename;
    }

    /**
     * 下载文件名重新编码
     *
     * @param response 响应对象
     * @param realFileName 真实文件名
     */
    public static void setAttachmentResponseHeader(HttpServletResponse response, String realFileName) throws UnsupportedEncodingException
    {
        String percentEncodedFileName = percentEncode(realFileName);

        StringBuilder contentDispositionValue = new StringBuilder();
        contentDispositionValue.append("attachment; filename=")
                .append(percentEncodedFileName)
                .append(";")
                .append("filename*=")
                .append("utf-8''")
                .append(percentEncodedFileName);

        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        response.setHeader("Content-disposition", contentDispositionValue.toString());
        response.setHeader("download-filename", percentEncodedFileName);
    }

    /**
     * 百分号编码工具方法
     *
     * @param s 需要百分号编码的字符串
     * @return 百分号编码后的字符串
     */
    public static String percentEncode(String s) throws UnsupportedEncodingException
    {
        String encode = URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        return encode.replaceAll("\\+", "%20");
    }

    /**
     * 获取图像后缀
     * 
     * @param photoByte 图像数据
     * @return 后缀名
     */
    public static String getFileExtendName(byte[] photoByte)
    {
        String strFileExtendName = "jpg";
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70) && (photoByte[3] == 56)
                && ((photoByte[4] == 55) || (photoByte[4] == 57)) && (photoByte[5] == 97))
        {
            strFileExtendName = "gif";
        }
        else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73) && (photoByte[9] == 70))
        {
            strFileExtendName = "jpg";
        }
        else if ((photoByte[0] == 66) && (photoByte[1] == 77))
        {
            strFileExtendName = "bmp";
        }
        else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71))
        {
            strFileExtendName = "png";
        }
        return strFileExtendName;
    }

    /**
     * 获取文件名称 /profile/upload/2022/04/16/ruoyi.png -- ruoyi.png
     * 
     * @param fileName 路径名称
     * @return 没有文件路径的名称
     */
    public static String getName(String fileName)
    {
        if (fileName == null)
        {
            return null;
        }
        int lastUnixPos = fileName.lastIndexOf('/');
        int lastWindowsPos = fileName.lastIndexOf('\\');
        int index = Math.max(lastUnixPos, lastWindowsPos);
        return fileName.substring(index + 1);
    }

    /**
     * 获取不带后缀文件名称 /profile/upload/2022/04/16/ruoyi.png -- ruoyi
     * 
     * @param fileName 路径名称
     * @return 没有文件路径和后缀的名称
     */
    public static String getNameNotSuffix(String fileName)
    {
        if (fileName == null)
        {
            return null;
        }
        String baseName = FilenameUtils.getBaseName(fileName);
        return baseName;
    }
}
