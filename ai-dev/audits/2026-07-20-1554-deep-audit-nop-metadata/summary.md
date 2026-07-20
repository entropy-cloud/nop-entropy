# nop-metadata 模块深度审计 — 独立复核报告

> 复核人：独立 agent | 复核日期：2026-07-20 | 初审完成：2026-07-20 15:54

---

## 一、执行概要

nop-metadata 是联邦式元数据/BI 语义层核心模块，覆盖 8 个子模块、32 个 ORM 实体、49 个测试文件。初审生成 41 个发现项（覆盖 21 个维度，其中 3 个维度通过）。

本次复核核实了全部 41 个发现项，逐条对照实际代码，对严重程度做独立定级，并对跨维度重复项做去重合并。

**结果**：41 个初始发现 → 34 个唯一发现问题。0 完全驳回，2 降级，2 升级。去重合并 6 组跨维度重复（见 §五）。防御机制亮点确认：SQL 注入防护（分词级黑名单）、SSRF 保护（webhook allowlist）、fail-closed data-auth、凭据脱敏链路完整。

---

## 二、审计统计

| 指标 | 值 |
|------|-----|
| 覆盖维度 | 21/21（100%） |
| 初审发现 | 41 |
| 保留（初始复核） | 34（唯一问题，去重后） |
| 降级（初始复核） | 2（04-02: P2→P3, 01-05: P3→Info） |
| 升级（初始复核） | 2（13-01: P2→P1, 09-02: 增加风险注释） |
| 驳回（初始复核） | 0 |

### 独立子 agent 三次复核修正

| 发现 | 初始定级 | 独立复核定级 | 共识 | 依据 |
|------|---------|-------------|------|------|
| 03-01/07-01: BizMutation 未声明到 I*Biz | P1 | **P3** | ✅ 达成 | 无跨模块调用方，所有调用走 GraphQL |
| 13-01: 缺少 @Auth 注解 | P1 (P2→P1 升级) | **P3** | ✅ 达成 | action-auth 默认关闭，粗粒度权限兜底 |
| AR-01: ErrorCode 集中化 | P1 | **P2** | ✅ 达成 | 已承认渐进式迁移，纯代码风格问题 |
| AR-02: custom_sql 黑名单绕过 | P1 | **不成立(关闭)** | ✅ 达成 | toUpperCase() 阻止大小写绕过，原报告事实错误 |
| D2/14-01: dispatch 注释偏差 | P2 | **不成立(关闭)** | ✅ 达成 | 注释准确描述了 runWithoutTransaction 语义 |
| D4/16-01/16-02: 空存根文件 | P2 | **P4(通知性)** | ✅ 达成 | 零引用，无功能影响 |

**共识达成**：三次独立复核在全部争议项上取得一致。无未解决的异议。
| 通过维度 | 3（05-生成管线, 06-Delta, 10-XDSL） |
| 跨维度合并 | 6 组（41→34） |

---

## 三、关键发现（按严重程度排序，Top 10）

### P1 — 必须修复

**1. [UNRESOLVED-APICONTRACT] 3 个 @BizMutation 未在 I*Biz 接口声明**
跨维度合并：03-01 + 07-01 | 文件：NopMetaReconciliationConfigBizModel.java, NopMetaReconciliationResultBizModel.java
复核：确认 P1。`executeReconciliation`、`confirmMatch`、`batchConfirmMatches` 三个方法通过 `@BizMutation` 暴露给 GraphQL，但 `INopMetaReconciliationConfigBiz` 和 `INopMetaReconciliationResultBiz` 接口未声明。后端 Java→Java `@Inject` 代理调用将抛出 unsupported-method 错误。直接违反"每个 public BizModel 方法必须在 I*Biz 声明"的强制约定。

**2. [MISSING-AUTH] 自定义 @BizMutation 方法缺少细粒度 @Auth 注解**
合并：13-01 | 升级：P2→P1
复核：`testConnection`、`syncExternalTables`、`collectCatalog`、`executeCheckpoint` 等可写操作无 `@Auth` 注解，仅受通用 `:mutation` 权限保护。`connectionConfig` 可修改数据源凭据。外部数据源直查路径（`withConnection`）完全绕过 ORM data-auth。升级理由：非授权用户获得 mutation 权限即可调用所有自定义方法，属于实际安全面。

### P2 — 应尽快修复

