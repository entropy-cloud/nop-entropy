---
name: nop-database-design
description: (opencode-project - Skill) Nop平台数据库设计规范。定义表命名、列命名、主键设计、索引设计、通用字段、域定义、关系设计等规范。触发词：数据库设计、表设计、DDL、ORM模型、字段命名。
---

# 数据库设计规范

## 1. 总则

### 1.1 目的
本文档定义数据库设计的通用规范，确保数据模型的一致性、可维护性和可扩展性。

### 1.2 适用范围
适用于所有关系型数据库设计，包括但不限于 MySQL、PostgreSQL、Oracle、SQLite。
---

## 2. 命名规范
### 2.1 表命名

| 规则 | 示例 |
|------|------|
| 使用 snake_case，全小写 | `nop_auth_user`, `nop_auth_role` |
| **必须**使用单数形式 | `nop_auth_user` 而不是 `nop_auth_users` |
| **必须**添加模块前缀 | `nop_auth_user`, `nop_audit_client`, `nop_audit_finding` |
| 前缀格式：`{vendor}_{module}_{entity}` | `nop_auth_user`, `nop_audit_request` |
| 避免使用保留字 | 禁止: `user`, `order`, `group`; 推荐: `nop_auth_user`, `nop_audit_order` |

**示例**：
- `nop_auth_user` - 认证模块用户表
- `nop_auth_role` - 认证模块角色表
- `nop_audit_client` - 审计模块客户表
- `nop_audit_request` - 审计模块请求表

### 2.2 列命名

| 规则 | 示例 |
|------|------|
| 数据库列名使用小写 snake_case | `user_id`, `user_name`, `create_time` |
| 代码属性名使用 camelCase | `userId`, `userName`, `createTime` |
| 外键列使用 `_id` 后缀 | `dept_id`, `role_id`, `parent_id` |
| 布尔列使用 `is_` 或 `has_` 前缀 | `is_active`, `is_deleted`, `has_permission` |
| 时间列使用 `_time` 或 `_at` 后缀 | `create_time`, `expire_at`, `login_time` |
| 日期列使用 `_date` 后缀 | `birth_date`, `start_date`, `end_date` |

### 2.3 索引命名

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| 主键 | `pk_{表名}` | `pk_nop_auth_user` |
| 唯一键 | `uk_{表名}_{列名}` | `uk_nop_auth_user_name` |
| 普通索引 | `ix_{表名}_{列名}` | `ix_nop_auth_user_dept` |
| 组合索引 | `ix_{表名}_{列1}_{列2}` | `ix_nop_auth_role_resource` |
| 外键 | `fk_{表名}_{引用表名}` | `fk_nop_auth_user_dept` |

---

## 3. 主键设计

### 3.1 设计原则

**必须**使用应用层生成的主键，**禁止**使用数据库自增（AUTO_INCREMENT）或数据库序列（SEQUENCE）。

**原因**：
1. **分布式支持**：多节点部署时，数据库自增会导致主键冲突
2. **跨数据库迁移**：不同数据库的自增机制不兼容
3. **数据合并**：多源数据整合时，自增主键会冲突
4. **离线生成**：应用层可以在插入前生成ID

### 3.2 主键类型

| 类型 | 适用场景 |
|------|----------|
| **VARCHAR(32)** | UUID 去掉横线（默认推荐） |
| **VARCHAR(50)** | 雪花算法ID、自定义编码 |

**禁止使用**：BIGINT 自增、数据库 SEQUENCE、AUTO_INCREMENT

### 3.3 主键命名

```sql
-- 推荐：使用 {实体}_id 格式
user_id VARCHAR(32) PRIMARY KEY
role_id VARCHAR(50) PRIMARY KEY

-- 或使用通用 sid (surrogate id)
sid VARCHAR(32) PRIMARY KEY
```

### 3.4 主键生成策略

| 策略 | 推荐度 | 说明 |
|------|--------|------|
| **UUID v7** | ★★★★★ | 有序 UUID，索引友好，推荐使用 |
| **雪花算法** | ★★★★☆ | 有序、全局唯一、含时间信息 |
| **NanoID** | ★★★★☆ | 短小、URL 安全、可自定义长度 |
| **ULID** | ★★★★☆ | 有序、全局唯一、字典排序友好 |
| **UUID v4** | ★★★☆☆ | 随机 UUID，无序、索引效率较低 |

