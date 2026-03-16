# AIOps 智能运维平台 - 数据生命周期管理文档

## 1. 数据分类与生命周期策略

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                           数据生命周期模型                                                   │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│   数据类型        存储位置        保留策略        压缩策略        访问延迟        备份频率  │
│   ────────────────────────────────────────────────────────────────────────────────────      │
│                                                                                             │
│   热数据          Doris 本地      7 天           LZ4              毫秒级          每日      │
│   (实时查询)      (SSD)                                                        Snapshot     │
│                                                                                             │
│   温数据          Doris 冷存储    30 天          Zstandard         秒级            每周      │
│   (历史分析)      (HDD)                                                                     │
│                                                                                             │
│   冷数据          OSS/S3         90 天           ZSTD + 列关联      分钟级         每月      │
│   (归档审计)                                                                                │
│                                                                                             │
│   永久归档        OSS 归档类型    3-7 年         ZSTD + 分片        小时级         每年      │
│   (合规)           (IA/Archive)                                                             │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.1 数据分层架构

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              数据分层架构                                                    │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│   应用层                                                                                    │
│      │                                                                                      │
│      ▼                                                                                      │
│   ┌────────────────────────────────────────────────────────────────────────────────────┐   │
│   │                        统一查询接口 (Federation Layer)                              │   │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │   │
│   │  │   热数据查询  │  │   温数据查询  │  │   冷数据查询  │  │   归档查询    │           │   │
│   │  │  (Doris BE)  │  │  (Doris BE)  │  │  (OSS 读取)  │  │  (OSS 解冻)  │           │   │
│   │  └──┬───────────┘  └──┬───────────┘  └──┬───────────┘  └──┬───────────┘           │   │
│   └─────┼────────────────┼────────────────┼────────────────┼────────────────────────┘   │
│         │                │                │                │                           │
│   ┌─────▼────────┐ ┌────▼────────┐ ┌─────▼────────┐ ┌─────▼────────┐                   │
│   │              │ │              │ │              │ │              │                   │
│   │   热数据层    │ │   温数据层    │ │   冷数据层    │ │   归档层      │                   │
│   │  (7 天内)    │ │  (7-30 天)   │ │  (30-90 天)  │ │  (90 天+)    │                   │
│   │              │ │              │ │              │ │              │                   │
│   │ Doris BE     │ │ Doris BE     │ │              │ │              │                   │
│   │ 本地 SSD     │ │ 冷数据盘      │ │ OSS 标准类型  │ │ OSS IA/归档   │                   │
│   │              │ │              │ │              │ │              │                   │
│   │ Size: 50GB   │ │ Size: 200GB  │ │ Size: 1TB    │ │ Size: 10TB   │                   │
│   │ /天          │ │ /天          │ │ /天          │ │ /年          │                   │
│   └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘                   │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Doris 数据生命周期管理

### 2.1 动态分区配置

```sql
-- Doris 动态分区配置
-- 自动创建未来 3 天分区，保留最近 30 天数据

ALTER TABLE aiops.logs
SET (
    "dynamic_partition.enable" = "true",
    -- 按天分区
    "dynamic_partition.time_unit" = "DAY",
    -- 保留 30 天
    "dynamic_partition.start" = "-30",
    -- 提前创建未来 3 天分区
    "dynamic_partition.end" = "3",
    -- 每个分区 16 桶
    "dynamic_partition.buckets" = "16",
    -- 创建历史分区
    "dynamic_partition.create_history_partition" = "true",
    -- 历史分区数量
    "dynamic_partition.history_partition_num" = "30"
);

-- 热温分离表配置
-- 日志表：近 7 天数据存热盘，7-30 天降冷存储

CREATE TABLE IF NOT EXISTS aiops.logs (
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `service_id` VARCHAR(64) NOT NULL,
    `level` VARCHAR(8) NOT NULL,
    -- ... 其他字段
    INDEX idx_message (`message`) USING INVERTED,
    INDEX idx_trace (`trace_id`) USING BITMAP
)
DUPLICATE KEY(`timestamp`, `tenant_id`, `service_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`service_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "enable_unique_key_merge_on_write" = "true",
    -- 磁盘容量管理
    "storage_medium" = "SSD",
    -- 自动 TTL
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-30",
    "dynamic_partition.end" = "3",
    "dynamic_partition.buckets" = "16"
);
```

