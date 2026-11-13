# nop-dyn — 动态表单/实体模块

## 功能概览

运行时定义业务实体和页面，无需代码生成。

- 动态实体定义（实体名、表名、属性）
- 动态属性管理（支持多种 stdDomain）
- 实体关系定义
- 动态页面（支持 AMIS/OpenTiny/Formily 三种 schema；其中 AMIS 为 legacy，已 deprecated）
- 动态 SQL 定义
- 模块与应用组合
- 应用级补丁文件覆盖

## 动态模块命名约束（P1-3.7-001）

动态模块 `moduleName` **不得**使用 `nop-` 保留前缀，也**不得**与已注册静态平台模块同名（ moduleId = moduleName 中 `-`→`/`，如 `nop-auth` → `nop/auth`）：动态模块资源经 VFS in-memory 第一优先层加载，与静态模块同路径会整体遮蔽静态 xbiz/xmeta/orm（导致口令哈希直读、XPL 注入、ORM 定义替换）。`generateByAI`/`importExcel`/`save` 三个入口均强制校验，违反抛 `nop.err.dyn.module-id-conflict`（plan 2385）。

## 动态模型生成与缓存并发契约（SYS-CC/DYN-CC，plan 2525）

### 生成锁语义（MA-02）

- **命中路径无锁**：`DynResourceStore.getResource` 高频命中读不经过任何锁；仅 miss（懒生成）进入 `synchronized(this)` DCL 临界区。管理端一次编辑触发的代码生成不再令平台级动态模型请求排队。
- **生成期窗口声明**：miss 生成的临界区内含 DB I/O + XPL 代码生成 + 用户 on-load/on-unload 钩子——生成期间该资源的首次请求会等待生成完成（短暂不可用），同资源并发首访由 DCL 合并为单次生成。该窗口为有意设计：代码生成的共享 `IEvalScope` 互斥是正确性要求而非纯性能问题。
- **锁序不变式**：`DynResourceStore` monitor → `InMemoryCodeCache` monitor 单向；`InMemoryCodeCache` 内 this → CHM 单向（ABBA 防护见 `getOrmModel` 注释）。回归钉：`TestInMemoryCodeCacheDeadlock`。

### mergedStore 写时复制发布（I-01）

已发布 merged store 为只读快照：所有生成写点（`genBizObjFiles` 恢复路径 / `addToMergedStore`）从 `moduleStores` 权威源重建新 `InMemoryResourceStore` 后整体替换 cache 字段与已发布 `DynResourceStore` 的内层 store（volatile）——读侧永不观察到底层 `ResourceTreeNode` 的结构修改。

### 租户缓存生命周期（MA-03）

- `DynCodeGen.tenantCache` 条目实现 `IDestroyable` + `CacheConfig.destroyOnRemove()`：Caffeine 容量驱逐/TTL 过期时自动执行 `cache.clear()`（含逐模块 on-unload）——**容器上限（`nop.dyn.codegen.tenant-cache-container-size`，默认 100）必须 ≥ 活跃租户数**，否则驱逐会引发整租户重初始化风暴（on-load 副作用重放）。
- 摘除（`clearForTenant`/`destroy`）为先原子摘除后清理配对；DCL 初始化发布后复验条目仍在缓存，被并发摘除的初始化实例就地 clear 自愈。
- `getUsedTenantIds` 返回「当前缓存驻留租户集」而非活跃租户全集（监控语义以此为准）。

### 删除缓存失效（I-04）

动态模块/实体删除经 `afterEntityChange` 钩子失效三面（codeCache 模块 / VFS in-memory 层 / bizObjectManager+ModuleManager 动态集）：

- **模块级删除**（`NopDynModuleBizModel`）：`removeDynModule` + 全量 `reloadModel`（三面同步的唯一既有通道）。
- **实体级删除**（`NopDynEntityMetaBizModel`）：先从父集合同步移除（dao 删除不同步内存集合），再走模块级通道 `generateForModule` 重生成（已删实体不可再经实体级通道解析）+ `reloadModel(true)`。
- 已删模型在 reload 完成前的在途请求按旧模型完成（窗口语义，见上方生成期窗口声明）。

### moduleName 唯一索引（I-03）

`nop_dyn_module.moduleName` 有唯一索引 `UX_NOP_DYN_MODULE_NAME`（orm 模型 `<indexes>` 段推导新建库 DDL）：跨节点并发双建的权威防护。`generateByAI` 的应用层查重为顺序路径友好失败；并发输家经 DB 重复键转译得到同一 `ERR_DYN_MODULE_NAME_EXISTS`。**存量库升级**：先重复行预检（见 `nop-dyn/model/upgrade/2026-11-unique-index-nop-dyn-module.sql`）再手工建索引。

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopDynApp | `nop_dyn_app` | 应用定义 |
| NopDynModule | `nop_dyn_module` | 模块定义 |
| NopDynAppModule | `nop_dyn_app_module` | 应用-模块映射 |
| NopDynModuleDep | `nop_dyn_module_dep` | 模块依赖 |
| NopDynEntityMeta | `nop_dyn_entity_meta` | 实体元数据 |
| NopDynPropMeta | `nop_dyn_prop_meta` | 属性元数据 |
| NopDynEntityRelationMeta | `nop_dyn_entity_relation_meta` | 实体关系 |
| NopDynFunctionMeta | `nop_dyn_function_meta` | 函数定义 |
| NopDynPage | `nop_dyn_page` | 页面定义 |
| NopDynSql | `nop_dyn_sql` | SQL 定义 |
| NopDynFile | `nop_dyn_file` | 模块文件 |
| NopDynPatchFile | `nop_dyn_patch_file` | 补丁文件 |
| NopDynDomain | `nop_dyn_domain` | 数据域定义 |

## 存储类型

- `VIRTUAL`：虚拟实体（不创建实际表）
- `REAL`：真实实体（创建数据库表）

## 页面 Schema 类型

- `AMIS`：百度 AMIS JSON Schema（legacy，已 deprecated，见 `../04-reference/amis-legacy-deprecation.md`）
- `OpenTiny`：华为 OpenTiny 组件
- `Formily`：阿里巴巴 Formily 表单方案

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-dyn-api` | API DTO |
| `nop-dyn-dao` | ORM 实体与 DAO |
| `nop-dyn-service` | 业务逻辑 |
| `nop-dyn-web` | Web 层与管理页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-dyn/model/nop-dyn.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`
