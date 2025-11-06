package com.gzu.web.controller.common;

import com.gzu.common.config.RuoYiConfig;
import com.gzu.common.constant.Constants;
import com.gzu.common.utils.StringUtils;
import com.gzu.common.utils.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 文件访问控制器 - 支持HDFS和本地文件系统
 * 
 * @author netdisk
 */
@RestController
@RequestMapping("/profile")
public class FileAccessController {
    
    private static final Logger log = LoggerFactory.getLogger(FileAccessController.class);

    /**
     * 文件访问接口
     * 支持访问路径: /profile/upload/xxx/file.ext
     */
    @GetMapping("/**")
    public void accessFile(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 获取请求的完整路径
            String requestPath = request.getRequestURI();
            log.info("========== 文件访问请求开始 ==========");
            log.info("原始请求路径: {}", requestPath);
            
            // URL解码
            try {
                requestPath = java.net.URLDecoder.decode(requestPath, "UTF-8");
                log.info("URL解码后: {}", requestPath);
            } catch (Exception e) {
                log.warn("URL解码失败，使用原始路径", e);
            }
            
            // 提取相对路径（去掉/profile前缀）
            String relativePath = requestPath;
            if (relativePath.startsWith("/profile")) {
                relativePath = relativePath.substring("/profile".length());
            }
            log.info("相对路径: {}", relativePath);
            
            // 构建完整路径
            String filePath = RuoYiConfig.getProfile() + relativePath;
            log.info("本地文件路径: {}", filePath);
            
            // 获取文件名
            String fileName = StringUtils.substringAfterLast(relativePath, "/");
            log.info("文件名: {}", fileName);
            
            // 根据文件扩展名设置Content-Type
            String contentType = getContentType(fileName);
            response.setContentType(contentType);
            log.info("Content-Type: {}", contentType);
            
            // 设置响应头，允许浏览器直接显示
            if (isImageFile(fileName) || isPdfFile(fileName) || isVideoFile(fileName)) {
                // 图片、PDF、视频直接显示
                response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
                log.info("设置为内联显示");
            } else {
                // 其他文件作为附件下载
                FileUtils.setAttachmentResponseHeader(response, fileName);
                log.info("设置为附件下载");
            }
            
            // 输出文件内容（自动支持HDFS和本地）
            log.info("开始写入文件内容到响应流...");
            FileUtils.writeBytes(filePath, response.getOutputStream());
            log.info("文件内容写入完成");
            log.info("========== 文件访问请求结束 ==========");
            
        } catch (Exception e) {
            log.error("========== 文件访问失败 ==========", e);
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误信息: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
    
    /**
     * 获取Content-Type
     */
    private String getContentType(String fileName) {
        String ext = StringUtils.substringAfterLast(fileName, ".").toLowerCase();
        switch (ext) {
            case "png":
                return MediaType.IMAGE_PNG_VALUE;
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG_VALUE;
            case "gif":
                return MediaType.IMAGE_GIF_VALUE;
            case "pdf":
                return MediaType.APPLICATION_PDF_VALUE;
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mpeg";
            case "txt":
                return MediaType.TEXT_PLAIN_VALUE;
            case "json":
                return MediaType.APPLICATION_JSON_VALUE;
            case "xml":
                return MediaType.APPLICATION_XML_VALUE;
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
    
    /**
     * 判断是否是图片文件
     */
    private boolean isImageFile(String fileName) {
        String ext = StringUtils.substringAfterLast(fileName, ".").toLowerCase();
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) 
            || "gif".equals(ext) || "bmp".equals(ext) || "webp".equals(ext);
    }
    
    /**
     * 判断是否是PDF文件
     */
    private boolean isPdfFile(String fileName) {
        String ext = StringUtils.substringAfterLast(fileName, ".").toLowerCase();
        return "pdf".equals(ext);
    }
    
    /**
     * 判断是否是视频文件
     */
    private boolean isVideoFile(String fileName) {
        String ext = StringUtils.substringAfterLast(fileName, ".").toLowerCase();
        return "mp4".equals(ext) || "avi".equals(ext) || "mkv".equals(ext) 
            || "mov".equals(ext) || "wmv".equals(ext) || "flv".equals(ext);
    }
}

