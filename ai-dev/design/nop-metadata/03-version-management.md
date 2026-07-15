# nop-metadata 版本管理设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 模块版本管理机制
> Goal: 定义模块级别的版本管理策略，对齐 Maven 打包/发布粒度

---

## 一、设计决策

### 1.1 版本粒度：模块而非模型

**决策**: 版本管理的基本粒度是**模块（MetaModule）**，不是单个模型（MetaOrmModel）。

**理由**:

| 对齐维度 | Maven 行为 | nop-metadata 应对 |
|---------|-----------|------------------|
| 打包 | `mvn package` 按模块执行 | 一个模块版本 = 一次发布 |
| 发布 | Maven 仓库按 `groupId:artifactId:version` 索引 | MetaModule.version 对应 Maven version |
| 依赖 | 模块间依赖声明在 pom.xml | MetaModule 的 baseModuleId 表达继承 |
| 不变性 | 已发布的 artifact 不可变 | 模块版本 released 后不可变 |

**拒绝方案**: 版本放在 MetaOrmModel 上
- 一个模块内有多个模型类型（ORM/API/WF），它们共享同一个版本
- Maven 不支持"同一模块的不同模型不同版本"

### 1.2 继承粒度：模块间 Delta

**决策**: `x:extends` 的继承发生在**模块之间**，不发生在模块内部。

```
nop-auth v1 (base, released)       — 完整 ORM 模型
nop-app-mall v1 (delta, released)  — x:extends="nop-auth"，只写增量
```

**理由**: 模块版本发布后不可变。继承关系是模块级别的——nop-app-mall 的 v1 继承 nop-auth 的 v1，nop-app-mall 的 v2 继承 nop-auth 的 v2。

### 1.3 版本号策略

**决策**: 版本号使用 long 整数（单调递增），不用语义版本号。

**理由**:
- 语义版本号（1.2.3）在 Maven 中用于依赖解析，在 nop-metadata 中用于对比和排序
- long 更简单：`version = lastVersion + 1`，不需要解析规则
- Delta 链的深度不受版本号格式约束

---

## 二、核心模型

### 2.1 MetaModule（模块版本）

```
MetaModule                        — 模块（版本管理基本粒度）
  ├── moduleId                    — "nop/auth"（唯一标识）
  ├── moduleName                  — "nop-auth"
  ├── displayName                 — "Nop 认证模块"
  ├── version                     — long，模块版本号（发布后不可变）
  ├── baseModuleId                → MetaModule（Delta 继承的 base 模块版本，null 表示无继承）
  ├── status                      — "drafting" | "released" | "deprecated"
  ├── importedAt                  — 导入时间
  │
  ├── mavenGroupId                — Maven groupId（如 "io.nop"）
  ├── mavenArtifactId             — Maven artifactId（如 "nop-auth"）
  ├── mavenVersion                — Maven 版本号（如 "1.2.3"，与内部 version 对应）
  │
  ├── gitRepoPath                 — Git 仓库路径（如 "/Users/abc/sources/nop-entropy"）
  ├── gitBranch                   — Git 分支（如 "main", "feature/xxx"）
  ├── gitCommitId                 — Git commit hash（如 "abc1234"）
  │
  └── extConfig                   — 扩展属性 JSON
```

**状态流转**:
```
drafting → released → deprecated
                ↑
  (新版本 drafting)
```

### 2.2 Delta 继承链

```
nop-auth v1 (released)             baseModuleId = null
nop-auth v2 (drafting)             baseModuleId = null (新主版本)

nop-app-mall v1 (released)         baseModuleId = nop-auth v1
nop-app-mall v2 (drafting)         baseModuleId = nop-auth v2
```

**继承规则**:
1. `baseModuleId` 指向 base 模块的某个版本（必须是 released）
2. delta 模块只存储自己声明的内容（增量定义）
3. 合并后的完整定义（full）也存储在同一模块下，用 `isDelta` 区分
4. base 模块删除后，delta 模块的 full 定义仍可查