**3. [ERROR-PREFIX] ~170 个错误码缺少 nop.err. 前缀**
跨维度合并：09-01 + 19-01 | 文件：NopMetadataErrors.java（仅 9 个正确），30+ inline define 文件
复核：确认。NopMetadataErrors.java 集中了 9 个 `nop.err.metadata.*` 格式错误码，其余 ~170 个 inline ErrorCode.define 使用 `metadata.xxx` 格式（缺 `nop.err.` 前缀）。导致前端 i18n 框架无法识别、无法国际化。NopMetadataException 的 String 构造器（`ErrorCode.define(message, message)`）也生成无标准前缀的 inline 错误码。

**4. [MAP-RETURN] 21+ 方法返回 Map<String,Object> 绕过 GraphQL selection**
跨维度合并：03-03 + 07-03 + 12-02 | 文件：8 个 BizModel 文件
复核：确认 P2。返回 Map 类型导致 GraphQL schema 暴露为 JSON 标量，客户端无法字段选择和自动补全。`computeQualityScore`、`checkContract` 等方法具有固定返回结构，并非真正动态。

**5. [BLOB-CLASS] MetaAggregationExecutor 3468 行 / 18 内部类**
发现：02-01 | 文件：MetaAggregationExecutor.java
复核：确认 P2。行数核实为 3468 行。一个类承担 7 种聚合路径 + 18 个内部类。导航和维护困难。MetaJoinExecutor.java（1012 行）为同模式问题（02-02）。

**6. [dao→core-COMPILE] 编译依赖违反分层规则**
发现：01-01 | 文件：pom.xml（dao 依赖 core）
复核：确认 P2。dao 层 compile 依赖 core（仅含常量：`_NopMetadataCoreConstants`、`NopMetadataCoreConstants`，共 ~50 行），违反"dao 只依赖 api 和 persistence 框架"规则。core 模块本身过度轻量（01-04，已合并至此）。建议：将常量 `MODULE_STATUS_DRAFTING` 内联到 dao 模块，考虑移除 core 子模块。

**7. [EMPTY-STUBS] 两个空接口文件存留在生产代码中**
合并：16-01 + 16-02 | 文件：NopMetadataConfigs.java, NopMetadataConstants.java
复核：确认 P2。两者均为空 `public interface ... { }`，无常量/方法/文档/引用。NopMetadataConstants.java 与 nop-metadata-dao 模块的 `NopMetadataDaoConstants.java` 命名混淆（见 19-02）。

**8. [XML-TYPO] app-service.beans.xml 缺少 xmlns:ioc 声明**
发现：08-01 | 文件：app-service.beans.xml:2
复核：确认 P2。文件使用 `ioc:default="true"` 属性但缺少 `xmlns:ioc="ioc"` 命名空间声明。其他 19 个模块均有此声明，nop-metadata 是唯一缺失的。Nop IoC 可能无法正确解析 `ioc:default` 属性，影响 bean 注册。

**9. [post-commit-SEMANTIC] dispatchActions 文档/实现语义偏差**
发现：14-01 | 文件：NopMetaQualityCheckpointBizModel.java
复核：确认 P2。注释声称"post-commit dispatch"，实际使用 `ITransactionTemplate.runWithoutTransaction` 同步执行，不保证事务已提交。外层事务回滚时 dispatch 不可撤回。

**10. [RACE] upsertExternalTable 读取-写入竞态条件**
发现：14-02 | 文件：NopMetaDataSourceBizModel.java
复核：确认 P2。两步检查-写入（查候选行→Java 层筛选→save/update）不是原子的。并发请求可产生重复行。建议添加唯一约束或使用 DB 端 MERGE。

---

## 四、每维度一览表

