# 紧凑扩展字段设计

**日期**：2026-06-24
**范围**：nop-sys 模块、ORM 实体集成
**状态**：草案

---

## 一、设计结论

1. **配置表**：`NopSysCompactExtField`（`nop_sys_compact_ext_field`），放在 `nop-sys` 模块
2. **核心接口**：`ICompactExtFieldSupport`，放在 `nop-orm` 模块（ORM 支持层）
3. **操作接口**：`ICompactExtFieldHelper`，由 `nop-sys` 模块提供实现
4. **集成方式**：通过 `CompactExtFieldHelper.registerInstance()` 注册实现
5. **存储结构**：单一 `varchar` 字段，每个字符位置对应一个枚举值
6. **前端集成**：前端根据 domain 信息自动生成对应的编辑控件

---

## 二、背景与动机

### 2.1 现有方案对比

| 方案 | 表结构 | 优点 | 缺点 |
|------|--------|------|------|
| `NopSysExtField` | KV 表 | 类型丰富、可配置 | JOIN 查询开销大、存储开销大 |
| `NopSysDict` + 独立列 | 独立字典表 + 实体列 | 类型安全、查询高效 | Schema 变更代价高 |
| **紧凑扩展字段** | 单 varchar 列 | 存储紧凑、查询高效 | 仅支持字符/枚举类型 |

### 2.2 设计目标

- **存储紧凑**：N 个标记仅需 N 个字符
- **查询高效**：单表查询，无需 JOIN
- **声明式配置**：通过配置表定义字段含义
- **类型安全**：支持字典映射，UI 自动渲染

---

## 三、核心设计

### 3.1 配置表结构

**实体**：`NopSysCompactExtField`
**表名**：`nop_sys_compact_ext_field`
**包名**：`io.nop.sys.dao.entity`

| 字段 | 类型 | 说明 |
|------|------|------|
| `entityName` | VARCHAR(200) | ORM 实体名（复合主键） |
| `propName` | VARCHAR(100) | 紧凑扩展属性名（复合主键） |
| `position` | INT | 字符位置（从 1 开始，复合主键） |
| `displayName` | VARCHAR(100) | 显示名称（业务代码使用） |
| `dictName` | VARCHAR(150) | 关联字典名（可选） |
| `defaultValue` | VARCHAR(10) | 默认值（空白 = null） |

### 3.2 核心接口

**接口位置**：`io.nop.orm.support.ICompactExtFieldSupport`

| 方法 | 类型 | 说明 |
|------|------|------|
| `orm_entityName()` | 抽象 | 获取实体名（与 IOrmEntity 一致） |
| `getExtFlags()` | 抽象 | 获取原始字符串值 |
| `setExtFlags(String)` | 抽象 | 设置原始字符串值 |
| `getExtValue(String)` | default | 根据 displayName 获取值 |
| `setExtValue(String, String)` | default | 根据 displayName 设置值 |
| `getExtBoolean(String)` | default | 获取布尔值（"0"→false，其他→true） |
| `setExtBoolean(String, Boolean)` | default | 设置布尔值（true→"1"，false→"0"） |
| `getExtValues()` | default | 批量获取所有扩展字段值 |
| `setExtValues(Map)` | default | 批量设置扩展字段值 |

### 3.3 使用契约

#### 3.3.1 实体声明

在 ORM 模型中使用 `compactExt` domain：

```xml
<domains>
    <domain name="compactExt" precision="64" stdSqlType="VARCHAR"/>
</domains>

<entity ...>
    <column code="EXT_FLAGS" name="extFlags" domain="compactExt"/>
</entity>
```

框架在代码生成时自动检测并实现 `ICompactExtFieldSupport` 接口。

#### 3.3.2 业务代码使用

```java
// 布尔操作
Boolean isVip = entity.getExtBoolean("isVip");
entity.setExtBoolean("isVip", true);

// 枚举操作
String userLevel = entity.getExtValue("userLevel");
entity.setExtValue("userLevel", "A");

// 批量操作
Map<String, String> values = entity.getExtValues();
entity.setExtValues(values);
```

### 3.4 数据模型示例

**存储结构**：
```
extFlags: "10A3"
          ↑↑↑↑
          │││└── position=4: dictName="level" → "GOLD"
          ││└── position=3: dictName="status" → "A"
          │└── position=2: 无 dict → false
          └── position=1: 无 dict → true
```

**配置示例**：

| entityName | propName | position | displayName | dictName | defaultValue |
|------------|----------|----------|-------------|----------|--------------|
| UserEntity | extFlags | 1 | isVip | - | 0 |
| UserEntity | extFlags | 2 | isActive | - | 1 |
| UserEntity | extFlags | 3 | userLevel | user-level | 0 |

### 3.5 缓存机制

参考 `IDictProvider` 的缓存模式：

| 维度 | 说明 |
|------|------|
| **缓存键** | `["compactExt", entityName]` |
| **缓存获取** | `IServiceContext.requireCtx().getCache()` |
| **策略** | 首次访问从数据库加载，后续从缓存获取 |
| **清理** | 配置变更时需手动清除缓存 |

### 3.6 框架集成

**实现生成**：ORM 代码生成器自动生成 `getExtFlags()`/`setExtFlags()` 方法

**助手实现**：`SysCompactExtFieldHelper` 在 `@PostConstruct` 时注册为全局实例

---

## 四、约束与边界

1. **仅支持字符类型**：每个位置存储一个字符
2. **默认值语义**：空白等价于 null，业务自行处理
3. **无索引支持**：复杂查询由业务层面通过函数索引优化
4. **无数据迁移工具**：null 值处理由业务层实现

---

## 五、模块依赖

```
nop-orm (接口定义)
    ↓
nop-sys (配置表 + 助手实现)
```

---

## 六、文件清单

| 文件 | 路径 | 说明 |
|------|------|------|
| `ICompactExtFieldSupport.java` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/` | 核心接口 |
| `ICompactExtFieldHelper.java` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/` | 助手接口 |
| `CompactExtFieldHelper.java` | `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/` | 默认空实现 |
| `SysCompactExtFieldHelper.java` | `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/impl/` | 完整实现 |
| `_app.orm.xml` | `nop-sys/nop-sys-dao/src/main/resources/_vfs/nop/sys/orm/` | 配置表实体定义 |