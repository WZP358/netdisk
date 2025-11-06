# Hadoop HDFS 网盘快速开始指南

## 🎯 改造完成！

您的网盘系统已成功改造为支持 **Hadoop HDFS 分布式存储**！

## ✅ 改造内容总结

### 1. 核心功能
- ✅ 支持 HDFS 分布式存储
- ✅ 支持本地文件系统存储
- ✅ 可通过配置灵活切换存储模式
- ✅ 支持普通文件上传到 HDFS
- ✅ 支持大文件分片上传到 HDFS
- ✅ 支持从 HDFS 下载文件
- ✅ 支持删除 HDFS 文件
- ✅ HDFS 分片文件自动合并

### 2. 新增文件
```
backend/
├── netdisk-common/
│   ├── pom.xml (已添加Hadoop依赖)
│   └── src/main/java/com/gzu/common/
│       ├── config/
│       │   └── HdfsConfig.java (新增)
│       └── utils/
│           ├── hdfs/
│           │   └── HdfsUtils.java (新增)
│           └── file/
│               ├── FileUploadUtils.java (已修改)
│               └── FileUtils.java (已修改)
├── netdisk-api/
│   └── src/main/java/com/gzu/disk/service/impl/
│       └── BackFileServiceImpl.java (已修改)
├── netdisk-admin/
│   └── src/main/resources/
│       ├── application.yml (已添加HDFS配置)
│       ├── application-local.yml (已添加HDFS配置)
│       ├── application-dev.yml (已添加HDFS配置)
│       └── application-prod.yml (已添加HDFS配置)
├── HADOOP_HDFS_README.md (详细文档)
└── QUICK_START.md (本文件)
```

## 🚀 快速开始

### 方式一：本地模式（无需Hadoop）

如果您**暂时没有Hadoop集群**，可以继续使用本地存储模式：

1. **确认配置**
   ```yaml
   # application.yml 或 application-local.yml
   hdfs:
     enable: false  # 使用本地存储
   ```

2. **启动项目**
   ```bash
   cd backend
   mvn clean package
   java -jar netdisk-admin/target/netdisk-admin.jar
   ```

3. **正常使用**
   - 所有文件操作将使用本地文件系统
   - 文件存储在：`E:/code/netdisk/backend/uploadPath/`

### 方式二：HDFS模式（推荐）

如果您**已有Hadoop集群**或**想体验HDFS**：

#### Step 1: 安装 Hadoop（如果还没有）

**Windows开发环境快速安装：**
```bash
# 1. 下载 Hadoop 3.3.4
# https://hadoop.apache.org/releases.html

# 2. 解压到 C:\hadoop

# 3. 配置环境变量
HADOOP_HOME = C:\hadoop
PATH 添加: %HADOOP_HOME%\bin

# 4. 配置 core-site.xml
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://localhost:9000</value>
  </property>
</configuration>

# 5. 配置 hdfs-site.xml
<configuration>
  <property>
    <name>dfs.replication</name>
    <value>1</value>
  </property>
  <property>
    <name>dfs.namenode.name.dir</name>
    <value>file:///C:/hadoop/data/namenode</value>
  </property>
  <property>
    <name>dfs.datanode.data.dir</name>
    <value>file:///C:/hadoop/data/datanode</value>
  </property>
</configuration>

# 6. 格式化NameNode（首次）
hdfs namenode -format

# 7. 启动HDFS
start-dfs.cmd
```

**Linux/Mac开发环境：**
```bash
# 使用Docker快速启动
docker run -d \
  --name hadoop \
  -p 9000:9000 \
  -p 9870:9870 \
  -p 8088:8088 \
  sequenceiq/hadoop-docker:2.7.0
```

#### Step 2: 修改项目配置

编辑 `application-local.yml`：
```yaml
hdfs:
  enable: true
  namenode: hdfs://localhost:9000
  user: hadoop
  basePath: /netdisk
```

#### Step 3: 创建HDFS目录

```bash
# 创建基础目录
hdfs dfs -mkdir -p /netdisk/upload
hdfs dfs -mkdir -p /netdisk/file

# 设置权限
hdfs dfs -chmod -R 755 /netdisk
```

#### Step 4: 启动项目

```bash
cd backend
mvn clean package
java -jar netdisk-admin/target/netdisk-admin.jar
```

#### Step 5: 测试验证

1. **上传文件**
   - 登录系统：http://localhost:8081
   - 上传一个测试文件

2. **验证HDFS**
   ```bash
   # 查看HDFS文件
   hdfs dfs -ls -R /netdisk
   
   # 输出示例：
   # /netdisk/upload/abc_test.txt
   ```

3. **下载文件**
   - 在系统中下载刚上传的文件
   - 验证文件内容正确

## 📊 配置说明

### HDFS配置参数详解

```yaml
hdfs:
  # 是否启用HDFS（核心开关）
  # true: 文件存储到HDFS
  # false: 文件存储到本地
  enable: false
  
  # HDFS NameNode地址
  # 格式: hdfs://主机名或IP:端口
  # 默认端口: 9000
  namenode: hdfs://localhost:9000
  
  # HDFS用户名
  # 建议使用有写权限的用户
  user: hadoop
  
  # HDFS基础路径
  # 所有文件将存储在此目录下
  basePath: /netdisk
```

