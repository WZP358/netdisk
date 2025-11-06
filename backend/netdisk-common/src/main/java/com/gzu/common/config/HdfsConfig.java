package com.gzu.common.config;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * HDFS配置类
 * 
 * @author netdisk
 */
@Configuration
public class HdfsConfig {
    
    private static final Logger log = LoggerFactory.getLogger(HdfsConfig.class);

    @Value("${hdfs.namenode:hdfs://localhost:9000}")
    private String namenode;

    @Value("${hdfs.user:hadoop}")
    private String user;

    @Value("${hdfs.enable:false}")
    private Boolean enable;

    @Value("${hdfs.basePath:/netdisk}")
    private String basePath;

    /**
     * 创建HDFS文件系统Bean
     * 仅当 hdfs.enable=true 时才创建
     * 如果初始化失败，返回null以允许应用继续启动
     */
    @Bean
    @ConditionalOnProperty(name = "hdfs.enable", havingValue = "true")
    public FileSystem hdfsFileSystem() {
        
        log.info("正在初始化HDFS文件系统，NameNode地址: {}", namenode);
        
        try {
            // 为 Java 17+ 设置系统属性以支持 Hadoop
            System.setProperty("HADOOP_USER_NAME", user);
            
            org.apache.hadoop.conf.Configuration configuration = new org.apache.hadoop.conf.Configuration();
            // 设置文件系统类型
            configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
            // 副本数量
            configuration.set("dfs.replication", "1");
            // 设置块大小 128MB
            configuration.set("dfs.blocksize", "134217728");
            
            // 禁用 Hadoop 的 Security Manager 检查
            configuration.set("hadoop.security.authentication", "simple");
            configuration.set("hadoop.security.authorization", "false");
            
            // 为 Java 17+ 设置 UserGroupInformation，避免 Subject.getSubject() 问题
            UserGroupInformation.setConfiguration(configuration);
            
            FileSystem fileSystem = FileSystem.get(new URI(namenode), configuration, user);
            
            // 创建基础目录
            org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(basePath);
            if (!fileSystem.exists(path)) {
                fileSystem.mkdirs(path);
                log.info("已创建HDFS基础目录: {}", basePath);
            }
            
            log.info("HDFS文件系统初始化成功");
            return fileSystem;
            
        } catch (UnsupportedOperationException e) {
            if (e.getMessage() != null && e.getMessage().contains("getSubject is supported only if a security manager is allowed")) {
                log.error("┌─────────────────────────────────────────────────────────────┐");
                log.error("│  HDFS初始化失败：Java 17+ SecurityManager 不兼容问题      │");
                log.error("├─────────────────────────────────────────────────────────────┤");
                log.error("│  解决方案：添加 JVM 启动参数                              │");
                log.error("│  -Djava.security.manager=allow                             │");
                log.error("│                                                             │");
                log.error("│  配置步骤（IntelliJ IDEA）：                               │");
                log.error("│  1. Run -> Edit Configurations...                          │");
                log.error("│  2. 选择您的应用（App）                                    │");
                log.error("│  3. 点击 'Modify options' -> 'Add VM options'              │");
                log.error("│  4. 在 VM options 输入框中添加上述参数                    │");
                log.error("│  5. 点击 Apply 和 OK，重新启动应用                        │");
                log.error("│                                                             │");
                log.error("│  临时方案：在 application-dev.yml 中设置                  │");
                log.error("│  hdfs.enable: false                                        │");
                log.error("│                                                             │");
                log.error("│  应用将继续启动，但HDFS功能不可用                          │");
                log.error("└─────────────────────────────────────────────────────────────┘");
                return null; // 返回null允许应用继续启动
            }
            log.error("HDFS初始化失败，未知错误", e);
            return null;
        } catch (Exception e) {
            log.error("HDFS初始化失败: {}", e.getMessage(), e);
            log.warn("HDFS将不可用，应用将继续启动并使用本地文件存储");
            return null;
        }
    }

    public String getNamenode() {
        return namenode;
    }

    public void setNamenode(String namenode) {
        this.namenode = namenode;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Boolean getEnable() {
        return enable;
    }

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}