### 2.2 数据降冷策略

```python
# data_lifecycle/doris_tiering.py
from datetime import datetime, timedelta
from typing import List


class DorisTieringManager:
    """Doris 数据分层管理器"""

    def __init__(self, doris_client, storage_config: dict):
        self.doris = doris_client
        self.storage = storage_config

    async def migrate_to_cold_storage(self, database: str, table: str, days: int):
        """迁移数据到冷存储"""
        # 1. 查询需要降冷的分区
        partitions = await self._get_partitions_older_than(database, table, days)

        for partition in partitions:
            # 2. 导出分区数据到 OSS
            await self._export_partition_to_oss(database, table, partition)

            # 3. 修改分区存储介质为 HDD
            await self._set_partition_storage_medium(
                database, table, partition, "HDD"
            )

            print(f"Migrated partition {partition} to cold storage")

    async def _get_partitions_older_than(
        self,
        database: str,
        table: str,
        days: int
    ) -> List[str]:
        """获取超过指定天数的分区"""
        sql = f"""
        SHOW PARTITIONS FROM {database}.{table};
        """
        result = await self.doris.execute(sql)

        cutoff_date = datetime.now() - timedelta(days=days)
        old_partitions = []

        for row in result:
            partition_name = row['PartitionName']
            # 解析分区名中的日期 (如 p20240115)
            if partition_name.startswith('p'):
                try:
                    partition_date = datetime.strptime(
                        partition_name[1:], '%Y%m%d'
                    )
                    if partition_date < cutoff_date:
                        old_partitions.append(partition_name)
                except ValueError:
                    continue

        return old_partitions

    async def _export_partition_to_oss(
        self,
        database: str,
        table: str,
        partition: str
    ):
        """导出分区到 OSS"""
        # 创建导出任务
        export_label = f"export_{table}_{partition}_{int(datetime.now().timestamp())}"

        export_sql = f"""
        EXPORT TABLE {database}.{table}
        PARTITION ({partition})
        TO "oss://aiops-backup/doris-cold/{database}/{table}/{partition}/"
        PROPERTIES (
            "column_separator" = ",",
            "line_delimiter" = "\\n",
            "format" = "parquet"
        )
        WITH BROKER "oss_broker"
        PROPERTIES (
            "fs.oss.accessKeyId" = "{self.storage['oss_access_key']}",
            "fs.oss.accessKeySecret" = "{self.storage['oss_secret_key']}",
            "fs.oss.endpoint" = "{self.storage['oss_endpoint']}"
        );
        """

        await self.doris.execute(export_sql)

    async def _set_partition_storage_medium(
        self,
        database: str,
        table: str,
        partition: str,
        medium: str
    ):
        """设置分区存储介质"""
        # 修改存储介质需要重建分区
        # 这里简化为修改表属性
        pass


class DorisCompactionOptimizer:
    """Doris Compaction 优化"""

    def __init__(self, doris_client):
        self.doris = doris_client

    async def optimize_table_compaction(self, database: str, table: str):
        """优化表的Compaction策略"""
        optimization_sql = f"""
        ALTER TABLE {database}.{table}
        SET (
            -- 自动 Compaction
            "enable_single_replica_compaction" = "true",
            -- 最大 Compaction 线程
            "max_compaction_threads" = "8",
            -- 垂直 Compaction 阈值
            "vertical_compaction_max_columns_per_group" = "5"
        );
        """
        await self.doris.execute(optimization_sql)

    async def trigger_manual_compaction(self, database: str, table: str):
        """手动触发Compaction"""
        compact_sql = f"CANCEL ALTER SYSTEM FROM {database}.{table};"
        try:
            await self.doris.execute(compact_sql)
        except:
            pass

        compact_sql = f"ALTER TABLE {database}.{table} COMPACT;"
        await self.doris.execute(compact_sql)
```

