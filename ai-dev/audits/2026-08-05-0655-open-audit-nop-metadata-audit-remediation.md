> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata-audit-remediation
> Processed: 2026-08-05 — AR-01/AR-02 → plan `ai-dev/plans/nop-metadata-audit-remediation/2026-08-05-1842-1-ssrf-host-normalization-unification.md`；AR-03 → plan `2026-08-05-1842-2-lineage-api-explicit-error.md`；AR-04/AR-05 → plan `2026-08-05-1842-3-doc-drift-and-audit-tracking.md`；AR-06~10 → roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`）

# 开放式对抗审查 — nop-metadata（mission: nop-metadata-audit-remediation）

## 执行说明

- **切入点**：异常路径侦探（SSRF 归一化盲区）+ 模型攻击者（UK 语义）+ 契约考古学家（DTO 字段语义）+ 治理审计（mission 追踪矩阵完整性）
- **方法**：完整阅读 06:55 multi-audit 报告、arm-index、roadmap（v13 终态）、MR3/MV/MG/R4.2/R4.3 记录；live code 逐项复核 multi-audit 6 个 P1 的当前状态；git 追踪最新两个修复 commit（R4.2 `4229d382e`、R4.3 `9e57d6373`）全量 diff 审查；jshell 实测 JDK 26 `InetAddress` 解析语义；docs-for-ai 三处契约断言核对；arm-index 追踪矩阵完整性核对
- **去重前置**：本次不机械重述 06:55 multi-audit / MA1-7 已精确描述且已修复的项；对"已知未修复"项标注并补充现状变化

---

## P1 发现（必须修复）

### [AR-01] [P1] MetaDataSourceConnectionProcessor.isInternalHost 仍无 IP 记法变体归一化 —— JDBC 建连 SSRF 绕过（已知未修复，未入追踪矩阵）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:343-375`（isInternalHost）、`:269-298`（extractHost）
- **证据片段**（jshell 26.0.1 实测解析语义）：
  ```
  2130706433  -> 127.0.0.1   (loopback)
  0.1         -> 0.0.0.1     (0.0.0.0/8)
  010.0.0.1   -> 10.0.0.1    (RFC1918，前导零按严格十进制）
  0127.0.0.1  -> 127.0.0.1   (loopback）
  0.256       -> 0.0.1.0     (0.0.0.0/8）
  ```
  而 `isInternalHost` 仍是纯字符串前缀比对（`127.`/`10.`/`192.168.`/`172.`/`169.254.`），`extractHost("jdbc:mysql://2130706433:3306/db")` 返回 `"2130706433"` → 全部不命中 → **放行**；`010.0.0.1`/`0127.0.0.1`/`127.1`/`0.1` 同样全部放行，而 JDK 解析均为内网/回环。
- **严重程度**: P1 — 安全：JDBC 路径内网判定可被数字变体/前导零/短格式绕过，构成 DB 型 SSRF（`jdbc:mysql://2130706433:3306/db` → 127.0.0.1）
- **现状**: 与 2026-08-05 06:55 multi-audit P1-01 完全一致——**已知未修复**。MA7.2-01 修复（commit 9b769490e）只覆盖了 userinfo/IPv6 字面量（extractHost），`isInternalHost` 主体未做任何归一化；此后 R4.2/R4.3（17:14/18:01 最新 commit）均未触碰该文件（git log 证实 9b769490e 是最后改动）。
- **风险**: 拥有数据源配置权限的用户可绕过内网判定触达内网 DB / 云元数据端点（169.254.169.254 经 `0169.254.169.254` 变体）。与同模块 dispatcher 的 "与 MetaDataSourceConnectionProcessor 一致" 注释（CheckpointActionDispatcher.java:301）继续漂移——两实现仍不一致。
- **建议**: 提取共享 `HostSecurityUtil`（按 JDK `textToNumericFormatV4` 严格十进制 + 1-4 段位移语义 + 0.0.0.0/8 前缀 + 前导零归一化），connection processor 与 dispatcher 统一调用；补 `2130706433`/`0.1`/`010.0.0.1`/`0127.0.0.1`/`127.1` 正负用例（jshell 已验证全部分析为内网）。
- **信心水平**: 确定（JDK 26 实测 + live code 核对）

