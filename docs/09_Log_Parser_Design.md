# AIOps 智能运维平台 - 多源异构日志采集与标准化设计

## 1. 设计目标

解决异构环境（主机/数据库/K8s/网络/存储）日志格式不统一问题，在采集层实现**标准化输出**，使AI推理层无需关心原始格式。

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         异构日志标准化流程                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   主机日志(syslog)      数据库日志        K8s容器日志      网络设备日志    │
│   ┌──────────────┐   ┌──────────────┐  ┌──────────────┐  ┌───────────┐   │
│   │Mar 14 10:00  │   │2024-03-14    │  │{"log":"..."} │  │<190>Mar   │   │
│   │sshd[123]:    │   │10:00:05      │  │              │  │14 10:00:00│   │
│   │Failed pass   │   │140234 ERROR  │  │              │  │link down  │   │
│   └──────┬───────┘   └──────┬───────┘  └──────┬───────┘  └─────┬─────┘   │
│          │                  │                 │               │         │
│          └──────────────────┼─────────────────┼───────────────┘         │
│                             ▼                 ▼                          │
│                    ┌────────────────────────────────────┐               │
│                    │    iLogtail Parser (分类解析器)     │               │
│                    │  ┌──────────────┐ ┌──────────────┐ │               │
│                    │  │ syslog解析器 │ │ mysql解析器  │ │               │
│                    │  │ k8s解析器    │ │ snmp解析器   │ │               │
│                    │  └──────────────┘ └──────────────┘ │               │
│                    └──────────────┬─────────────────────┘               │
│                                   │                                      │
│                                   ▼                                      │
│                    ┌────────────────────────────────────┐               │
│                    │     统一结构化日志格式 (Standard)    │               │
│                    │  ┌─────────────────────────────┐   │               │
│                    │  │ timestamp | level | service │   │               │
│                    │  │ trace_id | component | msg  │   │               │
│                    │  └─────────────────────────────┘   │               │
│                    └──────────────┬─────────────────────┘               │
│                                   │                                      │
│                                   ▼                                      │
│                           Kafka → Doris Routine Load                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 日志分类与解析策略

### 2.1 日志分类体系

| 分类 | 子类型 | 采集方式 | 关键特征 | 解析器 |
|------|--------|----------|----------|--------|
| **主机/系统** | Linux Syslog | iLogtail file | `/var/log/syslog`, `/var/log/messages` | SyslogParser |
| | dmesg | iLogtail file | `/var/log/dmesg` | DmesgParser |
| | 安全日志 | iLogtail file | `/var/log/secure`, `/var/log/auth.log` | SecureParser |
| **数据库** | MySQL Error | iLogtail file | `/var/log/mysql/error.log` | MySQLParser |
| | MySQL Slow | iLogtail file | `*-slow.log` | MySQLSlowParser |
| | PostgreSQL | iLogtail file | `/var/log/postgresql/*.log` | PGParser |
| | Redis | iLogtail stdout | 容器日志 | RedisParser |
| **K8s/容器** | 容器stdout | iLogtail CRI/容器引擎 | `/var/log/containers/*.log` | K8sParser |
| | K8s Event | K8s API | `kubectl get events` | K8sEventParser |
| | K8s Audit | K8s API | Audit log file | K8sAuditParser |
| **网络设备** | Switch/Router | Syslog Server (514端口) | SNMP Trap/Syslog | SNMPParser |
| | Firewall | Syslog Server | 防火墙syslog | FirewallParser |
| **存储/硬件** | 磁盘SMART | Smartmontools + 脚本 | `smartctl -a` | SMARTParser |
| | RAID卡 | MegaCli/工具输出 | RAID事件日志 | RAIDParser |

---

## 3. 各类日志详细解析规则

### 3.1 主机系统日志 (Syslog)

#### 原始格式示例
```
Mar 14 10:23:15 server-01 sshd[12345]: Failed password for invalid user admin from 192.168.1.100 port 54321 ssh2
Mar 14 10:23:45 server-01 kernel: [12345.678901] Out of memory: Kill process 1234 (java) score 456 or sacrifice child
```