### 2.3 数据清理定时任务

```python
# data_lifecycle/doris_cleanup.py
import asyncio
from datetime import datetime, timedelta
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger


class DorisCleanupScheduler:
    """Doris 数据清理调度器"""

    def __init__(self, doris_client):
        self.doris = doris_client
        self.scheduler = AsyncIOScheduler()

    def start(self):
        """启动清理调度"""
        # 每日凌晨 2 点执行
        self.scheduler.add_job(
            self.cleanup_expired_partitions,
            CronTrigger(hour=2, minute=0),
            id='cleanup_partitions',
            replace_existing=True
        )

        # 每周日凌晨 3 点执行 Vacuum
        self.scheduler.add_job(
            self.vacuum_tables,
            CronTrigger(day_of_week='sun', hour=3, minute=0),
            id='vacuum_tables',
            replace_existing=True
        )

        self.scheduler.start()

    async def cleanup_expired_partitions(self):
        """清理过期分区"""
        tables_with_ttl = [
            ('aiops', 'logs', 30),           # 日志保留 30 天
            ('aiops', 'alerts', 90),         # 告警保留 90 天
            ('aiops', 'traces', 7),          # Trace 保留 7 天
            ('aiops', 'ai_analysis', 365),   # AI 分析结果保留 1 年
        ]

        for database, table, days in tables_with_ttl:
            try:
                await self._drop_expired_partitions(database, table, days)
            except Exception as e:
                print(f"Failed to cleanup {database}.{table}: {e}")

    async def _drop_expired_partitions(
        self,
        database: str,
        table: str,
        retention_days: int
    ):
        """删除过期分区"""
        cutoff_date = datetime.now() - timedelta(days=retention_days)
        partition_suffix = cutoff_date.strftime('%Y%m%d')

        # Doris 的 DROP PARTITION 会删除整个分区数据
        # 这里假设分区名为 pYYYYMMDD
        drop_sql = f"""
        ALTER TABLE {database}.{table}
        DROP PARTITION IF EXISTS p{partition_suffix};
        """

        await self.doris.execute(drop_sql)
        print(f"Dropped partition p{partition_suffix} from {database}.{table}")

    async def vacuum_tables(self):
        """执行 Vacuum 回收空间"""
        tables = ['logs', 'alerts', 'traces', 'metrics']

        for table in tables:
            vacuum_sql = f"VACUUM TABLE aiops.{table};"
            try:
                await self.doris.execute(vacuum_sql)
            except Exception as e:
                print(f"Vacuum {table} failed: {e}")

    async def archive_old_data(self):
        """归档旧数据"""
        archive_configs = [
            {
                'table': 'logs',
                'older_than_days': 7,
                'destination': 'oss://aiops-archive/logs/',
                'format': 'parquet'
            },
            {
                'table': 'traces',
                'older_than_days': 3,
                'destination': 'oss://aiops-archive/traces/',
                'format': 'parquet'
            }
        ]

        for config in archive_configs:
            await self._export_and_delete(
                config['table'],
                config['older_than_days'],
                config['destination'],
                config['format']
            )

    async def _export_and_delete(
        self,
        table: str,
        days: int,
        destination: str,
        format: str
    ):
        """导出并删除数据"""
        # 导出到 OSS
        export_sql = f"""
        EXPORT TABLE aiops.{table}
        TO "{destination}{datetime.now().strftime('%Y/%m/%d')}/"
        PROPERTIES (
            "format" = "{format}"
        )
        WHERE timestamp < days_add(now(), -{days});
        """
        await self.doris.execute(export_sql)
```

---

## 3. OSS 数据归档策略

