#!/bin/bash

echo "========================================"
echo " 启动网盘系统 (启用HDFS)"
echo "========================================"
echo ""

cd netdisk-admin

echo "正在启动应用..."
echo "HDFS配置: hdfs://192.168.88.161:8020"
echo ""

java -Djava.security.manager=allow \
     -jar target/netdisk-admin.jar \
     --spring.profiles.active=dev

