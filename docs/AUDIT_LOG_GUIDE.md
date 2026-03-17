# 审计日志系统使用指南

## 概述

审计日志系统用于记录所有关键操作的审计信息，满足安全合规要求，支持事后审计和追踪。

## 功能特性

### 1. 自动审计
- 基于 AOP 切面自动拦截
- 无侵入式记录操作日志
- 支持敏感操作标记

### 2. 全面记录
- 操作用户信息
- HTTP 请求详情
- 响应状态和时间
- 客户端 IP 和 User-Agent
- 成功/失败状态

### 3. 灵活查询
- 多条件组合查询
- 资源操作历史追踪
- 统计分析功能

## 快速开始

### 1. 使用注解标记需要审计的方法

```java
@PostMapping("/alerts/webhook")
@Auditable(
    operationType = AuditLog.OperationType.CREATE,
    resourceType = "alert",
    description = "接收外部告警",
    sensitive = true
)
public ApiResponse<AlertResponse> receiveAlert(
    @RequestHeader("X-API-Key") String apiKey,
    @RequestBody AlertWebhookRequest request
) {
    // 业务逻辑
}
```

### 2. 注解参数说明

| 参数 | 类型 | 说明 | 默认值 |
|-----|------|------|--------|
| operationType | OperationType | 操作类型（必填） | - |
| resourceType | String | 资源类型 | "" |
| description | String | 操作描述 | "" |
| sensitive | boolean | 是否为敏感操作 | false |
| logParams | boolean | 是否记录请求参数 | true |
| logResult | boolean | 是否记录响应结果 | false |

### 3. 操作类型枚举

```java
public enum OperationType {
    // 认证操作
    LOGIN("用户登录", true),
    LOGOUT("用户登出", false),
    LOGIN_FAILED("登录失败", true),

    // 数据操作
    CREATE("创建", false),
    UPDATE("更新", false),
    DELETE("删除", true),
    READ("查询", false),
    EXPORT("导出", true),

    // 系统操作
    CONFIG_CHANGE("配置变更", true),
    PERMISSION_CHANGE("权限变更", true),
    PASSWORD_CHANGE("密码变更", true),

    // 告警操作
    ALERT_ACKNOWLEDGE("告警确认", false),
    ALERT_CLOSE("告警关闭", false),
    ALERT_ESCALATE("告警升级", false),

    // 事件操作
    INCIDENT_CREATE("事件创建", false),
    INCIDENT_UPDATE("事件更新", false),
    INCIDENT_CLOSE("事件关闭", false)
}
```

## API 使用

### 1. 查询审计日志

```bash
GET /api/v1/audit-logs?tenantId=tenant-1&operationType=LOGIN&page=0&size=20
```

**响应示例：**
```json
{
  "content": [
    {
      "id": 1,
      "tenantId": "tenant-1",
      "userId": "user-1",
      "userName": "admin",
      "operationType": "LOGIN",
      "operationTypeDesc": "用户登录",
      "resourceType": "user",
      "resourceId": "user-1",
      "operationDesc": "用户登录系统",
      "httpMethod": "POST",
      "requestPath": "/api/v1/auth/login",
      "clientIp": "192.168.1.100",
      "responseStatus": 200,
      "responseTimeMs": 125,
      "isSuccess": true,
      "isSensitive": true,
      "createdAt": "2024-03-15T10:30:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### 2. 查询资源操作历史

```bash
GET /api/v1/audit-logs/resource/alert/alert-123
```

返回指定资源的所有操作记录。

### 3. 获取操作统计

```bash
GET /api/v1/audit-logs/statistics?startTime=2024-03-01T00:00:00&endTime=2024-03-15T23:59:59
```

**响应示例：**
```json
{
  "byUser": {
    "user-1": 45,
    "user-2": 32
  },
  "byOperationType": {
    "LOGIN": 15,
    "CREATE": 20,
    "READ": 42
  },
  "recentFailures": 3,
  "recentSensitiveOperations": 12
}
```

## 手动记录审计日志

对于特殊情况，可以手动记录审计日志：

```java
@Autowired
private AuditLogService auditLogService;

