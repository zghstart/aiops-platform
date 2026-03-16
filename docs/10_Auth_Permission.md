# AIOps 智能运维平台 - 用户认证与权限管理设计

## 1. 核心概念

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     认证授权架构 (Authentication & Authorization)        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                         多层安全体系                              │   │
│  │                                                                  │   │
│  │   ┌─────────────┐     ┌─────────────┐     ┌─────────────────┐   │   │
│  │   │ 认证层      │     │ 权限层      │     │ 审计层          │   │   │
│  │   │ (你是谁)    │ ──► │ (你能做什么)│ ──► │ (你做了什么)    │   │   │
│  │   ├─────────────┤     ├─────────────┤     ├─────────────────┤   │   │
│  │   │ • 登录      │     │ • RBAC      │     │ • 操作日志      │   │   │
│  │   │ • 注册      │     │ • API鉴权   │     │ • 变更追踪      │   │   │
│  │   │ • Token刷新 │     │ • 数据隔离  │     │ • 安全告警      │   │   │
│  │   │ • MFA       │     │ • 字段脱敏  │     │ • 合规审计      │   │   │
│  │   └─────────────┘     └─────────────┘     └─────────────────┘   │   │
│  │                                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  权限模型: 租户(Tenant) > 角色(Role) > 权限(Permission) > 资源(Resource) │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 数据库设计

### 2.1 用户表 (users)

```sql
CREATE TABLE IF NOT EXISTS users (
    -- 基本信息
    id VARCHAR(32) PRIMARY KEY COMMENT '用户ID (UUID)',
    tenant_id VARCHAR(32) NOT NULL COMMENT '所属租户',
    username VARCHAR(64) NOT NULL COMMENT '用户名，租户内唯一',
    email VARCHAR(128) NOT NULL COMMENT '邮箱',
    phone VARCHAR(32) COMMENT '手机号',

    -- 安全凭证
    password_hash VARCHAR(256) NOT NULL COMMENT 'BCrypt哈希密码',
    salt VARCHAR(64) COMMENT '附加盐值',

    -- 用户状态
    status TINYINT DEFAULT 1 COMMENT '0:禁用 1:正常 2:待激活 3:锁定',
    user_type VARCHAR(16) DEFAULT 'standard' COMMENT 'admin/standard/readonly/api',

    -- MFA配置
    mfa_enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用双因素认证',
    mfa_type VARCHAR(16) COMMENT 'totp/sms/email',
    mfa_secret VARCHAR(256) COMMENT 'MFA密钥(加密存储)',

    -- 登录控制
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(45) COMMENT '最后登录IP',
    login_fail_count INT DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until TIMESTAMP NULL COMMENT '锁定截止时间',
    password_changed_at TIMESTAMP NOT NULL COMMENT '密码最后修改时间',
    password_expiry_days INT DEFAULT 90 COMMENT '密码有效期(天)',

    -- 安全设置
    require_password_change BOOLEAN DEFAULT FALSE COMMENT '下次登录需修改密码',
    api_key_hash VARCHAR(256) COMMENT 'API Key哈希(供系统调用)',
    api_key_expiry TIMESTAMP NULL COMMENT 'API Key过期时间',

    -- 个人资料
    display_name VARCHAR(128) COMMENT '显示名',
    avatar_url VARCHAR(512) COMMENT '头像URL',
    department VARCHAR(64) COMMENT '部门',
    employee_id VARCHAR(32) COMMENT '工号',

    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(32) COMMENT '创建人',
    updated_by VARCHAR(32) COMMENT '最后修改人',

    -- 索引
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    UNIQUE KEY uk_email (email),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status),
    INDEX idx_api_key (api_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户状态枚举说明
-- 0: DISABLED - 被管理员禁用
-- 1: ACTIVE - 正常可用
-- 2: PENDING - 待激活（刚注册未验证邮箱）
-- 3: LOCKED - 登录失败次数过多被锁定
```

### 2.2 角色表 (roles)