#### iLogtail配置
```yaml
# config/syslog_parser.yaml
inputs:
  - Type: file_log
    LogPath: /var/log
    FilePattern: syslog, messages, secure
    MaxReadSpeed: 10MB

processors:
  - Type: processor_regex
    SourceKey: content
    Regex: '^([A-Za-z]{3}\s+\d{1,2}\s+\d{2}:\d{2}:\d{2})\s+(\S+)\s+(\S+)\[(\d+)\]:\s+(.+)$'
    Keys: ["raw_time", "host", "process", "pid", "message"]
    NoKeyError: true
    NoMatchError: true

  # 时间格式转换
  - Type: processor_strptime
    SourceKey: raw_time
    Format: "%b %d %H:%M:%S"
    DestKey: timestamp

  # 日志级别提取
  - Type: processor_grok
    Match: "%{SYSLOGLEVEL:level}"
    SourceKey: message
    DestKey: level
    # 默认INFO，匹配关键词
    PatternMapping:
      "(?i)error|fail|failed": "ERROR"
      "(?i)warn|warning": "WARN"
      "(?i)debug": "DEBUG"
      "(?i)info|information": "INFO"

  # PII脱敏
  - Type: processor_desensitize
    SourceKey: message
    Method: regex
    Regex: '(\d{1,3}\.){3}\d{1,3}'  # IP脱敏
    ReplaceString: '***.***.***.***'

  # 字段映射到标准格式
  - Type: processor_fields
    Fields:
      service_id: "${host}_system"
      component: "${process}"
      level: "${level}"
      message: "${message}"
      host_ip: "${__host_ip__}"
```

#### 输出到Kafka的JSON格式
```json
{
  "timestamp": "2024-03-14T10:23:15Z",
  "tenant_id": "tenant_001",
  "service_id": "server-01_system",
  "level": "WARN",
  "component": "sshd",
  "pid": "12345",
  "message": "Failed password for invalid user admin from ***.***.***.*** port 54321 ssh2",
  "host_ip": "10.0.1.15",
  "log_type": "syslog",
  "parsed_fields": {
    "event_type": "auth_failure",
    "user": "admin",
    "remote_ip_masked": true
  }
}
```

---

### 3.2 MySQL数据库日志

#### MySQL Error Log 原始格式
```
2024-03-14T10:23:15.123456+08:00 140234 [ERROR] [MY-012345] [InnoDB] Cannot allocate memory for the buffer pool
2024-03-14T10:23:16.234567+08:00 140235 [Warning] [MY-012346] [Server] Aborted connection 12345 to db: 'payment_db' user: 'app_user'
```

#### 解析配置
```yaml
# config/mysql_parser.yaml
inputs:
  - Type: file_log
    LogPath: /var/log/mysql
    FilePattern: error.log, mysql.err

processors:
  - Type: processor_multiline
    Mode: java
    # MySQL多行日志以时间戳开头
    Pattern: '^\d{4}-\d{2}-\d{2}T'

  - Type: processor_regex
    SourceKey: content
    Regex: '^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+\+\d{2}:\d{2})\s+(\d+)\s+\[(\w+)\]\s+\[(\w+-\d+)\]\s+\[(\w+)\]\s+(.+)$'
    Keys: ["raw_time", "thread_id", "level", "error_code", "component", "message"]

  - Type: processor_strptime
    SourceKey: raw_time
    Format: "%Y-%m-%dT%H:%M:%S.%f%z"
    DestKey: timestamp

  - Type: processor_json
    # Error Code映射
    Mapping:
      "MY-012345": {"severity": "critical", "category": "memory"}
      "MY-012346": {"severity": "warning", "category": "connection"}

  # 提取表名/数据库
  - Type: processor_regex_extract
    SourceKey: message
    Regex: "db:\s*['\"](\w+)['\"]"
    DestKey: database_name

  # 提取SQL错误类型
  - Type: processor_keywords
    SourceKey: message
    Keywords:
      - pattern: "Deadlock found"
        dest_field: error_type
        value: "deadlock"
      - pattern: "Table.*doesn't exist"
        dest_field: error_type
        value: "table_not_exist"
      - pattern: "Out of memory"
        dest_field: error_type
        value: "oom"
      - pattern: "Aborted connection"
        dest_field: error_type
        value: "connection_aborted"
```