### 3.1 对象存储归档配置

```python
# data_lifecycle/oss_archiver.py
import oss2
from datetime import datetime, timedelta
from typing import Optional


class OSSArchiver:
    """OSS 归档管理器"""

    # 存储类型
    STORAGE_CLASSES = {
        'STANDARD': oss2.BUCKET_STORAGE_CLASS_STANDARD,      # 标准
        'IA': oss2.BUCKET_STORAGE_CLASS_IA,                  # 低频访问
        'ARCHIVE': oss2.BUCKET_STORAGE_CLASS_ARCHIVE,        # 归档
        'COLDARCHIVE': oss2.BUCKET_STORAGE_CLASS_COLD_ARCHIVE  # 冷归档
    }

    def __init__(self, access_key: str, secret_key: str, bucket: str, endpoint: str):
        self.auth = oss2.Auth(access_key, secret_key)
        self.bucket = oss2.Bucket(self.auth, endpoint, bucket)

    async def archive_data(
        self,
        source_prefix: str,
        dest_prefix: str,
        older_than_days: int,
        storage_class: str = 'IA'
    ):
        """归档数据到指定存储类型"""
        cutoff_date = datetime.now() - timedelta(days=older_than_days)

        # 列出需要归档的对象
        marker = ''
        while True:
            result = self.bucket.list_objects(
                prefix=source_prefix,
                marker=marker,
                max_keys=1000
            )

            for obj in result.object_list:
                # 检查最后修改时间
                if obj.last_modified < cutoff_date:
                    # 复制到归档路径
                    new_key = obj.key.replace(source_prefix, dest_prefix)

                    # 修改存储类型
                    self.bucket.copy_object(
                        self.bucket.bucket_name,
                        obj.key,
                        new_key,
                        params={'x-oss-storage-class': self.STORAGE_CLASSES[storage_class]}
                    )

                    # 删除原对象（可选）
                    # self.bucket.delete_object(obj.key)

            if not result.is_truncated:
                break
            marker = result.next_marker

    async def restore_from_archive(
        self,
        object_key: str,
        days: int = 7,
        tier: str = 'Expedited'
    ):
        """从归档恢复数据"""
        # 解冻归档对象
        self.bucket.restore_object(object_key)

        # 等待解冻完成（可选）
        metadata = self.bucket.head_object(object_key)
        while 'ongoing-request="true"' in str(metadata.headers.get('x-oss-restore', '')):
            await asyncio.sleep(60)
            metadata = self.bucket.head_object(object_key)

    def lifecycle_policy(self):
        """配置生命周期策略"""
        from oss2.models import LifecycleRule, LifecycleExpiration

        rules = [
            # 日志数据
            LifecycleRule(
                id='logs-lifecycle',
                prefix='logs/',
                status='Enabled',
                expiration=LifecycleExpiration(days=90),
                transition=[
                    {'days': 7, 'storage_class': 'IA'},
                    {'days': 30, 'storage_class': 'Archive'},
                ]
            ),
            # AI 分析结果
            LifecycleRule(
                id='ai-analysis-lifecycle',
                prefix='ai-analysis/',
                status='Enabled',
                expiration=LifecycleExpiration(days=365),
                transition=[
                    {'days': 30, 'storage_class': 'IA'},
                    {'days': 90, 'storage_class': 'Archive'},
                ]
            ),
            # Doris 备份
            LifecycleRule(
                id='doris-backup-lifecycle',
                prefix='doris-backup/',
                status='Enabled',
                expiration=LifecycleExpiration(days=180),
                transition=[
                    {'days': 7, 'storage_class': 'IA'},
                ]
            ),
        ]

        self.bucket.put_bucket_lifecycle(oss2.models.BucketLifecycle(rules))
```

### 3.2 归档数据查询