### 2.3 版本查询

| 视图 | 查询条件 | 用途 |
|------|---------|------|
| 模块当前版本 | `moduleId=? AND status='released'` | 获取最新已发布版本 |
| Delta 定义 | `moduleId=? AND version=? AND isDelta=true` | 本模块自己声明了什么 |
| Full 定义 | `moduleId=? AND version=? AND isDelta=false` | 合并后完整模型 |
| 版本历史 | `moduleId=? ORDER BY version DESC` | 版本演进时间线 |
| 版本对比 | 两个版本的 full 定义 diff | 变更影响分析 |

---

## 三、模块内模型组织

### 3.1 模型类型

一个模块可以包含多种模型类型，每种独立一张表：

```
MetaModule (version)
  ├── MetaOrmModel[]        — ORM 模型（model/*.orm.xml）
  ├── MetaApiModel[]        — API 模型（预留）
  └── MetaWfModel[]         — 工作流模型（预留）
```

### 3.2 MetaOrmModel（ORM 模型）

```
MetaOrmModel                    — ORM 模型（属于某个模块版本）
  ├── modelId                   — PK
  ├── moduleId                  → MetaModule
  ├── modelName                 — "nop-auth"（同一模块内分组 key）
  ├── isDelta                   — true: 本模块声明的 delta, false: 合并后的 full
  ├── sourceContent             — CLOB，orm.xml 原文
  └── importedAt
```

**注意**: MetaOrmModel 不再有 `version` 和 `status` 字段——这些在 MetaModule 上。

### 3.3 ORM 模型内容拆解

所有子实体跟随 MetaOrmModel：

```
MetaOrmModel
  ├── MetaEntity[]             — 实体
  │   ├── MetaEntityField[]    — 字段
  │   ├── MetaEntityRelation[] — 关系
  │   ├── MetaEntityUniqueKey[] — 唯一键
  │   └── MetaEntityIndex[]    — 索引
  ├── MetaDomain[]             — 域定义
  └── MetaDict[]               — 字典
      └── MetaDictItem[]       — 字典项
```

---

## 四、版本生命周期

### 4.1 模块导入流程

```
orm.xml → 解析 → MetaOrmModel(isDelta=true)
               → MetaOrmModel(isDelta=false, 合并后)
               → MetaEntity/Field/Relation/Dict (跟随 MetaOrmModel)
               → MetaTable (自动为每个 MetaEntity 创建)
```

### 4.2 版本发布

```
1. 用户在 UI 上选择模块版本
2. 检查所有 MetaOrmModel 的 status = released
3. 设置 MetaModule.status = released
4. version 字段发布后不可变
5. 发布 MetaModuleChangedEvent 事件
```

### 4.3 版本废弃

```
1. 用户选择废弃某个模块版本
2. 设置 MetaModule.status = deprecated
3. 该版本下的模型仍然可查，但标记为已废弃
```

---

## 五、与 Maven 的对齐

### 5.1 映射关系

| Maven 概念 | nop-metadata 概念 |
|-----------|-------------------|
| groupId | MetaModule 的组织前缀（如 `io.nop`） |
| artifactId | MetaModule.moduleId 的最后一段（如 `auth`） |
| version | MetaModule.version |
| packaging | 由模型类型决定（ORM = Java POJO） |
| dependencies | MetaModule 的 baseModuleId + 额外依赖 |

### 5.2 导入触发时机

- **构建时**: `mvn install` 后自动导入（推荐）
- **手动导入**: UI 上点击"导入模块"
- **定时同步**: 定时扫描已注册模块的 orm.xml 变更

---

## 六、待定问题

- [ ] MetaModule 的 `sourceContent` 存储什么？pom.xml 全文？关键字段提取？
- [ ] 跨模块依赖（非继承）如何表达？如 nop-auth 依赖 nop-core
- [ ] 版本对比的 UI 展示方式：并排 diff？层级展开？