### [AR-02] [P1] CheckpointActionDispatcher 短格式 IPv4 遗漏 + 八进制语义与 JDK 严格十进制错配 —— webhook SSRF 绕过（已知未修复）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java:308-378`（isInternalHost）、`:406-449`（looksLikeDottedIpv4/parseDottedIpv4）
- **证据片段**:
  ```java
  // L406-417: looksLikeDottedIpv4 要求 dotCount == 3 → "127.1"/"0.1"/"0.256" 不进入
  // L420-449: parseDottedIpv4 对前导零段按 radix=8（inet_aton 语义）
  //   "0127.0.0.1" → 87.0.0.1（非内网）→ 放行；JDK 实测 → 127.0.0.1（loopback）
  //   "0169.254.169.254" → 87.254.169.254（非内网）→ 放行；JDK 实测 → 169.254.169.254（link-local）
  ```
- **严重程度**: P1 — 安全：默认 fail-closed 配置下 `http://127.1/`、`http://0.1/`、`http://0.256/`、`http://0127.0.0.1/` 等被放行，JDK HttpClient 实际解析到 loopback/0.0.0.0/8，可触达本机管理端口与内部 HTTP 服务
- **现状**: 与 06:55 multi-audit P1-02 完全一致——**已知未修复**。MA7.6-04（commit 9b769490e）修复了 4 段点分/十进制整数/IPv4-mapped，但其 `parseDottedIpv4` 采用 inet_aton 八进制语义，与 JDK 严格十进制**错配**（"修复本身语义与 JDK 实际行为不一致"）；1-2 段短格式（`127.1`→127.0.0.1）完全未覆盖。jshell 复核：`0x7f.0.0.1`/`0177.0.0.1` 在 JDK 26 不可利用（非数值/严格十进制→177.0.0.1 非内网），与 multi-audit 勘误一致，无需重复。
- **风险**: 普通可配 checkpoint actions 的用户无需白名单即可触发 webhook SSRF 打到 localhost；`TestCheckpointActionDispatcherWebhookSsrf` 无 `127.1`/`0.1`/`0127.0.0.1` 用例。
- **建议**: 短格式（1-4 段）统一按 Java 严格十进制位移语义归一化后交 `isInternalIpv4`；废弃 parseDottedIpv4 八进制分支；补 `127.1`/`0.1`/`0.256`/`0127.0.0.1`/`0169.254.169.254` 回归用例（正：拒绝；负：`0177.0.0.1` 放行）。
- **信心水平**: 确定（JDK 26 逐字实测 + live code 核对）

### [AR-03] [P1] 血缘提取公开 API 将"SQL 解析失败"降级为成功响应 —— 文档化"无静默跳过"契约违反（已知未修复）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:158-166, 214-222`；`NopMetaLineageEdgeBizModel.java:106-131`
- **证据片段**:
  ```java
  // QueryAction:162-166（表级）与 :218-222（列级）：catch NopException → errors.add + refs=emptyList
  // BizModel:113-118 / 128-131：dto.setErrors(r.errors); return dto;  ← 无 errors 非空检查
  ```