#### 输出格式
```json
{
  "timestamp": "2024-03-14T02:23:15.123Z",
  "tenant_id": "tenant_001",
  "service_id": "mysql_payment_master",
  "level": "ERROR",
  "component": "InnoDB",
  "thread_id": "140234",
  "error_code": "MY-012345",
  "message": "Cannot allocate memory for the buffer pool",
  "host_ip": "10.0.1.15",
  "log_type": "mysql_error",
  "parsed_fields": {
    "database_name": "payment_db",
    "error_type": "oom",
    "severity": "critical",
    "category": "memory"
  }
}
```

---

### 3.3 MySQL Slow Query Log

#### 原始格式
```sql
# Time: 2024-03-14T10:23:15.123456+08:00
# User@Host: app_user[app_user] @  [10.0.1.100]  Id: 12345
# Query_time: 10.123456  Lock_time: 0.001234 Rows_sent: 100  Rows_examined: 1000000
use order_db;
SET timestamp=1710388995;
SELECT * FROM orders WHERE status = 'pending' AND created_at > '2024-03-01' ORDER BY updated_at LIMIT 100;
```

#### 解析配置
```yaml
# config/mysql_slow_parser.yaml
inputs:
  - Type: file_log
    LogPath: /var/lib/mysql
    FilePattern: '*-slow.log'

processors:
  # 多行模式 - 以# Time: 开头
  - Type: processor_multiline
    Mode: custom
    Pattern: '^# Time:'

  # Grok解析
  - Type: processor_grok
    Pattern: |
      # Time: %{TIMESTAMP_ISO8601:slow_timestamp}
      # User@Host: %{USER:slow_user}\[%{USER}\] @\s+\[%{IP:slow_client_ip}\]\s+Id:\s+%{NUMBER:slow_thread_id:int}
      # Query_time: %{NUMBER:query_time:float}\s+Lock_time: %{NUMBER:lock_time:float}\s+Rows_sent: %{NUMBER:rows_sent:int}\s+Rows_examined: %{NUMBER:rows_examined:int}
      (?:use\s+%{WORD:slow_database};\s+)?
      SET\s+timestamp=%{NUMBER:timestamp_sec:int};
      %{GREEDYDATA:slow_sql}

  # SQL指纹生成（归一化）
  - Type: processor_sql_fingerprint
    SourceKey: slow_sql
    DestKey: sql_fingerprint
    # 将具体值替换为?
    # SELECT * FROM orders WHERE id=123 -> SELECT * FROM orders WHERE id=?

  # SQL类型分类
  - Type: processor_sql_classify
    SourceKey: slow_sql
    DestKey: sql_type
    # SELECT/INSERT/UPDATE/DELETE/ALTER/CREATE

  # 计算扫描效率
  - Type: processor_math
    Expression: "rows_examined / rows_sent"
    DestKey: scan_efficiency
    Condition: "rows_sent > 0"

  # 标记高危慢查询
  - Type: processor_filter
    Condition: "query_time > 10 OR rows_examined > 100000"
    AddFields:
      level: "WARN"
      alert_flag: true
```

#### 输出格式
```json
{
  "timestamp": "2024-03-14T02:23:15.123Z",
  "tenant_id": "tenant_001",
  "service_id": "mysql_order_master",
  "level": "WARN",
  "component": "slow_query",
  "log_type": "mysql_slow",
  "message": "SELECT * FROM orders WHERE status = 'pending' AND created_at > '2024-03-01' ORDER BY updated_at LIMIT 100",
  "parsed_fields": {
    "slow_user": "app_user",
    "slow_client_ip": "10.0.1.100",
    "slow_thread_id": 12345,
    "query_time": 10.123456,
    "lock_time": 0.001234,
    "rows_sent": 100,
    "rows_examined": 1000000,
    "scan_efficiency": 10000.0,
    "slow_database": "order_db",
    "sql_fingerprint": "SELECT * FROM orders WHERE status = ? AND created_at > ? ORDER BY updated_at LIMIT ?",
    "sql_type": "SELECT",
    "alert_flag": true,
    "alert_reason": "query_time > 10"
  }
}
```

---

### 3.4 K8s容器日志

