-- 修复 disk_file 表中 del_flag 为 NULL 的记录
-- 这个脚本将所有 del_flag 为 NULL 的记录设置为 '0'（表示文件存在）

-- 1. 查看有多少记录的 del_flag 为 NULL
SELECT COUNT(*) as null_count FROM disk_file WHERE del_flag IS NULL;

-- 2. 显示这些记录的详细信息
SELECT id, name, create_id, create_time, del_flag 
FROM disk_file 
WHERE del_flag IS NULL 
ORDER BY create_time DESC;

-- 3. 修复这些记录，将 del_flag 设置为 '0'
UPDATE disk_file 
SET del_flag = '0' 
WHERE del_flag IS NULL;

-- 4. 验证修复结果
SELECT COUNT(*) as fixed_count FROM disk_file WHERE del_flag = '0';
SELECT COUNT(*) as null_count FROM disk_file WHERE del_flag IS NULL;

-- 5. 显示最近修复的记录
SELECT id, name, create_id, create_time, del_flag 
FROM disk_file 
WHERE del_flag = '0'
ORDER BY create_time DESC 
LIMIT 20;