```sql
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(32) PRIMARY KEY COMMENT '角色ID',
    tenant_id VARCHAR(32) NOT NULL COMMENT '所属租户',

    -- 角色定义
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码，如platform_admin',
    role_name VARCHAR(128) NOT NULL COMMENT '角色显示名',
    description TEXT COMMENT '角色描述',

    -- 角色类型
    role_type ENUM('system', 'custom', 'predefined') DEFAULT 'custom' COMMENT '角色类型',
    -- system: 系统内置角色，不可删除
    -- predefined: 预设角色(如租户管理员)，可编辑权限
    -- custom: 用户自定义角色

    -- 角色范围
    scope_level TINYINT DEFAULT 1 COMMENT '权限范围 1:租户内 2:全平台',

    -- 继承关系 (支持角色继承)
    parent_role_id VARCHAR(32) COMMENT '父角色ID，继承其权限',

    -- 状态
    is_active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否为新用户默认角色',

    -- 审计
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_tenant_code (tenant_id, role_code),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (role_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 系统内置角色种子数据
INSERT INTO roles (id, tenant_id, role_code, role_name, role_type, scope_level, description, is_active, is_default) VALUES
-- 系统超级管理员（scope_level=2表示全平台）
('role_sys_admin', 'system', 'platform_admin', '平台超级管理员', 'system', 2, '拥有平台所有权限', TRUE, FALSE),

-- 租户级别角色
('role_tenant_admin', 'system', 'tenant_admin', '租户管理员', 'predefined', 1, '租户内最高权限，可管理用户和配置', TRUE, FALSE),
('role_ops_manager', 'system', 'ops_manager', '运维经理', 'predefined', 1, '可管理告警规则，查看所有数据', TRUE, FALSE),
('role_ops_engineer', 'system', 'ops_engineer', '运维工程师', 'predefined', 1, '处理告警，查看监控数据', TRUE, TRUE),
('role_viewer', 'system', 'viewer', '只读用户', 'predefined', 1, '仅能查看数据，无法操作', TRUE, FALSE),
('role_ai_operator', 'system', 'ai_operator', 'AI操作员', 'predefined', 1, '可操作AI诊断，查看分析结果', TRUE, FALSE);
```

### 2.3 权限定义表 (permissions)