---

## 4. 通用字段（标准列）
每个业务表**必须**包含以下标准字段：

### 4.1 审计字段（必须）
| 列名 | 类型 | 必须 | 说明 |
|------|------|------|------|
| `created_by` | VARCHAR(50) | 是 | 创建人ID/用户名 |
| `create_time` | TIMESTAMP/DATETIME | 是 | 创建时间 |
| `updated_by` | VARCHAR(50) | 是 | 最后修改人ID/用户名 |
| `update_time` | TIMESTAMP/DATETIME | 是 | 最后修改时间 |

### 4.2 乐观锁字段（推荐）
| 列名 | 类型 | 必须 | 说明 |
|------|------|------|------|
| `version` | INTEGER | 推荐 | 数据版本号，每次更新+1 |

### 4.3 逻辑删除字段（推荐）
| 列名 | 类型 | 必须 | 说明 |
|------|------|------|------|
| `del_flag` | TINYINT(1) | 推荐 | 0=未删除，1=已删除 |
| `del_version` | BIGINT | 可选 | 删除版本，一般对应于删除时间（软删除）。初始值为0 |

### 4.4 备注字段（可选）
| 列名 | 类型 | 说明 |
|------|------|------|
| `remark` | VARCHAR(200) | 简短备注 |
| `description` | VARCHAR(1000) | 详细描述 |

---

## 5. 域定义（标准数据类型）
### 5.1 标识类
| 域名 | 类型 | 精度 | 说明 |
|------|------|------|------|
| `userId` | VARCHAR | 50 | 用户ID |
| `roleId` | VARCHAR | 50 | 角色ID |
| `deptId` | VARCHAR | 50 | 部门ID |
| `tenantId` | VARCHAR | 32 | 租户ID |
| `sid` | VARCHAR | 32 | 代理主键 |

### 5.2 联系方式类
| 域名 | 类型 | 精度 | 说明 |
|------|------|------|------|
| `userName` | VARCHAR | 50 | 用户名 |
| `email` | VARCHAR | 100 | 邮箱 |
| `phone` | VARCHAR | 50 | 电话 |
| `realName` | VARCHAR | 50 | 真实姓名 |

### 5.3 内容类
| 域名 | 类型 | 精度 | 说明 |
|------|------|------|------|
| `remark` | VARCHAR | 200 | 简短备注 |
| `description` | VARCHAR | 1000 | 描述 |
| `json-1k` | VARCHAR | 1000 | JSON配置（1K） |
| `json-4k` | VARCHAR | 4000 | JSON配置（4K） |
| `xml-4k` | VARCHAR | 4000 | XML配置 |
| `text` | TEXT | - | 长文本 |

### 5.4 布尔与状态类
| 域名 | 类型 | 说明 |
|------|------|------|
| `boolFlag` | TINYINT(1) | 布尔标志：0=否，1=是 |
| `status` | INTEGER | 状态码（建议用枚举字典） |
| `delFlag` | TINYINT(1) | 删除标志：0=正常，1=已删除 |

### 5.5 时间类
| 域名 | 类型 | 说明 |
|------|------|------|
| `createTime` | TIMESTAMP | 创建时间 |
| `updateTime` | TIMESTAMP | 更新时间 |
| `date` | DATE | 日期 |
| `datetime` | DATETIME | 日期时间 |

### 5.6 文件类
| 域名 | 类型 | 精度 | 说明 |
|------|------|------|------|
| `file` | VARCHAR | 100 | 单个文件路径 |
| `file-list` | VARCHAR | 500 | 多文件路径（JSON数组） |
| `image` | VARCHAR | 100 | 图片路径 |

---

## 6. 关系设计
### 6.1 外键关系

```sql
-- 外键列命名：{引用实体}_id
dept_id VARCHAR(50),        -- 引用 nop_auth_dept 表
parent_id VARCHAR(50),      -- 引用自身（树形结构）
role_id VARCHAR(50),        -- 引用 nop_auth_role 表
```

### 6.2 关系类型
| 关系类型 | 实现方式 | 示例 |
|----------|----------|------|
| **一对一** | 外键 + 唯一约束 | nop_auth_user - nop_auth_ext_login |
| **一对多** | 外键（多方持有） | nop_auth_dept - nop_auth_user |
| **多对多** | 中间表 | nop_auth_user_role |