```python
# data_lifecycle/archive_query.py
import pandas as pd
import pyarrow.parquet as pq
from typing import List, Dict, Iterator


class ArchiveQueryEngine:
    """归档数据查询引擎"""

    def __init__(self, oss_client, cache_layer):
        self.oss = oss_client
        self.cache = cache_layer  # Redis 缓存

    async def query_archived_logs(
        self,
        start_time: datetime,
        end_time: datetime,
        filters: Dict[str, any],
        limit: int = 1000
    ) -> Iterator[Dict]:
        """查询归档日志"""
        # 1. 检查本地热缓存
        cache_key = f"archive:logs:{start_time.isoformat()}:{end_time.isoformat()}"
        cached = await self.cache.get(cache_key)
        if cached:
            return iter(cached)

        # 2. 确定需要查询的对象
        objects_to_query = self._get_relevant_objects(start_time, end_time)

        # 3. 并行读取对象
        results = []
        for obj_key in objects_to_query:
            df = await self._read_parquet_from_oss(obj_key)

            # 应用过滤器
            filtered = self._apply_filters(df, filters)

            # 限制返回数量
            if len(results) + len(filtered) > limit:
                remaining = limit - len(results)
                results.extend(filtered[:remaining])
                break
            else:
                results.extend(filtered)

        # 4. 写入缓存 (TTL 1 小时)
        await self.cache.setex(cache_key, 3600, results)

        return iter(results)

    def _get_relevant_objects(
        self,
        start_time: datetime,
        end_time: datetime
    ) -> List[str]:
        """获取相关对象路径"""
        objects = []
        current = start_time

        while current <= end_time:
            # 按日期组织的路径: logs/2024/01/15/data.parquet
            obj_path = current.strftime('logs/%Y/%m/%d/')

            # 列出该日期下的所有对象
            objects.extend(
                obj.key for obj in self.oss.list_objects(obj_path)
                if obj.key.endswith('.parquet')
            )

            current += timedelta(days=1)

        return objects

    async def _read_parquet_from_oss(self, obj_key: str) -> pd.DataFrame:
        """从 OSS 读取 Parquet 文件"""
        # 下载到临时文件
        temp_path = f"/tmp/{obj_key.replace('/', '_')}"
        self.oss.get_object_to_file(obj_key, temp_path)

        # 读取 Parquet
        df = pq.read_table(temp_path).to_pandas()

        return df

    async def create_archive_summary(self, date: datetime) -> Dict:
        """创建归档数据摘要"""
        prefix = date.strftime('logs/%Y/%m/%d/')

        total_size = 0
        total_records = 0

        for obj in self.oss.list_objects(prefix):
            total_size += obj.size
            # 估算记录数 (假设每行约 500B)
            total_records += obj.size // 500

        return {
            'date': date.isoformat(),
            'total_size_gb': total_size / 1024 / 1024 / 1024,
            'estimated_records': total_records,
            'objects_count': sum(1 for _ in self.oss.list_objects(prefix))
        }
```

---

## 4. 租户数据隔离与配额

### 4.1 租户配额管理