- **严重程度**: P1 — 契约漂移：公开 GraphQL mutation 返回 200 + edgeCount=0，违反 `docs-for-ai/03-modules/nop-metadata.md:161`"SQL 解析失败 → 显式抛 NopException + ErrorCode，不静默空集"
- **现状**: **已知未修复**（06:55 P1-03 原样）。两级行为不一致仍在：表级空 sourceSql 降级（158-166），列级空 sourceSql 显式抛（211-213）。抽取器层测试钉死"必须抛异常"，但异常在 QueryAction 被吞，到不了 API 边界。
- **风险**: 调用方不检查嵌套 `data.errors` 时，解析失败被当作成功的零边抽取；与同模块表不存在/类型错误的显式抛错路径形成反差。
- **建议**: BizModel 在 `r.errors` 非空时抛 `NopMetadataException(ERR_LINEAGE_SQL_PARSE_FAILED / ERR_COL_LINEAGE_SQL_PARSE_FAILED)`（param 带 metaTableId），补 API 级非法 SQL 回归测试（`assertTrue(resp.hasError())`）；或文档化豁免并统一两级空 SQL 行为。
- **信心水平**: 确定

### [AR-04] [P1] 文档契约漂移三件仍 live：实体表 21/39、META-004 transformType 枚举错误、全称 I*Biz 断言（已知未修复）

- **文件**: `docs-for-ai/03-modules/nop-metadata.md:19-41`、`:109`；`docs-for-ai/04-reference/source-anchors.md:168`
- **证据**:
  - 实体表仍止于 21 行（NopMetaManifest），orm.xml 39 实体未变；NopMetaOrmModel/NopMetaClassification/NopMetaTagLabel/NopMetaGlossary 等 18 个仍缺
  - `source-anchors.md:168` 仍写 `transformType: direct/expression/aggregate`；代码/常量/dict 三方一致为 `direct/derived/aggregated`（`SqlColumnLineageExtractor.java:400-402`、dict yaml 复核）
  - `nop-metadata.md:109` 仍全称断言"每个 BizModel 都实现了对应的 INopMeta*Biz 接口"；`nop-metadata-dao/.../biz/` 下仍无 INopMetaSearchBiz（NopMetaSearchBizModel 例外未文档化）
- **严重程度**: P1 — 文档契约漂移：规范来源写出不存在的枚举值与全称断言，开发者按文档写 `transformType == "expression"` 或 `@Inject INopMetaSearchBiz` 会静默失败/编译失败
- **现状**: **已知未修复**（06:55 P1-04/05/06 原样）。R3.7 文档批量修复覆盖的是 MA5.x 清单，未含 multi-audit 三件。
- **风险**: 误导后续开发（重复建模 18 个已有实体、按错枚举写断言、跨模块按文档注入不存在的接口）。
- **建议**: 补全表格至 39（或注明完整清单入口）；source-anchors.md:168 改 `direct/derived/aggregated`；模块文档补 NopMetaSearch Pseudo-BizModel 例外（或补接口）；修后跑 `node ai-dev/tools/check-doc-links.mjs --strict`。
- **信心水平**: 确定（三方核对）

### [AR-05] [P1] 治理缺口：06:55 multi-audit 的 6 个 P1 从未入 arm-index/roadmap —— mission 闭环声明（"P1 12/12 PASS"）口径不含它们