### 6.3 中间表设计规范

```sql
-- 多对多关系中间表
CREATE TABLE nop_auth_user_role (
    user_id VARCHAR(50) NOT NULL,
    role_id VARCHAR(50) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    update_time TIMESTAMP NOT NULL,
    remark VARCHAR(200),
    PRIMARY KEY (user_id, role_id)
);
```

### 6.4 树形结构设计

```sql
-- 树形结构（如部门、菜单）
CREATE TABLE nop_auth_dept (
    dept_id VARCHAR(50) PRIMARY KEY,
    dept_name VARCHAR(100) NOT NULL,
    parent_id VARCHAR(50),           -- 父节点ID
    order_no INTEGER DEFAULT 0,       -- 排序号
    -- 标准字段...
    FOREIGN KEY (parent_id) REFERENCES nop_auth_dept(dept_id)
);
```

---

## 7. 索引设计
### 7.1 索引原则

1. **主键自动创建索引**
2. **外键列必须建索引**
3. **高频查询条件列建索引**
4. **排序字段考虑建索引**
5. **组合索引遵循最左前缀原则**

### 7.2 索引类型选择
| 场景 | 索引类型 |
|------|----------|
| 唯一约束 | UNIQUE INDEX |
| 外键关系 | NORMAL INDEX |
| 文本搜索 | FULLTEXT INDEX（MySQL） |
| 高频查询 | NORMAL INDEX |
| 组合查询 | COMPOSITE INDEX |

### 7.3 索引示例

```sql
-- 唯一索引
CREATE UNIQUE INDEX uk_nop_auth_user_name ON nop_auth_user(user_name);

-- 普通索引
CREATE INDEX ix_nop_auth_user_dept ON nop_auth_user(dept_id);

-- 组合索引
CREATE INDEX ix_nop_auth_resource_site ON nop_auth_resource(site_id, order_no);
```

---

## 8. 数据字典
### 8.1 通用状态码
| 状态值 | 含义 |
|--------|------|
| 0 | 禁用/无效 |
| 1 | 启用/有效 |

### 8.2 删除标志
| 值 | 含义 |
|----|------|
| 0 | 正常（未删除） |
| 1 | 已删除 |

### 8.3 性别
| 值 | 含义 |
|----|------|
| 0 | 未知 |
| 1 | 男 |
| 2 | 女 |

### 8.4 是/否标志
| 值 | 含义 |
|----|------|
| 0 | 否 |
| 1 | 是 |

---

## 9. 表设计模板
### 9.1 标准业务表模板

```sql
CREATE TABLE {prefix}_{module}_{entity} (
    -- 主键
    {entity}_id VARCHAR(32) PRIMARY KEY,
    
    -- 业务字段
    -- ...
    
    -- 树形结构（可选）
    parent_id VARCHAR(32),
    order_no INTEGER DEFAULT 0,
    
    -- 审计字段
    version INTEGER NOT NULL DEFAULT 0,
    del_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(200),
    
    -- 索引
    INDEX ix_{entity}_parent (parent_id)
);
```

**完整示例**：

```sql
CREATE TABLE nop_audit_client (
    client_id VARCHAR(32) PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    industry VARCHAR(50),
    status INTEGER NOT NULL DEFAULT 1,
    
    version INTEGER NOT NULL DEFAULT 0,
    del_flag TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(200)
);
```

### 9.2 关联表模板

```sql
CREATE TABLE {prefix}_{module}_{entity1}_{entity2} (
    -- 联合主键
    {entity1}_id VARCHAR(50) NOT NULL,
    {entity2}_id VARCHAR(50) NOT NULL,
    
    -- 可选扩展字段
    -- include_child TINYINT(1) DEFAULT 0,
    
    -- 审计字段
    version INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(200),
    
    PRIMARY KEY ({entity1}_id, {entity2}_id)
);
```

**完整示例**：

```sql
CREATE TABLE nop_auth_user_role (
    user_id VARCHAR(50) NOT NULL,
    role_id VARCHAR(50) NOT NULL,
    
    version INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(50) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(200),
    
    PRIMARY KEY (user_id, role_id)
);
```

### 9.3 日志表模板