```python
# data_lifecycle/tenant_quota.py
from dataclasses import dataclass
from typing import Dict
from datetime import datetime


@dataclass
class TenantQuota:
    """租户数据配额"""
    tenant_id: str
    daily_log_limit_gb: float = 100.0
    ai_analysis_quota: int = 1000
    retention_days: int = 30
    archive_enabled: bool = True
    hot_storage_days: int = 7


class TenantQuotaManager:
    """租户配额管理器"""

    def __init__(self, doris, redis):
        self.doris = doris
        self.redis = redis

    async def check_quota(self, tenant_id: str, data_size_gb: float) -> bool:
        """检查租户配额"""
        quota = await self._get_quota(tenant_id)

        # 今日已使用量
        today_usage = await self._get_daily_usage(tenant_id)

        if today_usage + data_size_gb > quota.daily_log_limit_gb:
            # 配额超限，触发告警
            await self._trigger_quota_alert(tenant_id, today_usage, quota)
            return False

        return True

    async def enforce_retention_policy(self, tenant_id: str):
        """强制执行租户的保留策略"""
        quota = await self._get_quota(tenant_id)

        # 删除超过保留期的数据
        tables = ['logs', 'alerts', 'traces']

        for table in tables:
            cutoff_date = datetime.now() - timedelta(days=quota.retention_days)

            cleanup_sql = f"""
            DELETE FROM aiops.{table}
            WHERE tenant_id = '{tenant_id}'
            AND timestamp < '{cutoff_date.strftime('%Y-%m-%d')}';
            """
            await self.doris.execute(cleanup_sql)

    async def migrate_to_cold_for_tenant(self, tenant_id: str):
        """迁移租户数据到冷存储"""
        quota = await self._get_quota(tenant_id)
        cutoff_date = datetime.now() - timedelta(days=quota.hot_storage_days)

        # 查询需要降冷的数据
        tables = ['logs', 'traces']

        for table in tables:
            export_sql = f"""
            EXPORT TABLE aiops.{table}
            TO "oss://aiops-tenant-{tenant_id}/{table}/"
            PROPERTIES ("format" = "parquet")
            WHERE tenant_id = '{tenant_id}'
            AND timestamp < '{cutoff_date.strftime('%Y-%m-%d')}'
            AND timestamp >= '{(cutoff_date - timedelta(days=quota.retention_days)).strftime('%Y-%m-%d')}';
            """
            await self.doris.execute(export_sql)

    async def get_tenant_storage_stats(self, tenant_id: str) -> Dict:
        """获取租户存储统计"""
        stats = {}

        tables = ['logs', 'alerts', 'traces', 'ai_analysis']

        for table in tables:
            size_sql = f"""
            SELECT
                COUNT(*) as row_count,
                COUNT(DISTINCT DATE(timestamp)) as days,
                MIN(timestamp) as oldest_record,
                MAX(timestamp) as latest_record
            FROM aiops.{table}
            WHERE tenant_id = '{tenant_id}';
            """
            result = await self.doris.execute(size_sql)
            stats[table] = result[0] if result else {}

        return stats
```

### 4.2 审计日志保留

```sql
-- 审计日志表 - 永久保存
CREATE TABLE IF NOT EXISTS aiops.audit_logs (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `user_id` VARCHAR(32) NOT NULL,
    `action` VARCHAR(64) NOT NULL,      -- LOGIN, LOGOUT, QUERY, EXPORT, DELETE
    `resource_type` VARCHAR(32),        -- ALERT, LOG, CONFIG
    `resource_id` VARCHAR(64),
    `details` JSON,                     -- 操作详情
    `ip_address` VARCHAR(15),
    `user_agent` VARCHAR(256),
    `success` BOOLEAN DEFAULT TRUE,
    INDEX idx_time_tenant (`timestamp`, `tenant_id`),
    INDEX idx_user (`user_id`),
    INDEX idx_action (`action`)
)
DUPLICATE KEY(`id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`tenant_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "MONTH",
    "dynamic_partition.start" = "-36",    -- 保留 3 年
    "dynamic_partition.end" = "3"
);

-- 审计数据导出到 OSS 永久归档
call export_audit_to_oss('monthly');
```

---

## 5. 数据压缩优化

### 5.1 压缩策略对比

| 压缩算法 | 压缩率 | 解压速度 | CPU 占用 | 适用场景 |
|---------|-------|---------|---------|---------|
| LZ4     | 2:1   | 极快     | 低       | 热数据实时写入 |
| ZSTD    | 5:1   | 快       | 中       | 温数据归档 |
| Snappy  | 2.5:1 | 极快     | 低       | 日志实时压缩 |
| GZIP    | 8:1   | 慢       | 高       | 长期归档 |

### 5.2 Doris 压缩配置

