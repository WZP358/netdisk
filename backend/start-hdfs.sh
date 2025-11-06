#!/bin/bash

echo "========================================"
echo "  启动 Hadoop HDFS 网盘系统"
echo "========================================"
echo

cd "$(dirname "$0")"

if [ ! -f "netdisk-admin/target/netdisk-admin.jar" ]; then
    echo "[错误] 未找到 netdisk-admin.jar"
    echo "请先执行: mvn clean package -DskipTests"
    exit 1
fi

echo "启动应用（支持 Java 17+）..."
echo

java -Djava.security.manager=allow \
     -jar netdisk-admin/target/netdisk-admin.jar