```sql
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(64) PRIMARY KEY COMMENT '权限ID，如alert:view',

    -- 权限定义
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(256) NOT NULL COMMENT '权限名称',
    description TEXT COMMENT '权限描述',

    -- 资源与操作 (RESTful风格)
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型: alert/incident/service/user/config',
    action VARCHAR(32) NOT NULL COMMENT '操作: view/create/update/delete/execute/admin',

    -- 权限分组
    module VARCHAR(64) NOT NULL COMMENT '所属模块: core/alert/ai/dashboard/system',

    -- 数据范围限制
    data_scope_define VARCHAR(256) COMMENT '数据范围定义JSON: {"type":"own|team|tenant|all"}',

    -- 敏感操作标记
    is_sensitive BOOLEAN DEFAULT FALSE COMMENT '是否敏感操作(需二次确认)',
    requires_reason BOOLEAN DEFAULT FALSE COMMENT '操作是否需要填写原因',
    requires_approval BOOLEAN DEFAULT FALSE COMMENT '是否需要审批',

    -- 前置条件
    prerequisites TEXT COMMENT '前置权限要求JSON',

    -- API端点关联 (用于自动权限检查)
    api_endpoints TEXT COMMENT '关联的API端点列表JSON',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_code (permission_code),
    INDEX idx_resource (resource_type),
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限定义表';

-- 种子数据：核心权限
INSERT INTO permissions (id, permission_code, permission_name, resource_type, action, module, is_sensitive) VALUES
-- 告警管理
('perm_alert_view', 'alert:view', '查看告警', 'alert', 'view', 'alert', FALSE),
('perm_alert_manage', 'alert:manage', '管理告警(确认/抑制/关闭)', 'alert', 'update', 'alert', FALSE),
('perm_alert_config', 'alert:config', '配置告警规则', 'alert', 'admin', 'alert', TRUE),
('perm_noise_rule_manage', 'alert:noise_manage', '管理降噪规则', 'alert', 'admin', 'alert', TRUE),

-- AI诊断
('perm_ai_view', 'ai:view', '查看AI诊断结果', 'ai', 'view', 'ai', FALSE),
('perm_ai_execute', 'ai:execute', '执行AI诊断', 'ai', 'execute', 'ai', FALSE),
('perm_ai_feedback', 'ai:feedback', '提交AI反馈', 'ai', 'update', 'ai', FALSE),
('perm_ai_config', 'ai:config', '配置AI引擎', 'ai', 'admin', 'ai', TRUE),
('perm_ai_model_manage', 'ai:model_manage', '管理模型和Prompt', 'ai', 'admin', 'ai', TRUE),

-- 故障处理
('perm_incident_view', 'incident:view', '查看故障', 'incident', 'view', 'core', FALSE),
('perm_incident_create', 'incident:create', '创建故障工单', 'incident', 'create', 'core', FALSE),
('perm_incident_update', 'incident:update', '更新故障状态', 'incident', 'update', 'core', FALSE),
('perm_incident_resolve', 'incident:resolve', '解决故障', 'incident', 'execute', 'core', TRUE),

-- 服务与CMDB
('perm_service_view', 'service:view', '查看服务', 'service', 'view', 'core', FALSE),
('perm_service_manage', 'service:manage', '管理服务', 'service', 'admin', 'core', TRUE),
('perm_cmdb_view', 'cmdb:view', '查看CMDB', 'cmdb', 'view', 'core', FALSE),
('perm_cmdb_manage', 'cmdb:manage', '管理CMDB', 'cmdb', 'admin', 'core', TRUE),

-- 大屏
('perm_dashboard_view', 'dashboard:view', '查看大屏', 'dashboard', 'view', 'dashboard', FALSE),
('perm_dashboard_manage', 'dashboard:manage', '配置大屏', 'dashboard', 'admin', 'dashboard', FALSE),

-- 知识库
('perm_kb_view', 'kb:view', '查看知识库', 'kb', 'view', 'core', FALSE),
('perm_kb_edit', 'kb:edit', '编辑知识库', 'kb', 'update', 'core', FALSE),

-- 系统管理 (仅管理员)
('perm_user_view', 'user:view', '查看用户', 'user', 'view', 'system', FALSE),
('perm_user_manage', 'user:manage', '管理用户', 'user', 'admin', 'system', TRUE),
('perm_role_manage', 'role:manage', '管理角色权限', 'role', 'admin', 'system', TRUE),
('perm_tenant_config', 'tenant:config', '租户配置', 'tenant', 'admin', 'system', TRUE),
('perm_cost_view', 'cost:view', '查看成本', 'cost', 'view', 'system', FALSE),
('perm_audit_view', 'audit:view', '查看审计日志', 'audit', 'view', 'system', TRUE),
('perm_system_config', 'system:config', '系统配置', 'system', 'admin', 'system', TRUE);
```

### 2.4 角色-权限关联表 (role_permissions)

```sql
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id VARCHAR(32) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,

    -- 该角色下的特殊限制
    data_scope VARCHAR(32) DEFAULT 'tenant' COMMENT '数据范围: own/team/tenant/all',
    -- own: 只能看自己的数据
    -- team: 能看同团队数据
    -- tenant: 能看租户内所有数据
    -- all: 跨租户(平台管理员)

    -- 该角色下的额外条件
    conditions TEXT COMMENT '额外条件JSON，如{"dept_only":true}',

    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(32) COMMENT '授权人',

    UNIQUE KEY uk_role_perm (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- 预置角色权限
-- 租户管理员: 拥有所有权限
INSERT INTO role_permissions (role_id, permission_id, data_scope)
SELECT 'role_tenant_admin', id, 'tenant' FROM permissions;

-- 运维经理: 去掉用户管理/角色管理/系统配置等敏感权限
INSERT INTO role_permissions (role_id, permission_id, data_scope)
SELECT 'role_ops_manager', id, 'tenant' FROM permissions
WHERE permission_code NOT IN ('user:manage', 'role:manage', 'system:config', 'tenant:config', 'cost:view', 'audit:view');

-- 运维工程师
INSERT INTO role_permissions (role_id, permission_id, data_scope)
VALUES
('role_ops_engineer', 'perm_alert_view', 'tenant'),
('role_ops_engineer', 'perm_alert_manage', 'tenant'),
('role_ops_engineer', 'perm_ai_view', 'tenant'),
('role_ops_engineer', 'perm_ai_execute', 'tenant'),
('role_ops_engineer', 'perm_ai_feedback', 'own'),
('role_ops_engineer', 'perm_incident_view', 'tenant'),
('role_ops_engineer', 'perm_incident_update', 'own'),
('role_ops_engineer', 'perm_incident_resolve', 'own'),
('role_ops_engineer', 'perm_service_view', 'tenant'),
('role_ops_engineer', 'perm_cmdb_view', 'tenant'),
('role_ops_engineer', 'perm_dashboard_view', 'tenant'),
('role_ops_engineer', 'perm_kb_view', 'tenant'),
('role_ops_engineer', 'perm_kb_edit', 'own');

-- 只读用户
INSERT INTO role_permissions (role_id, permission_id, data_scope)
VALUES
('role_viewer', 'perm_alert_view', 'tenant'),
('role_viewer', 'perm_ai_view', 'tenant'),
('role_viewer', 'perm_incident_view', 'tenant'),
('role_viewer', 'perm_service_view', 'tenant'),
('role_viewer', 'perm_cmdb_view', 'tenant'),
('role_viewer', 'perm_dashboard_view', 'tenant'),
('role_viewer', 'perm_kb_view', 'tenant');
```