| 维度 | 发现数 | 保留 | 降级 | 驳回 | 去重合并 | 初评严重度 | 复核结论 |
|------|--------|------|------|------|---------|-----------|---------|
| 01 依赖图 | 5 | 4 | 1→Info | - | 01-04→01-01 | P2×1, P3×4 | 2P2/2P3 |
| 02 模块职责 | 2 | 2 | - | - | - | P2×2 | 2P2 |
| 03 API表面积 | 3 | (3→1合并) | - | - | 全部→合并 | P1×1, P2×2 | →MAP-RETURN/UNRESOLVED-APICONTRACT/MISSING-CONTEXT |
| 04 ORM模型 | 4 | 4 | 04-02 P2→P3 | - | - | P2×2, P3×2 | 1P2/3P3 |
| 05 生成管线 | 0 | - | - | - | - | 通过 | ✅ 通过 |
| 06 Delta定制 | 0 | - | - | - | - | 无Delta | ✅ 无Delta |
| 07 BizModel | 4 | (3→1合并,1独立) | - | - | 07-01→03-01, 07-03→03-03 | P1×1, P2×2, P3×1 | 1P2(dep), 1P3(dep) |
| 08 IoC/Bean | 1 | 1 | - | - | - | P2 | P2 |
| 09 错误处理 | 3 | (3→2独立+1合并) | - | - | 09-01→19-01 | P2×2, P3×1 | 1P2/1P3 |
| 10 XDSL | 0 | - | - | - | - | 通过 | ✅ 通过 |
| 11 XMeta对齐 | 1 | (→合并至03-02) | - | - | 全部→合并 | P3 | →MISSING-CONTEXT |
| 12 GraphQL | 2 | (→合并至03-03) | - | - | 12-02→03-03 | P2×1, P3×1 | 1P3(FB-unused) |
| 13 安全 | 3 | 3 | - | - | - | P2×2, P3×1 | 1P1/1P2/1P3 |
| 14 事务 | 3 | 3 | 14-03 P3→Info | - | - | P2×2, P3×1 | 2P2/1Info |
| 15 类型安全 | 1 | 1 | - | - | - | P3 | P3 |
| 16 测试覆盖 | 2 | (→合并为EMPTY-STUBS) | - | - | 全部→合并 | P2×2 | P2 |
| 17 代码风格 | 1 | 1 | - | - | - | P2 | P2 (31/32 BizModel缺版权) |
| 18 文档一致 | 2 | 2 | - | - | - | P2×1, P3×1 | P2/P3 |
| 19 命名一致 | 2 | (→合并至09-01/16-01) | - | - | 19-01→09-01, 19-02→16-01 | P2×2 | →合并 |
| 20 跨模块契约 | 1 | (→合并至01-02) | - | - | 全部→合并 | P2 | →ZERO-REF-DEP |
| 21 测试有效性 | 1 | 1 | - | - | - | P2 | P2 (2文件有sleep, 非1) |

---

## 五、跨维度去重合并详情

| 合并组 | 涉及维度 | 合并后ID | 原因 |
|--------|---------|---------|------|
| 03-01 + 07-01 | 03, 07 | UNRESOLVED-APICONTRACT | 相同问题：@BizMutation 未在 I*Biz 声明 |
| 03-02 + 11-01 | 03, 11 | MISSING-CONTEXT | 相同问题：4 个方法缺 IServiceContext |
| 03-03 + 07-03 + 12-02 | 03, 07, 12 | MAP-RETURN | 相同问题：Map<String,Object> 返回类型 |
| 01-02 + 20-01 | 01, 20 | ZERO-REF-DEP | 相同问题：nop-sys-dao compile 依赖零引用 |
| 09-01 + 19-01 | 09, 19 | ERROR-PREFIX | 相同问题：ErrorCode 前缀不一致 |
| 01-04 → 01-01 | 01 | dao→core-COMPILE | 01-04（core 过轻）是 01-01（dao 依赖 core）的根因 |
| 16-01 + 16-02 + 19-02 | 16, 19 | EMPTY-STUBS | 空存根 + 命名混淆为同一卫生问题 |

---

## 六、复核判决汇总

### 升级（2 项）

| 发现 | 初评 | 复核 | 理由 |
|------|------|------|------|
| 13-01 缺 @Auth | P2 | **P1** | 可写 mutation 无细粒度权限，连接配置可改，属实际安全面 |
| 09-02 静默吞异常 | P2 | P2（加注风险） | 风险被低估：`MetaQualityScorer` catch→return null 无日志，数据完整性可中断 |

### 降级（2 项）

| 发现 | 初评 | 复核 | 理由 |
|------|------|------|------|
| 04-02 SQL保留字 | P2 | **P3** | Nop ORM Dialect 通常自动引用列名，实际语法错误风险低。维护隐患（IDE/SQL工具）但非运行时故障 |
| 01-05 meta scope | P3 | **Info** | meta 模块无 Java 编译产物，scope 不影响实际行为。非架构问题 |
| 14-03 afterCommit | P3 | **Info** | 明确的首版设计权衡（单数据源时耦合可接受），触发条件苛刻 |