#### 原始格式 (Docker/Containerd CRI)
```json
{"log":"2024-03-14 10:23:15.123 ERROR [payment-service] [trace-id-abc] Connection pool exhausted\n","stream":"stderr","time":"2024-03-14T10:23:15.123456789Z"}
```

#### 解析配置
```yaml
# config/k8s_parser.yaml
inputs:
  - Type: file_log
    LogPath: /var/log/containers
    FilePattern: '*.log'
    # 文件名格式: pod_container_namespace.log
    ExtractMetadataFromFilename:
      - pattern: '^(?<pod_name>[\w-]+)_(?<container_name>[\w-]+)_(?<namespace>[\w-]+)'

processors:
  # 解析外层JSON (CRI格式)
  - Type: processor_json
    SourceKey: content
    ExtractKeys: ["log", "stream"]
    # time字段已经是标准ISO格式

  # 解析内层日志内容
  - Type: processor_regex
    SourceKey: log
    Regex: '^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+(\w+)\s+\[(\w+)\]\s+\[(\w+)\]\s+(.+)$'
    Keys: ["raw_time", "level", "service", "trace_id", "message"]
    NoMatchError: false  # 不强制匹配，非结构化日志也保留

  # 时间格式
  - Type: processor_strptime
    SourceKey: raw_time
    Format: "%Y-%m-%d %H:%M:%S.%f"
    DestKey: timestamp

  # 从Pod名称提取Service ID
  - Type: processor_regex_extract
    SourceKey: __pod_name__
    Regex: '^(?<service_id>[\w-]+)-[a-f0-9]{10}-[a-z0-9]{5}$'
    DestKey: service_id

  # 异常堆栈合并
  - Type: processor_multiline
    Mode: java
    Pattern: '^\d{4}-\d{2}-\d{2}'
    MaxLines: 50
    Negate: false
```

#### 输出格式
```json
{
  "timestamp": "2024-03-14T10:23:15.123Z",
  "tenant_id": "tenant_001",
  "service_id": "payment-service",
  "level": "ERROR",
  "component": "payment-service",
  "trace_id": "trace-id-abc",
  "message": "Connection pool exhausted",
  "host_ip": "10.0.1.20",
  "pod_name": "payment-service-5d4f7b9c2-x2z9a",
  "container_name": "payment-service",
  "namespace": "production",
  "log_type": "k8s_container",
  "stream": "stderr",
  "parsed_fields": {
    "is_stacktrace": false
  }
}
```

---

### 3.5 网络设备日志 (Syslog)

#### 原始格式
```
<190>Mar 14 2024 10:23:15 switch-core-01 %%01IFNET/4/LINKSTATUS(l)[12345]:Interface 10GE1/0/1 turned into DOWN state.
<189>Mar 14 2024 10:23:45 firewall-01 %%01SEC/6/SESSION(s)[67890]:Session created. Protocol: TCP, Source: 192.168.1.100:12345, Destination: 10.0.0.50:443
```

#### 解析配置
```yaml
# config/network_parser.yaml
inputs:
  - Type: syslog
    Address: 0.0.0.0:514
    Protocol: udp
    Format: rfc3164  # or rfc5424

processors:
  # 解析Syslog PRI
  - Type: processor_syslog_pri
    SourceKey: content
    DestFacilityKey: facility
    DestSeverityKey: severity

  # 解析消息体
  - Type: processor_regex
    SourceKey: content
    Regex: '^(\w{3}\s+\d{1,2}\s+\d{4}\s+\d{2}:\d{2}:\d{2})\s+(\S+)\s+(.+)$'
    Keys: ["raw_time", "device_name", "message"]

  # 华为/思科设备特定格式解析
  - Type: processor_grok
    SourceKey: message
    Patterns:
      # 接口状态变化
      - '%%\d+(?<vendor>IFNET)/(?<level_num>\d+)/(?<event_type>LINKSTATUS)(?<log_type>\(\w\))?\[(?<seq>\d+)\]:\s*(?<description>.+)'
      # 安全会话
      - '%%\d+(?<vendor>SEC)/(?<level_num>\d+)/(?<event_type>SESSION)(?<log_type>\(\w\))?\[(?<seq>\d+)\]:\s*(?<description>.+)'
      # 通用事件
      - '%%\d+(?<vendor>\w+)/(?<level_num>\d+)/(?<event_type>\w+)(?<log_type>\(\w\))?\[(?<seq>\d+)\]:\s*(?<description>.+)'

  # 提取接口名称
  - Type: processor_regex_extract
    SourceKey: message
    Regex: 'Interface\s+(\S+)'
    DestKey: interface_name

  # 提取协议和IP
  - Type: processor_regex_extract_all
    SourceKey: message
    Regexes:
      - pattern: 'Protocol:\s+(\w+)'
        dest: protocol
      - pattern: 'Source:\s+(\S+)'
        dest: source_addr
      - pattern: 'Destination:\s+(\S+)'
        dest: dest_addr

  # 事件严重性映射
  - Type: processor_map
    SourceKey: severity
    Mapping:
      "0": "EMERGENCY"
      "1": "ALERT"
      "2": "CRITICAL"
      "3": "ERROR"
      "4": "WARNING"
      "5": "NOTICE"
      "6": "INFO"
      "7": "DEBUG"
    DestKey: level
```