### 2.5 用户-角色关联表 (user_roles)

```sql
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,

    -- 角色有效期限
    valid_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP NULL COMMENT 'NULL表示永久有效',

    -- 授予信息
    granted_by VARCHAR(32) COMMENT '授权人',
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 审批信息(如需)
    approval_id VARCHAR(32) COMMENT '关联的审批单ID',

    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id),
    INDEX idx_valid (valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';
```

### 2.6 登录会话表 (user_sessions)

```sql
CREATE TABLE IF NOT EXISTS user_sessions (
    id VARCHAR(64) PRIMARY KEY COMMENT 'Session ID',
    user_id VARCHAR(32) NOT NULL,
    tenant_id VARCHAR(32) NOT NULL,

    -- Token信息
    access_token_hash VARCHAR(256) NOT NULL COMMENT 'Access Token哈希',
    refresh_token_hash VARCHAR(256) COMMENT 'Refresh Token哈希',

    -- 会话状态
    status ENUM('active', 'expired', 'revoked', 'suspended') DEFAULT 'active',

    -- 客户端信息
    client_type VARCHAR(32) COMMENT 'web/dashboard/api',
    client_ip VARCHAR(45),
    client_ua TEXT,
    client_device_id VARCHAR(64) COMMENT '设备指纹',

    -- 会话时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    -- 安全标记
    is_mfa_verified BOOLEAN DEFAULT FALSE COMMENT '是否已完成MFA验证',
    bypass_mfa BOOLEAN DEFAULT FALSE COMMENT '是否跳过MFA(可信设备)',

    INDEX idx_user (user_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_token (access_token_hash),
    INDEX idx_expires (expires_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话';
```

### 2.7 操作审计表 (audit_logs)

```sql
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- 操作人
    user_id VARCHAR(32),
    tenant_id VARCHAR(32) NOT NULL,
    username VARCHAR(64),

    -- 操作定义
    operation_type VARCHAR(64) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/EXECUTE/LOGIN/LOGOUT',
    resource_type VARCHAR(64) NOT NULL COMMENT '操作的资源类型',
    resource_id VARCHAR(64) COMMENT '资源ID',
    action_description VARCHAR(512) COMMENT '操作描述',

    -- 请求详情
    request_method VARCHAR(16) COMMENT 'HTTP方法',
    request_path VARCHAR(1024) COMMENT '请求路径',
    request_params TEXT COMMENT '请求参数(JSON)',
    request_body TEXT COMMENT '请求体(敏感信息脱敏后)',

    -- 响应详情
    response_status INT COMMENT 'HTTP状态码',
    response_body TEXT COMMENT '响应摘要',
    error_message TEXT COMMENT '错误信息',

    -- 执行结果
    success BOOLEAN NOT NULL,
    execution_time_ms INT COMMENT '执行耗时',

    -- 客户端信息
    client_ip VARCHAR(45),
    client_ua TEXT,
    session_id VARCHAR(64),
    trace_id VARCHAR(32) COMMENT '分布式追踪ID',

    -- 变更前后数据(用于敏感操作审计)
    change_before TEXT COMMENT '变更前数据',
    change_after TEXT COMMENT '变更后数据',

    -- 审计结论
    risk_level TINYINT DEFAULT 1 COMMENT '1:低 2:中 3:高',
    requires_followup BOOLEAN DEFAULT FALSE COMMENT '是否需要后续跟进',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user (user_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_time (created_at),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_operation (operation_type),
    INDEX idx_risk (risk_level),
    INDEX idx_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (UNIX_TIMESTAMP(created_at)) (
    PARTITION p_current VALUES LESS THAN (UNIX_TIMESTAMP('2024-05-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
) COMMENT='操作审计日志';
```