```sql
-- 为不同表配置不同的压缩策略

-- 热数据表 - 使用 LZ4 快速压缩
ALTER TABLE aiops.logs SET (
    "compression" = "LZ4"
);

-- 历史数据表 - 使用 ZSTD 高效压缩
ALTER TABLE aiops.alerts SET (
    "compression" = "ZSTD"
);

-- 索引优化
ALTER TABLE aiops.logs
MODIFY COLUMN `message` VARCHAR(4096) COMPRESS(ZSTD);
```

### 5.3 对象存储压缩

```python
# data_lifecycle/compression.py
import zstandard as zstd
import lz4.frame
import snappy
from typing import Union


class CompressionOptimizer:
    """压缩优化器"""

    COMPRESSORS = {
        'lz4': lz4.frame,
        'zstd': zstd,
        'snappy': snappy
    }

    def compress_data(
        self,
        data: bytes,
        algorithm: str = 'zstd',
        level: int = 3
    ) -> bytes:
        """压缩数据"""
        if algorithm == 'zstd':
            compressor = zstd.ZstdCompressor(level=level)
            return compressor.compress(data)
        elif algorithm == 'lz4':
            return lz4.frame.compress(data)
        elif algorithm == 'snappy':
            return snappy.compress(data)
        else:
            return data

    def decompress_data(self, data: bytes, algorithm: str) -> bytes:
        """解压数据"""
        if algorithm == 'zstd':
            decompressor = zstd.ZstdDecompressor()
            return decompressor.decompress(data)
        elif algorithm == 'lz4':
            return lz4.frame.decompress(data)
        elif algorithm == 'snappy':
            return snappy.uncompress(data)
        else:
            return data

    def estimate_compression_ratio(
        self,
        sample_data: bytes,
        algorithm: str = 'zstd'
    ) -> float:
        """估算压缩比例"""
        compressed = self.compress_data(sample_data, algorithm)
        return len(sample_data) / len(compressed)

    def select_best_algorithm(
        self,
        data_type: str,
        access_pattern: str
    ) -> str:
        """为数据类型选择最优压缩算法"""
        selection_matrix = {
            # 数据类型 -> 访问模式 -> 算法
            'logs': {
                'realtime_write': 'lz4',
                'frequent_read': 'snappy',
                'archive': 'zstd'
            },
            'metrics': {
                'realtime_write': 'snappy',
                'analytics': 'zstd',
                'long_term': 'zstd'
            },
            'ai_analysis': {
                'default': 'zstd'  # JSON 数据压缩效果好
            }
        }

        return selection_matrix.get(data_type, {}).get(
            access_pattern,
            'zstd'  # 默认 ZSTD
        )


# Parquet 文件优化写入
import pyarrow as pa
import pyarrow.parquet as pq


def write_optimized_parquet(
    df,
    output_path: str,
    compression: str = 'zstd',
    row_group_size: int = 100000
):
    """写入优化的 Parquet 文件"""
    table = pa.Table.from_pandas(df)

    pq.write_table(
        table,
        output_path,
        compression=compression,
        row_group_size=row_group_size,
        use_dictionary=True,
        write_statistics=True,
        flavor='spark'  # Spark 兼容
    )
```

---

## 6. 数据治理检查清单

```markdown
## 日常数据治理检查清单

### 每日检查
- [ ] Doris 分区自动创建正常
- [ ] 存储使用率 < 80%
- [ ] 压缩任务运行正常
- [ ] 租户配额未超限

### 每周检查
- [ ] 人工 Compaction 效果
- [ ] 冷热数据比例
- [ ] OSS 归档数据完整性
- [ ] 压缩率统计

### 每月检查
- [ ] 存储成本分析
- [ ] 数据保留策略执行
- [ ] 过期数据清理
- [ ] 备份数据验证

### 每季度检查
- [ ] 数据分级策略评估
- [ ] 归档成本优化
- [ ] 合规审计
- [ ] 灾难恢复演练
```

---

*本文档定义了 AIOps 平台的数据生命周期管理策略，包括分层存储、归档清理、租户隔离和压缩优化，确保数据管理的高效性和经济性。*