- **文件**: `ai-dev/audits/arm-index-nop-metadata.md`（P1 汇总表 19 行，无 multi-audit 条目）；`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（Work Item Status 各表）；`ai-dev/audits/2026-08-05-0655-multi-audit-nop-metadata-audit-remediation.md`（仍为 open 状态）
- **证据**:
  ```
  grep "0655\|P1-0[1-6]" arm-index-nop-metadata.md → 0 命中
  grep "SSRF" arm-index → 仅 MA7.2-01（userinfo/IPv6，已 fixed）与 MA7.6-04（dispatcher 变体，已 fixed）
  multi-audit 文件头 "Audit Status: open"，6 个 P1 无任何 tracking 引用
  ```
- **严重程度**: P1 — 治理/契约漂移：MV closure audit（V.2）"P0 4/4 + P1 12/12 PASS + 0 untraceable" 的追踪矩阵**没有覆盖** multi-audit 的 6 个 P1——其中 2 个是实测可绕过的 SSRF。使命按现有矩阵宣称闭环，但 6 个 P1 全部仍 live（本次逐项复核确认）。"0 untraceable" 结论对 multi-audit 输入不成立。
- **现状**: multi-audit（06:55）产出后未经历"产出即更新 arm-index"（roadmap 横切关注点明示的规范）即被搁置；MR3/MV/MG/R4.2/R4.3 继续执行，multi-audit 的 P1 从未进入任何 MR 展开器。
- **风险**: 修复排程完全丢失；closure audit 的"P1 全闭"声明误导后续；AR-01/AR-02 两个 SSRF 绕过至少已存续一整轮 mission（2026-08-05 全天）。这是"虚假关闭/薄弱证明"类问题，也是本仓库应当把"multi-audit 结果入库追踪"提升为检查项的信号。
- **建议**: 将 6 个 multi-audit P1 登记入 arm-index + roadmap（标注来源 2026-08-05 multi-audit），优先修复 AR-01/AR-02（共享 HostSecurityUtil）与 AR-03；mission 重新验证前不得将 multi-audit 文件置 closed；考虑在 roadmap-authoring skill 增加"多审计源汇入追踪矩阵"检查项（沿 G.2 先例）。
- **信心水平**: 确定

---

## P2 发现（记录，不单独驱动 remediation plan）

### [AR-06] [P2] LineageExtractResultDTO.sourceTables 被填充为 unresolved 列表 —— 字段语义与名称相反，解析成功的源表从不暴露

- **文件**: `NopMetaLineageEdgeBizModel.java:117`（`dto.setSourceTables(r.unresolved)`）vs `NopMetaLineageEdgeQueryAction.java:526-536`（LineageExtractResult 仅含 edgeCount/unresolved/errors，无 resolved 列表）
- **证据**:
  ```java
  dto.setEdgeCount(r.edgeCount);
  dto.setSourceTables(r.unresolved);   // ← 名称 sourceTables，实际赋值 unresolved
  dto.setUnresolved(r.unresolved);
  ```
- **严重程度**: P2 — 契约缺陷：`sourceTables` 字段返回的是"未解析表名"（与 `unresolved` 完全重复），而"本次解析命中的源表"列表（candidateSourceIds）无处承载；列级路径（extractColumnLineageFromSql）则完全不设 sourceTables（空列表默认）。全仓 0 消费方（grep 证实），故不升 P1。
- **风险**: 未来消费方按字段名语义读取 sourceTables 会得到相反含义的数据；排查血缘结果时无法知道解析命中了哪些表。
- **建议**: LineageExtractResult 增加 resolved source 列表字段并正确填充 sourceTables；或删除 sourceTables 字段避免双名重复。
- **信心水平**: 确定

### [AR-07] [P2] R4.2 把可空 META_SCHEMA 纳入 UK 后，DB 层不再拦截 NULL-schema 重复行 —— find-then-insert 竞态从"约束违例失败"退化为"静默重复行"

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:224`（`UK_NOP_META_TABLE_MODULE_NAME (metaModuleId, tableName, isDelta, metaSchema)`，META_SCHEMA 可空）；`NopMetaDataSourceBizModel.java:428-468`（upsertExternalTable find-then-insert）；`NopMetaTableBizModel.java:166-173`（createSqlTable 守卫同样非原子）
- **证据**: MySQL/PostgreSQL 唯一索引对含 NULL 的复合键不判冲突（NULL != NULL）。R4.2 前 UK 三列全非空 → 并发重复插入必被 DB 拒绝；R4.2 后 null-schema 元组（如两个无 schema 数据源并发 sync 同名表）**两次插入都成功** → 永久重复行；Java 层 find-then-insert 非原子，无法兜底；且 findAllByQuery 无 ORDER BY，后续 dedup 命中行不确定（另一行成为陈旧孤儿行）。R4.2 计划自身只对 SQL 表（createSqlTable）补了守卫，未覆盖 external 路径——"路径 A（保持可空）"的 UK 语义代价只处理了一半。
- **严重程度**: P2 — 竞态概率低（需并发触发同步/创建），后果为静默数据重复而非崩溃；与 R4.2 的"多 schema 支持"目标部分自相矛盾（null-schema 正是最常见形态）。
- **风险**: catalog 出现同名重复逻辑表，血缘/查询命中歧义；升级部署后（upgrade-nop-meta-table-uk.sql 已落地）旧行为保护消失。
- **建议**: upsertExternalTable 与 createSqlTable 的 find-then-insert 改为带唯一键的原子 upsert（或事务内 select-for-update / 捕获约束异常重试）；或将 SQL 表/无 schema 场景的 schema 存储改为空串占位（非 null）以恢复 UK 强制力（需评估 Oracle ''=NULL 语义）。
- **信心水平**: 很可能（DB 语义确定，竞态场景为推演）