---

## 3. API设计

### 3.1 认证接口

#### 用户注册
```http
POST /api/v1/auth/register

# Request
{
  "tenant_code": "demo_tenant",  // 如果是平台级注册可省略
  "username": "zhangsan",
  "email": "zhangsan@company.com",
  "phone": "13800138000",
  "password": "SecurePass123!",  // 需满足复杂度要求
  "display_name": "张三",
  "department": "运维部",
  "invitation_code": "INV202403" // 可选：邀请码
}

# Response
{
  "code": 200,
  "message": "注册成功，请激活邮箱",
  "data": {
    "user_id": "usr_abc123",
    "status": "pending_activation",
    "email_sent": true
  }
}

# Error Cases
400 - 用户名已存在
400 - 邮箱已被注册
400 - 密码强度不足（需包含大小写+数字+特殊字符）
400 - 邀请码无效或已过期
```

#### 用户登录
```http
POST /api/v1/auth/login

# Request
{
  "tenant_code": "demo_tenant",
  "username": "zhangsan",  // 或邮箱
  "password": "SecurePass123!",
  "mfa_code": "123456",     // 如启用MFA
  "client_type": "web",     // web/dashboard/api
  "device_id": "uuid...",   // 设备指纹
  "remember_me": false
}

# Response (成功)
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "user": {
      "id": "usr_abc123",
      "username": "zhangsan",
      "display_name": "张三",
      "tenant_id": "ten_xyz",
      "roles": [
        {"id": "role_ops_engineer", "name": "运维工程师"}
      ],
      "permissions": ["alert:view", "alert:manage", "ai:execute"]  // 权限列表
    },
    "tokens": {
      "access_token": "eyJhbGciOiJIUzI1NiIs...",
      "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
      "expires_in": 7200,  // 2小时
      "token_type": "Bearer"
    },
    "session": {
      "id": "sess_abc123",
      "mfa_required": false,
      "password_expiry_days": 15  // 密码即将过期提醒
    }
  }
}

# Response (需要MFA)
{
  "code": 401,
  "message": "需要双因素认证",
  "data": {
    "mfa_required": true,
    "mfa_type": "totp",  // totp/sms/email
    "temp_token": "temp_xyz"  // 临时token用于完成MFA
  }
}

# Response (密码过期)
{
  "code": 403,
  "message": "密码已过期，请修改密码",
  "data": {
    "require_password_change": true,
    "temp_token": "temp_xyz"
  }
}

# Response (账号锁定)
{
  "code": 403,
  "message": "账号已锁定",
  "data": {
    "locked": true,
    "unlock_after": "2024-03-14T12:00:00Z",  // 或永久锁定
    "contact_admin": true
  }
}
```

#### Token刷新
```http
POST /api/v1/auth/refresh
Authorization: Bearer {expired_access_token}

# Request
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIs..."
}

# Response
{
  "code": 200,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 7200
  }
}
```

#### 登出
```http
POST /api/v1/auth/logout
Authorization: Bearer {token}

# Response
{
  "code": 200,
  "message": "登出成功"
}
```

### 3.2 用户管理接口 (管理员)