public void someBusinessMethod() {
    // 业务逻辑

    // 手动记录审计日志
    auditLogService.recordManualLog(
        "tenant-1",           // 租户ID
        "user-1",             // 用户ID
        "admin",              // 用户名
        OperationType.CONFIG_CHANGE,  // 操作类型
        "system_config",      // 资源类型
        "config-1",           // 资源ID
        "修改系统配置",         // 操作描述
        true                  // 是否敏感
    );
}
```

## 数据库配置

审计日志存储在 MySQL 中，表名为 `audit_logs`。

### 表结构

- `id`: 主键
- `tenant_id`: 租户ID（索引）
- `user_id`: 用户ID（索引）
- `operation_type`: 操作类型（索引）
- `resource_type`: 资源类型（索引）
- `created_at`: 创建时间（索引）

### 数据保留策略

建议定期归档审计日志：

```java
// 清理90天前的日志
auditLogService.cleanOldLogs(LocalDateTime.now().minusDays(90));
```

## 敏感操作处理

对于敏感操作（如删除、权限变更、密码修改），系统会：

1. **自动标记** `is_sensitive = true`
2. **额外日志输出** - 在应用日志中单独记录
3. **优先索引** - 数据库索引优化查询

```java
@Auditable(
    operationType = OperationType.DELETE,
    resourceType = "alert",
    description = "删除告警",
    sensitive = true  // 标记为敏感操作
)
public void deleteAlert(String alertId) {
    // 业务逻辑
}
```

## 最佳实践

### 1. 选择合适的操作类型

```java
// ✅ 好的做法
@Auditable(operationType = OperationType.CREATE, resourceType = "alert")
public Alert createAlert(AlertDTO dto) { }

// ❌ 不推荐
@Auditable(operationType = OperationType.OTHER, resourceType = "alert")
public Alert createAlert(AlertDTO dto) { }
```

### 2. 标记敏感操作

```java
// ✅ 删除操作应标记为敏感
@Auditable(
    operationType = OperationType.DELETE,
    resourceType = "user",
    sensitive = true
)
public void deleteUser(String userId) { }
```

### 3. 提供清晰的描述

```java
// ✅ 好的做法
@Auditable(
    operationType = OperationType.PERMISSION_CHANGE,
    resourceType = "user",
    description = "修改用户角色从GUEST到ADMIN"
)
public void changeUserRole(String userId, Role newRole) { }
```

### 4. 避免记录敏感参数

```java
// ✅ 不记录请求参数（包含密码）
@Auditable(
    operationType = OperationType.PASSWORD_CHANGE,
    resourceType = "user",
    logParams = false,  // 不记录参数
    sensitive = true
)
public void changePassword(String userId, PasswordDTO dto) { }
```

## 监控与告警

### 1. 监控失败操作

```bash
# 查询最近的失败操作
GET /api/v1/audit-logs/failures
```

### 2. 监控敏感操作

```bash
# 查询最近的敏感操作
GET /api/v1/audit-logs/sensitive
```

### 3. 应用日志

敏感操作会输出到应用日志：

```
[INFO] [SENSITIVE OPERATION] User: admin, Operation: DELETE, Resource: alert:alert-123, Time: 45ms
```

## 性能优化

### 1. 异步写入

审计日志采用同步写入保证可靠性，对于高并发场景可以考虑：

- 使用消息队列异步写入
- 批量插入优化

### 2. 索引优化

已创建以下索引：

- `idx_tenant_id` - 租户查询
- `idx_user_id` - 用户查询
- `idx_operation_type` - 操作类型查询
- `idx_created_at` - 时间范围查询
- `idx_resource_type` - 资源类型查询

### 3. 分区表

对于大数据量场景，建议按月分区：

```sql
ALTER TABLE audit_logs PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

## 故障排查

### 1. 审计日志未记录

**检查清单：**
- [ ] 方法是否添加了 `@Auditable` 注解
- [ ] Spring AOP 是否正常工作
- [ ] 数据库连接是否正常
- [ ] 查看应用日志是否有错误

### 2. 性能问题

**优化建议：**
- 减少请求参数记录（`logParams = false`）
- 检查数据库索引是否生效
- 考虑异步写入或批量插入

## 安全建议

1. **访问控制**
   - 审计日志查询需要管理员权限
   - 不同租户数据隔离

2. **数据保护**
   - 定期备份审计日志
   - 敏感参数脱敏或跳过记录

3. **合规要求**
   - 保留周期符合法规要求（通常1-3年）
   - 日志不可篡改

## 示例场景

### 场景1: 用户登录

```java
@PostMapping("/auth/login")
@Auditable(
    operationType = OperationType.LOGIN,
    resourceType = "user",
    description = "用户登录系统",
    sensitive = true
)
public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
    // 登录逻辑
}
```

### 场景2: 告警处理

```java
@PostMapping("/alerts/{alertId}/acknowledge")
@Auditable(
    operationType = OperationType.ALERT_ACKNOWLEDGE,
    resourceType = "alert",
    description = "确认告警"
)
public ApiResponse<Void> acknowledgeAlert(@PathVariable String alertId) {
    // 确认逻辑
}
```

### 场景3: 权限变更

```java
@PutMapping("/users/{userId}/role")
@Auditable(
    operationType = OperationType.PERMISSION_CHANGE,
    resourceType = "user",
    description = "修改用户权限",
    sensitive = true,
    logParams = true
)
public ApiResponse<Void> changeRole(
    @PathVariable String userId,
    @RequestBody RoleChangeRequest request
) {
    // 权限变更逻辑
}
```

---

**更新时间：** 2026-03-17
**相关文档：** [10_Auth_Permission.md](../docs/10_Auth_Permission.md)