#### 输出格式
```json
{
  "timestamp": "2024-03-14T10:23:15Z",
  "tenant_id": "tenant_001",
  "service_id": "switch-core-01_network",
  "level": "WARNING",
  "component": "IFNET",
  "facility": "local7",
  "severity": 4,
  "message": "Interface 10GE1/0/1 turned into DOWN state.",
  "host_ip": "10.0.1.30",
  "log_type": "network_syslog",
  "parsed_fields": {
    "device_name": "switch-core-01",
    "vendor": "IFNET",
    "event_type": "LINKSTATUS",
    "interface_name": "10GE1/0/1",
    "event_direction": "DOWN"
  }
}
```

---

### 3.6 磁盘/硬件SMART日志

#### 原始格式
```
smartctl 7.2 2020-12-30 r5155 [x86_64-linux-5.4.0] (local build)
Copyright (C) 2002-20, Bruce Allen, Christian Franke, www.smartmontools.org

=== START OF INFORMATION SECTION ===
Model Family: Samsung based SSDs
Device Model: Samsung SSD 870 EVO 1TB
Serial Number: S6PENL0T123456
LU WWN Device Id: 5 002538 e01456789
Firmware Version: SVT01B6Q
User Capacity: 1,000,204,886,016 bytes [1.00 TB]
Sector Size: 512 bytes logical/physical

=== START OF SMART DATA SECTION ===
SMART overall-health self-assessment test result: FAILED!
  - with prefail/error attributes

ID# ATTRIBUTE_NAME          FLAG     VALUE WORST THRESH TYPE      UPDATED  WHEN_FAILED RAW_VALUE
  5 Reallocated_Sector_Ct   0x0033   100   100   010    Pre-fail  Always   -       0
196 Reallocated_Event_Count 0x0032   100   100   000    Old_age   Always   -       5
197 Current_Pending_Sector  0x0032   100   100   000    Old_age   Always   -       12
198 Offline_Uncorrectable   0x0030   100   100   000    Old_age   Offline  FAILING_NOW 25
```

#### 采集与解析配置
```yaml
# config/smart_parser.yaml
inputs:
  # SMART数据通过定时任务采集，非实时文件
  - Type: service_docker_stdout
    LogPath: /var/log/smartmon
    # 启动一个sidecar容器定期执行smartctl并输出到stdout

processors:
  - Type: processor_multiline
    Mode: custom
    Pattern: '^smartctl|^(ID#|===)'

  # 提取关键指标行
  - Type: processor_regex_extract_table
    SourceKey: content
    TablePattern: '^(\d+)\s+(\w+)\s+0x[0-9a-f]+\s+(\d+)\s+(\d+)\s+(\d+)\s+(\w+)\s+(\w+)\s+(\w+)?\s+(\d+)$'
    Columns: ["id", "name", "value", "worst", "thresh", "type", "updated", "when_failed", "raw_value"]

  # 提取设备信息
  - Type: processor_regex_extract
    SourceKey: content
    Regex: 'Device Model:\s+(.+)'
    DestKey: device_model

  # 提取健康状态
  - Type: processor_regex_extract
    SourceKey: content
    Regex: 'SMART overall-health.*result:\s+(\w+)'
    DestKey: health_status

  # 生成告警字段
  - Type: processor_filter
    Condition: "when_failed != '-' OR health_status == 'FAILED!'"
    AddFields:
      level: "ERROR"
      alert_type: "hardware_degradation"

  # 提取磁盘序列号
  - Type: processor_regex_extract
    SourceKey: content
    Regex: 'Serial Number:\s+(\S+)'
    DestKey: disk_serial
```