```http
# 创建用户 POST /api/v1/users
# 查询用户 GET /api/v1/users?page=1&status=active
# 获取用户详情 GET /api/v1/users/{userId}
# 更新用户 PUT /api/v1/users/{userId}
# 禁用用户 POST /api/v1/users/{userId}/disable
# 重置密码 POST /api/v1/users/{userId}/reset-password
# 分配角色 POST /api/v1/users/{userId}/roles

# 示例：创建用户
POST /api/v1/users
Authorization: Bearer {admin_token}

{
  "username": "lisi",
  "email": "lisi@company.com",
  "phone": "13900139000",
  "initial_password": "TempPass123!",  // 首次登录需修改
  "display_name": "李四",
  "department": "开发部",
  "role_ids": ["role_viewer"],
  "force_password_change": true
}
```

### 3.3 角色权限管理接口

```http
# 获取角色列表 GET /api/v1/roles
# 创建角色 POST /api/v1/roles
# 更新角色 PUT /api/v1/roles/{roleId}
# 删除角色 DELETE /api/v1/roles/{roleId}
# 获取权限树 GET /api/v1/permissions
# 分配权限 POST /api/v1/roles/{roleId}/permissions

# 示例：授权
POST /api/v1/roles/role_ops_engineer/permissions
{
  "permission_ids": ["perm_alert_manage", "perm_ai_execute"],
  "data_scope": "team"  // 限制为团队级别
}
```

---

## 4. 权限检查机制

### 4.1 Java端权限注解

```java
// 权限检查注解
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();  // 权限编码
    String dataScope() default "";  // 数据范围覆盖
}

// 敏感操作注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveOperation {
    String value();  // 操作描述
    boolean requireReason() default false;
    boolean requireApproval() default false;
}

// 使用示例
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    @GetMapping
    @RequirePermission("alert:view")
    public List<AlertVO> listAlerts(@TenantId String tenantId) {
        // 自动检查用户是否有alert:view权限
        // 自动按租户ID过滤数据
    }

    @PostMapping("/{id}/suppress")
    @RequirePermission("alert:manage")
    @SensitiveOperation(value = "抑制告警", requireReason = true)
    public void suppressAlert(
            @PathVariable String id,
            @RequestBody @Valid SuppressRequest request,
            @CurrentUser UserContext user) {
        // 1. 检查alert:manage权限
        // 2. 记录操作原因
        // 3. 写入审计日志
        // 4. 执行抑制
    }

    @PostMapping("/rules")
    @RequirePermission("alert:config")
    @SensitiveOperation(value = "创建告警规则", requireApproval = true)
    public AlertRule createRule(@RequestBody AlertRuleRequest request) {
        // 需要审批后才生效
    }
}
```

### 4.2 权限检查AOP切面

```java
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuditLogService auditLogService;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint point, RequirePermission requirePermission) throws Throwable {
        // 获取当前用户
        UserContext user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("未登录");
        }

        String permission = requirePermission.value();

        // 检查权限
        boolean hasPermission = permissionService.checkPermission(
            user.getId(),
            user.getTenantId(),
            permission
        );

        if (!hasPermission) {
            // 记录越权尝试
            auditLogService.logUnauthorizedAccess(user, permission, getRequestInfo());
            throw new ForbiddenException("权限不足: " + permission);
        }

        // 检查数据范围
        String requiredDataScope = requirePermission.dataScope();
        if (!StringUtils.isEmpty(requiredDataScope)) {
            // 验证用户的数据范围是否满足要求
            if (!permissionService.checkDataScope(user.getId(), requiredDataScope)) {
                throw new ForbiddenException("数据范围不足");
            }
        }

        // 传递数据范围上下文到Service层
        DataScopeContext.set(user.getDataScope());

        try {
            return point.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    @Around("@annotation(sensitiveOp)")
    public Object handleSensitiveOperation(ProceedingJoinPoint point, SensitiveOperation sensitiveOp) throws Throwable {
        // 敏感操作额外处理
        // 1. 检查是否需要审批
        // 2. 记录详细审计日志
        // 3. 可能需要发送通知

        AuditContext ctx = new AuditContext();
        ctx.setOperation(sensitiveOp.value());
        ctx.setStartTime(System.currentTimeMillis());

        try {
            Object result = point.proceed();
            ctx.setSuccess(true);
            return result;
        } catch (Exception e) {
            ctx.setSuccess(false);
            ctx.setError(e.getMessage());
            throw e;
        } finally {
            ctx.setEndTime(System.currentTimeMillis());
            auditLogService.logSensitiveOperation(ctx);
        }
    }
}
```