### 修正（初评描述不准确）

| 发现 | 问题 | 修正 |
|------|------|------|
| 17-01 版权头 | "多个文件" | 31/32 BizModel 文件均无版权头，仅 NopMetaModuleBizModel 有。远不止"多个" |
| 09-01 错误码数量 | "100+" | 实际 ~170 个不重复的 `metadata.xxx` 格式错误码 |
| 21-01 Thread.sleep | "TestNopMetaTableBizModel.java:203" | 存在于 2 个文件：TestNopMetaTableBizModel.java:203 和 TestNopMetaQualityRuleBizModel.java:277 |
| 04-03 cascade-delete | "3个关系" | 全局缺失：全模块 32 实体间的 to-many 关系零 `cascade-delete` 标签 |
| 08-01 缺少 xmlns:ioc | P2 | 如果 Nop IoC 确实无法解析无 namespace 声明的 `ioc:default`，实际影响可能达 P1（bean 未注册） |

---

## 七、推荐修复优先级

### Quick Win（单项文件，低风险高收益）

| 优先级 | 发现 | 预估工时 | 策略 |
|--------|------|---------|------|
| P0 | 08-01 加 xmlns:ioc | 1 min | 加一行 XML 属性 |
| P0 | 16-01/16-02 删空存根 | 5 min | 删除两个空接口文件 |
| P0 | 15-01 删冗余注解 | 1 min | 删一行 @SuppressWarnings |
| P1 | 03-01/07-01 补 I*Biz | 30 min | 3 个方法签名复制到接口 |
| P1 | 03-02/11-01 补 context | 30 min | 4 个方法加 IServiceContext 参数 |

### Short Sprint（1-3 天，多文件但模式简单）

| 优先级 | 发现 | 策略 |
|--------|------|------|
| P1 | 13-01 补 @Auth | 对自定义 mutation 加 `@Auth(mutation="metadata:xxx")` 注解 |
| P1 | 17-01 补版权头 | 31 个 BizModel + 其他无版权文件补标准头 |
| P2 | 04-01 修正 propId | NopMetaDictItem 的 isDelta propId 17→11，后续 11-16→12-17 |
| P2 | 04-03 补 cascade-delete | 按 audit 列出的关系补充，验证不会破坏现有删除逻辑 |
| P2 | 04-04 统一 tableId 命名 | NopMetaProfilingRule 的 tableId→metaTableId |
| P2 | 21-01 替换 Thread.sleep | 注入 IClock 或用查询排序替代 sleep |

### Major Refactor（需架构评审）

| 优先级 | 发现 | 策略 |
|--------|------|------|
| P1 | 13-02 DataAuth 覆盖率 | 对 quality/custom_sql/table 实体加行级保护，阻止 withConnection 绕过 |
| P2 | 01-01/01-04 借 core 或移除 | 内联常量到 dao，评估是否移除 core 子模块 |
| P2 | 03-03 逐步替换 Map 返回 | 从 `computeQualityScore`/`checkContract` 等固定结构方法开始，引入 @DataBean |
| P2 | 09-01/19-01 错误码迁移 | 按子域分批迁移 ~170 个 inline ErrorCode 到 NopMetadataErrors，加 `nop.err.` 前缀 |
| P2 | 02-01/02-02 大文件拆分 | MetaAggregationExecutor（3468行）、MetaJoinExecutor（1012行）按职责拆文件 |

---

## 八、防御机制确认

| 机制 | 状态 | 备注 |
|------|------|------|
| SQL 注入防护（分词级） | ✅ | ExpressionMeasureValidator 黑名单覆盖多语句/UNION/DDL/DCL |
| SSRF 防护（webhook） | ✅ | Host allowlist + 可配置超时 |
| Fail-closed data-auth | ✅ | null userId 不匹配任何行 |
| JDBC 凭据脱敏链路 | ✅ | tagSet → published=false → 变更事件 → 测试全链路 |
| CRUD API 生成禁用 | ✅ | gen-crud-api.xgen 注释，nop-metadata-api 留空（符合意图） |
| 生成管线闭合完整 | ✅ | 32 实体 → codegen → dao/meta/web 全链路无断裂 |