```sql
CREATE TABLE {prefix}_{module}_{entity}_log (
    log_id VARCHAR(32) PRIMARY KEY,
    
    -- 关联ID
    {entity}_id VARCHAR(50) NOT NULL,
    
    -- 操作信息
    operation VARCHAR(100),
    action_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_time BIGINT,                    -- 耗时（毫秒）
    result_status INTEGER NOT NULL,      -- 0=成功，其他=失败
    error_code VARCHAR(200),
    ret_message VARCHAR(1000),
    
    -- 请求/响应（可选）
    op_request VARCHAR(8000),
    op_response VARCHAR(4000),
    
    -- 索引
    INDEX ix_log_{entity} ({entity}_id),
    INDEX ix_log_time (action_time)
);
```

**完整示例**：

```sql
CREATE TABLE nop_auth_op_log (
    log_id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    session_id VARCHAR(100),
    operation VARCHAR(100),
    action_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_time BIGINT,
    result_status INTEGER NOT NULL,
    error_code VARCHAR(200),
    ret_message VARCHAR(1000),
    op_request VARCHAR(8000),
    op_response VARCHAR(4000),
    
    INDEX ix_log_user (user_id),
    INDEX ix_log_time (action_time)
);
```

---

## 10. 安全考虑
### 10.1 敏感字段
| 字段类型 | 存储要求 |
|----------|----------|
| 密码 | 加密存储（BCrypt/Argon2），禁止明文 |
| 盐值 | 单独字段存储 |
| 身份证号 | 加密或脱敏存储 |
| 手机号 | 可脱敏存储 |
| 银行卡号 | 加密存储 |
| 地址 | 可加密存储 |

### 10.2 审计追踪
- 所有关键业务表必须包含 `created_by`, `create_time`, `updated_by`, `update_time`
- 敏感操作应记录操作日志
- 数据变更可使用审计表或CDC机制

---

## 11. 性能考虑
### 11.1 表设计
1. **避免过宽的表**：单表字段数建议不超过 50 个
2. **大字段分离**：TEXT/BLOB 考虑单独存储
3. **冷热数据分离**：历史数据归档

### 11.2 索引设计
1. **避免过多索引**：单表索引数建议不超过 10 个
2. **组合索引顺序**：高选择性列在前
3. **定期维护**：重建碎片化严重的索引

### 11.3 查询优化
1. 避免 `SELECT *`
2. 合理使用分页
3. 大表查询使用覆盖索引

---

## 12. 版本控制
### 12.1 Schema 变更管理
1. 所有 DDL 变更必须通过迁移脚本管理
2. 迁移脚本命名：`V{版本号}__{描述}.sql`
3. 示例：`V1.0.1__add_user_status_column.sql`

### 12.2 向后兼容
1. 新增列使用默认值
2. 删除列前确保无引用
3. 类型变更需评估数据迁移

---

## 附录 A：常用数据类型对照表
| 用途 | MySQL | PostgreSQL | Oracle | SQLite |
|------|-------|------------|--------|--------|
| 主键ID | VARCHAR(32) | VARCHAR(32) | VARCHAR2(32) | TEXT |
| 字符串 | VARCHAR(100) | VARCHAR(100) | VARCHAR2(100) | TEXT |
| 长文本 | TEXT | TEXT | CLOB | TEXT |
| 布尔 | TINYINT(1) | BOOLEAN | NUMBER(1) | INTEGER |
| 整数 | INTEGER | INTEGER | NUMBER(10) | INTEGER |
| 长整数 | BIGINT | BIGINT | NUMBER(19) | INTEGER |
| 小数 | DECIMAL(18,4) | DECIMAL(18,4) | NUMBER(18,4) | REAL |
| 日期 | DATE | DATE | DATE | TEXT |
| 时间戳 | TIMESTAMP | TIMESTAMP | TIMESTAMP | TEXT |
| JSON | JSON | JSONB | CLOB | TEXT |

---

## 附录 B：设计检查清单
- [ ] 表名使用单数形式
- [ ] 表名包含模块前缀
- [ ] 列名符合命名规范
- [ ] 主键设计合理
- [ ] 包含所有必须的标准字段
- [ ] 外键关系正确
- [ ] 索引设计合理
- [ ] 敏感字段已加密
- [ ] 考虑了性能优化
- [ ] 编写了迁移脚本