### [AR-08] [P2] createSqlTable 重复守卫查询缺 isDelta/schema 过滤 —— 比 4 列 UK 更严，delta 行/异 schema 行存在时误报 ERR_SQL_VIEW_TABLE_EXISTS

- **文件**: `NopMetaTableBizModel.java:164-173`
- **证据**: 守卫查询仅按 `(metaModuleId, tableName)`；而 R4.2 UK 允许 `(m, t, isDelta=0, NULL)` 与 `(m, t, isDelta=1, NULL)` 共存、`(m, t, 0, "public")` 与 `(m, t, 0, NULL)` 共存。守卫与 DB 语义不一致：已导入的 delta 表（isDelta=1，importOrmModel 持久化）或异 schema external 表存在时，创建同名 SQL 视图被误判重复，而 DB 本身允许。
- **严重程度**: P2 — 误报场景罕见（需同模块同名 delta/external 行），行为从"DB 允许"退化为"Java 拒绝"，且错误信息误导（实际是"存在同名异 isDelta/schema 行"而非"同名 SQL 表已存在"）。
- **建议**: 守卫查询补 `isDelta=0`（+ `metaSchema IS NULL`，EQL 若不支持 IS NULL 则在 Java 层过滤），精确镜像 UK 元组。
- **信心水平**: 确定（代码核对）

### [AR-09] [P2] R4.3 cron 错误路径 DTO 不携带 runId；R4.2 多 schema 语义未进模块文档

- **文件**: `MetaQualityCheckpointScheduler.java:242-250`（buildErrorResult 无 setRunId）；`docs-for-ai/03-modules/nop-metadata.md`（无 metaSchema/多 schema/UK 维度变更的说明，仅 R4.3 段已更新）
- **证据**: 手动路径 `executeCheckpoint` 返回的 DTO 带 runId（`dto.setRunId(runId)`），cron 错误路径 `buildErrorResult` 不设——同一方法族返回值结构不一致；模块文档 §7 已写 R4.3 幂等语义，但 R4.2 的 UK 扩展（`(metaModuleId, tableName, isDelta, metaSchema)`）与 upsertExternalTable 去重语义（java 层 schema 匹配）未文档化，升级 SQL（upgrade-nop-meta-table-uk.sql）也无部署说明。
- **严重程度**: P2 — 信息不一致/文档缺口，无运行时缺陷。
- **建议**: buildErrorResult 补 setRunId（或注释说明错误路径无 runId）；模块文档补多 schema 段。
- **信心水平**: 确定

### [AR-10] [P2] 先前 P2 批仍 open（状态确认，不重述）

- 06:55 multi-audit P2 清单经抽查仍 live：P2-01（`NopMetaSearchProcessor.java:56-66, 77-87` fail-closed 分支无 cause 无日志）、P2-04（`MetaQualityCheckpointScheduler.readRegisteredCron:253-261` 等 4 处无日志 catch）、P2-12（rawJdbcUrl 进 NopException params）——均无代码变更（git log 证实）。P2 不驱动 remediation plan，按 mission 规则记入 backlog 即可。

---

## 已核验安全 / 排除项