### 不同环境的推荐配置

| 环境 | enable | namenode | 说明 |
|------|--------|----------|------|
| local | false | localhost:9000 | 本地开发，使用本地存储 |
| dev | false | hadoop-dev:9000 | 开发环境，可选HDFS |
| prod | true | hadoop-cluster:9000 | 生产环境，使用HDFS |

## 🔍 验证和监控

### 检查HDFS状态

```bash
# 查看HDFS集群状态
hdfs dfsadmin -report

# 查看Web界面
# 打开浏览器访问: http://localhost:9870
```

### 查看应用日志

启动日志应该包含：
```
HDFS配置已加载
FileSystem initialized: hdfs://localhost:9000
HDFS基础目录创建成功: /netdisk
```

上传成功日志：
```
文件上传到HDFS成功: /netdisk/upload/xxx.txt
```

## 🎨 架构示意图

```
┌─────────────────────────────────────┐
│         用户浏览器                   │
└──────────────┬──────────────────────┘
               │ HTTP/HTTPS
┌──────────────┴──────────────────────┐
│      Spring Boot 应用                │
│  ┌────────────────────────────┐     │
│  │  Controller层               │     │
│  └──────────┬─────────────────┘     │
│  ┌──────────┴─────────────────┐     │
│  │  Service层                  │     │
│  └──────────┬─────────────────┘     │
│  ┌──────────┴─────────────────┐     │
│  │  FileUploadUtils/FileUtils │     │
│  │  (智能路由)                 │     │
│  └──────────┬─────────────────┘     │
│             │                        │
│    ┌────────┴────────┐               │
│    │                 │               │
│  ┌─┴──────┐    ┌────┴─────┐         │
│  │ HDFS   │    │  本地     │         │
│  │ Utils  │    │  文件系统 │         │
│  └─┬──────┘    └──────────┘         │
└────┼─────────────────────────────────┘
     │ Hadoop RPC
┌────┴─────────────────────────────────┐
│        Hadoop HDFS 集群               │
│  ┌──────────┐  ┌─────────────┐      │
│  │ NameNode │  │ DataNode(s) │      │
│  └──────────┘  └─────────────┘      │
└──────────────────────────────────────┘
```

## 💡 常见问题

### Q1: 如何知道当前使用的是HDFS还是本地存储？

**A**: 查看启动日志或上传文件时的日志：
- 看到 "文件上传到HDFS成功" → 使用HDFS
- 看到 "文件块写入本地成功" → 使用本地

### Q2: 切换存储模式后，旧文件怎么办？

**A**: 旧文件不会自动迁移，有以下选择：
1. **保持双模式**：HDFS和本地同时可用
2. **手动迁移**：使用脚本迁移旧文件
3. **逐步迁移**：新文件用新模式，旧文件保持不变

### Q3: HDFS连接不上怎么办？

**A**: 检查清单：
1. ✅ Hadoop是否启动？`jps` 查看进程
2. ✅ 端口是否正确？默认 9000
3. ✅ 防火墙是否开放？
4. ✅ 配置文件地址是否正确？

### Q4: 性能如何？

**A**: 性能对比：
- **小文件（< 1MB）**: 本地更快
- **大文件（> 100MB）**: HDFS更优
- **高并发**: HDFS更好（分布式）

### Q5: 生产环境建议？

**A**: 生产环境建议：
1. ✅ 使用HDFS集群（至少3个节点）
2. ✅ 配置副本数为3（数据安全）
3. ✅ 启用HDFS HA（高可用）
4. ✅ 配置资源监控（Prometheus）
5. ✅ 定期备份元数据

## 📚 更多文档

- **详细部署文档**: [HADOOP_HDFS_README.md](./HADOOP_HDFS_README.md)
- **API文档**: http://localhost:8081/swagger-ui/
- **HDFS Web UI**: http://localhost:9870

## 🎉 下一步

### 推荐操作顺序

1. ✅ **测试本地模式**
   - 确保项目基本功能正常

2. ✅ **安装Hadoop**
   - 开发环境可用单机版

3. ✅ **开启HDFS模式**
   - 修改配置 `hdfs.enable=true`

4. ✅ **功能测试**
   - 上传、下载、删除、分片上传

5. ✅ **性能测试**
   - 测试大文件、高并发

6. ✅ **生产部署**
   - 搭建HDFS集群
   - 配置高可用
   - 数据迁移

## 🆘 获取帮助

如有问题：
1. 查看日志：`logs/netdisk.log`
2. 查看HDFS日志：`$HADOOP_HOME/logs/`
3. 查阅文档：`HADOOP_HDFS_README.md`

## 🎊 祝贺

恭喜！您已成功将网盘系统改造为支持Hadoop HDFS的分布式存储系统！

**开始享受大数据存储的便利吧！** 🚀