---

## 5. 多因素认证(MFA)设计

### 5.1 TOTP (Google Authenticator)

```java
// MFA服务
@Service
public class MFAService {

    public MFASetupResult setupTOTP(String userId) {
        // 生成密钥
        String secret =_totp.generateSecret();

        // 生成二维码URL
        String qrCodeUrl = String.format(
            "otpauth://totp/AIOps:%s?secret=%s&issuer=AIOps",
            userId, secret
        );

        // 生成base64二维码图片
        String qrCodeBase64 = generateQRCode(qrCodeUrl);

        // 临时保存密钥(待用户验证后激活)
        redisTemplate.opsForValue().set(
            "mfa:setup:" + userId,
            secret,
            Duration.ofMinutes(10)
        );

        return new MFASetupResult(qrCodeBase64, secret);
    }

    public boolean verifyTOTP(String userId, String code) {
        String secret = getUserMFASecret(userId);
        return _totp.verify(secret, code, 1);  // 允许±1个时间窗口的误差
    }
}
```

### 5.2 登录流程(含MFA)

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ 输入账号密码  │────►│ 验证账号密码  │────►│ MFA已启用?   │
└──────────────┘     └──────────────┘     └───────┬──────┘
       │                    │                      │
       │                    │                      ▼
       │                    │              ┌──────────────┐
       │                    │              │ 验证MFA码    │
       │                    │              └───────┬──────┘
       │                    │                      │
       │                    ▼                      ▼
       │            ┌──────────────┐      ┌──────────────┐
       │            │ 返回临时Token│      │ 生成正式Token│
       │            │ (需MFA验证)  │      │ 登录成功     │
       │            └──────────────┘      └──────────────┘
       │
       ▼
┌──────────────┐
│ 登录失败     │
└──────────────┘
```

---

## 6. 审计与合规

### 6.1 审计事件类型

```java
public enum AuditEventType {
    // 认证事件
    LOGIN_SUCCESS("登录成功"),
    LOGIN_FAILED("登录失败"),
    LOGOUT("登出"),
    PASSWORD_CHANGE("修改密码"),
    PASSWORD_RESET("重置密码"),
    MFA_SETUP("设置MFA"),
    MFA_DISABLE("禁用MFA"),
    TOKEN_REFRESH("刷新Token"),

    // 授权事件
    PERMISSION_DENIED("权限拒绝"),
    ROLE_ASSIGNED("分配角色"),
    ROLE_REVOKED("撤销角色"),

    // 业务操作
    ALERT_CREATE("创建告警"),
    ALERT_UPDATE("更新告警"),
    ALERT_SUPPRESS("抑制告警"),
    AI_ANALYSIS_EXECUTE("执行AI分析"),
    RULE_CREATE("创建规则"),
    RULE_UPDATE("更新规则"),
    RULE_DELETE("删除规则"),

    // 管理操作
    USER_CREATE("创建用户"),
    USER_DELETE("删除用户"),
    CONFIG_CHANGE("配置变更"),

    // 安全事件
    SUSPICIOUS_LOGIN("可疑登录"),
    BRUTE_FORCE_ATTEMPT("暴力破解尝试"),
    DATA_EXPORT("数据导出");

    private final String description;
}
```

### 6.2 高风险操作告警

```yaml
# 风控规则
risk_rules:
  # 登录失败次数过多
  - name: 暴力破解检测
    condition: "same_ip_login_fail > 5 in 5min"
    action:
      - block_ip: 30min
      - alert_admin: true
      - require_captcha: true

  # 异地登录
  - name: 异地登录告警
    condition: "login_geo_diff > 500km in 1hour"
    action:
      - notify_user: email
      - require_mfa: true

  # 敏感操作
  - name: 批量删除告警
    condition: "delete_operations > 10 in 5min"
    action:
      - require_approval: true
      - alert_admin: true

  # 非工作时间操作
  - name: 非工作时间登录
    condition: "login_time not in work_hours(9-18)"
    action:
      - log_level: high
      - notify_admin: true
```

---

*本文档定义了AIOps平台的用户认证与权限管理体系，确保系统安全可控。*