- R4.2 schemaPattern 注入面：`MetaQualityRuleExecutor.normalizeSchema`（:730-736）过 `validateIdentifier` 白名单（AR-01 修复），测试 `TestMetaTableProfilerSecurity` 钉死调用链——**安全**。
- R4.3 锁语义：per-checkpoint `ConcurrentHashMap.putIfAbsent` 非阻塞 + 最外层 finally 释放，覆盖 executor+autoScore+dispatchActions；cron 路径经 `executeScheduledCheckpoint` 委托同一 singleton BizModel bean（beans.xml `ioc:default`）→ 手动×cron 交叉面有真实保护；无 ABA（remove 先于后续 put）；锁 map 无泄漏（finally 恒释放）；多实例为文档化 baseline 排除。
- R4.3 新增测试非空壳：`testConcurrentExecuteCheckpointFailFast` 用真实线程池 + latch + fetchCallCount 断言 webhook 不重复投递；`testSameRunIdDuplicateWriteRejectedByUk` 区分性断言 UK 拒绝/异 runId 接受——**有效**。
- `0x7f.0.0.1` / `0177.0.0.1` 在 JDK 21+ 不可利用（严格十进制），非误报校准项（不重复报告）。
- NopMetaQualityResult 新列 checkpointId/runId propId 16/17 无冲突、xmeta/i18n/DDL/_add_tenant 再生成一致（R4.3 commit 全量核对）。

## 总评

nop-metadata 修复管线（MR1-MR4）工程质量本身很高——R4.3 幂等设计（进程内锁 + runId + 复合 UK 三层）实现正确、测试非空壳、错误码/DDL/文档同步到位。但本 mission 最值得关注的三个方向：

1. **SSRF 修复做了一半且未被发现（AR-01/AR-02）**：MA7.2-01 与 MA7.6-04 修的是"变体归一化"的同一家族，但 06:55 multi-audit 已精确指出残余缺口（十进制整数/前导零/短格式/八进制错配），jshell 全量可复现——这是两处真实可绕过的内网防护，且 12 小时后 mission 宣告"P1 全闭"。安全面"修复到位性"的验证链需要"修复提交后复测原绕过向量"这一步。
2. **审计输出未回流的治理断裂（AR-05）**：multi-audit 报告写了 6 个 P1 却从未进入 arm-index/roadmap，"产出即更新 arm-index"规范在 multi-audit 这条输入上失效；closure audit 的追踪矩阵没有这道输入，因此"0 untraceable"在事实上不成立。这是本 mission 最需要被记录为 lessons/检查项的教训——防止"审计报告写完 = 审计完成"。
3. **R4.2 可空列入 UK 的语义代价（AR-07）**：路径 A（保持可空）规避了 Oracle ''=NULL，代价是 MySQL/PG 的 NULL 不参与唯一约束——DB 层防重复防线整体移除，Java 层 find-then-insert 非原子。多 schema 支持与幂等保障在 null-schema 场景互相削弱，建议后续 plan 显式处理（原子 upsert 或空串占位）。

## 本次审查的盲区自评

- 未独立重跑测试（依赖 mission MV 的 867/0 记录与 R4.2/R4.3 commit 的测试清单，未自行验证新测试真实全绿）
- 未逐字段核对 xmeta published/insertable（R4.3 新列的 GraphQL 暴露面——checkpointId/runId 是否应对普通用户可写，属权限面，未深挖）
- 未检查 upgrade SQL 在真实存量库的迁移（三方言 ALTER 脚本仅静态阅读）
- 多 schema 语义对血缘/join 执行器的联动（queryTableData schema 解析路径）未追完整调用链
- 未审计 web/app 模块运行时配置对 R4.2/R4.3 的适配

## 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 5 | SSRF 归一化缺口 ×2（已知未修复）、血缘 API 契约漂移（已知未修复）、文档契约漂移三件合报（已知未修复）、治理追踪缺口 |
| P2 | 5 | DTO 字段语义错误、可空 UK 竞态退化、守卫过度拦截、DTO/文档一致性、先前 P2 批状态确认 |
| P3 | 0 | — |

---

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