#### 输出格式
```json
{
  "timestamp": "2024-03-14T10:23:15Z",
  "tenant_id": "tenant_001",
  "service_id": "server-01_hw",
  "level": "ERROR",
  "component": "smartd",
  "message": "SMART health check failed for Samsung SSD 870 EVO 1TB (S6PENL0T123456)",
  "host_ip": "10.0.1.15",
  "log_type": "hardware_smart",
  "parsed_fields": {
    "device_model": "Samsung SSD 870 EVO 1TB",
    "disk_serial": "S6PENL0T123456",
    "health_status": "FAILED!",
    "alert_type": "hardware_degradation",
    "failing_attributes": [
      {"name": "Offline_Uncorrectable", "value": 25, "threshold": "FAILING_NOW"}
    ],
    "pending_sectors": 12,
    "reallocated_events": 5
  }
}
```

---

## 4. 标准化输出统一Schema

无论原始日志来源如何，最终输出到Kafka的JSON必须符合以下统一Schema:

```json
{
  "timestamp": "ISO8601格式，UTC时区",
  "tenant_id": "租户ID",
  "service_id": "标准化服务ID",
  "level": "ERROR|WARN|INFO|DEBUG",

  "component": "组件名(如mysql/sshd/dockerd)",
  "trace_id": "可选",
  "span_id": "可选",

  "host_ip": "采集主机IP",
  "pod_name": "K8s Pod名(如有)",
  "container_name": "容器名(如有)",

  "message": "日志正文",
  "raw_message": "可选，原始日志",
  "log_type": "syslog|mysql_error|mysql_slow|k8s_container|network_syslog|hardware_smart",

  "parsed_fields": {
    "**该类型日志特有的结构化字段**",
    "**务必包含AI分析可能用到的关键信息**"
  },

  "alert_flag": "boolean，是否触发告警",
  "alert_reason": "触发告警的原因"
}
```

---

## 5. 配置管理方案

### 5.1 配置热更新机制

```yaml
# 使用ConfigMap管理解析规则
apiVersion: v1
kind: ConfigMap
metadata:
  name: aiops-log-parser-config
  namespace: aiops
data:
  syslog_parser.yaml: |
    # 内容...
  mysql_parser.yaml: |
    # 内容...
  k8s_parser.yaml: |
    # 内容...

---
# iLogtail挂载ConfigMap
volumeMounts:
  - name: parser-config
    mountPath: /etc/ilogtail/conf/parser
volumes:
  - name: parser-config
    configMap:
      name: aiops-log-parser-config
```

### 5.2 动态发现与分发

```python
# 自动发现新日志类型并应用对应解析器
class LogTypeClassifier:
    """根据日志内容自动分类"""

    CLASSIFIERS = [
        {
            "type": "mysql_error",
            "keywords": ["InnoDB", "MySQL", "[ERROR]", "[Warning]"],
            "filename_pattern": r"mysql.*error\.log"
        },
        {
            "type": "syslog",
            "keywords": ["systemd", "sshd", "crontab"],
            "filename_pattern": r"syslog|messages"
        },
        {
            "type": "k8s_container",
            "keywords": [],
            "filename_pattern": r"/var/log/containers/.*\.log"
        }
    ]

    def classify(self, filename: str, sample_lines: List[str]) -> str:
        # 先匹配文件名
        for classifier in self.CLASSIFIERS:
            if re.match(classifier["filename_pattern"], filename):
                return classifier["type"]

        # 再匹配内容关键词
        content = " ".join(sample_lines)
        for classifier in self.CLASSIFIERS:
            score = sum(1 for kw in classifier["keywords"] if kw in content)
            if score >= 2:  # 匹配2个以上关键词
                return classifier["type"]

        return "unknown"
```

---

*本文档定义了多源异构日志的采集与标准化机制，确保AI引擎接收到格式统一、信息完整的结构化日志。*
