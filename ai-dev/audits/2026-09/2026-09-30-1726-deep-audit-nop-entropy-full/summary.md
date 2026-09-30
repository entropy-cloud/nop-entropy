# 深度审核汇总报告 —— nop-entropy 全仓代码质量

## 基本信息

- **审核模块**: nop-entropy 全仓（除 nop-stream 外全部模块组，共 12 组）
- **审核日期**: 2026-09-30（17:26–20:20）
- **方法论**: `ai-dev/skills/deep-audit-prompts.md`（共享前缀 + 维度正文 + P0–P3 判级 + 独立复核）
- **执行方式**: 主 agent 按模块分组派发 12 个首轮审计子代理（各组 4–5 个维度），再派发 4 个独立复核子代理对全部 P1/P2 逐条复核
- **目标范围**: 手写 main 代码 + 源模型（model/*.orm.xml、xdef、xbiz、xwf、beans.xml）；排除 `target/`、`_` 前缀生成产物、`.m2-repo-2275/`、`_tmp/`
- **nop-stream 不在本轮范围**: 该模块 2026-09-27 刚完成 r2 深度审计（见 `module-groups.md` 流处理引擎段落引用），本轮不重复

## 执行统计

| 组 | 报告文件 | 覆盖模块 | 初审条目 | P1 | P2 | P3 | 复核判定 |
|---|---|---|---|---|---|---|---|
| G1 | [01](01-nop-core-commons-api.md) | nop-core / nop-commons / nop-api-core | 9 | 0 | 3 | 6 | 全保留 |
| G2 | [02](02-nop-kernel-xlang.md) | nop-xlang / nop-xdefs / nop-codegen 等 XLang 体系 | 10 | 1 | 3 | 6 | 全保留 |
| G3 | [03](03-nop-ai.md) | nop-ai 全部子模块 | 8 | 0 | 4 | 4 | 驳回 1、保留 3 |
| G4 | [04](04-nop-persistence-core-framework.md) | nop-persistence + nop-core-framework | 10 | 0 | 2 | 8 | 全保留 |
| G5 | [05](05-nop-service-framework.md) | nop-service-framework（biz/graphql/rpc） | 10 | 0 | 5 | 5 | 全保留 |
| G6 | [06](06-nop-auth-wf.md) | nop-auth + nop-wf | 11 | 1 | 4 | 6 | 全保留 |
| G7 | [07](07-job-task-batch-retry-tcc.md) | nop-job/task/batch/retry/tcc | 9 | 2 | 5 | 2 | 全保留 |
| G8 | [08](08-nop-metadata.md) | nop-metadata | 4 | 0 | 1 | 3 | 保留 |
| G9 | [09](09-sys-report-rule-dyn-file.md) | nop-sys/report/rule/dyn/file | 13 | 1 | 8 | 4 | 全保留 |
| G10 | [10](10-lint-refactor-jq.md) | nop-lint + nop-refactor + nop-jq | 12 | 0 | 2 | 10 | 全保留 |
| G11 | [11](11-code-graph.md) | nop-code + nop-graph | 13 | 2 | 6 | 5 | 全保留（1 处行号勘误） |
| G12 | [12](12-network-integration-runtime.md) | network/integration/runner/spring/quarkus/autotest | 17 | 1 | 8 | 8 | 降级 1（P1→P2）、余保留 |
| **合计** | | | **126** | **8** | **51** | **67** | 见下 |

**复核统计**: 4 个独立复核代理（R1–R4，fresh eyes）对全部 8 条 P1 + 51 条 P2 共 59 条逐条复核，P3 按口径不做独立复核（报告中标注"未复核"）。

- 保留：57 条（其中 2 条附证据修正/勘误，判定不变）
- 降级：1 条 —— [G12-13-01] OkHttp trust-all P1→P2（代码缺陷属实，但 nop-http-client-okhttp 在仓内零接线零调用方、无激活路径）
- 驳回：1 条 —— [G3-13-01] SkillExecutor 路径逃逸（VFS `checkNormalVirtualPath` 对 `..` 段 fail-closed，首轮把 `DeltaResourceStore.getChildren` 的归一化行为误安到 `getResource` 链上）

**最终口径**: **P0 × 0，P1 × 7，P2 × 51，P3 × 67**（保留 125 条 + 驳回 1 条 = 初审 126 条）

## 按严重程度分布（复核后）

| 严重程度 | 数量 | 主要类别 |
|---|---|---|
| P0 | 0 | —（安全敏感面经多轮加固后本轮无 P0） |
| P1 | 7 | 审批流语义 1、数据完整性/并发 2、算法正确性 1、bean 装配 1、能力宣称失实 1、XLang 语义 1 |
| P2 | 51 | 安全权限 8、并发/资源 7、错误码漂移 ~28 处归 4 组、数据模型/索引 5、静默失效 DSL/死代码 6、测试缺口 6、其他 12 |
| P3 | 67 | 命名/拼写、观测口径、低价值测试、局部冗余（未独立复核） |

## 关键发现摘要

### P1 发现（7 条，全部经独立复核保留）

| 编号 | 文件 | 摘要 |
|---|---|---|
| [G6-22-01] | `nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf` | 仓库 canonical 审批流模板的 `*end` listener 不判定结束原因：审批人 disagree 时业务单据仍被 `approve()`——驳回即通过（MA7.6-01 同构缺陷；复核确认 nop-metadata 同类 3 处已修而 nop-wf 模板未回改，触发链逐行核实） |
| [G7-04-01] | `nop-batch/model/nop-batch.orm.xml` + `DaoBatchStateStore.java` | taskKey 无 unique-key/索引，启动防重为 check-then-act + 无条件 update；复核证实 UPDATE WHERE 无版本条件、无 affected-rows 检查，并发双实例可同时 RUNNING，无 DB 层兜底 |
| [G7-14-01] | `nop-retry` 幂等键生命周期错配 | `UK_RETRY_IDEMPOTENT_ID` 全局唯一但查重只查 PENDING/RETRYING，COMPLETED/SUSPENDED 记录永久占用幂等键，重提交撞裸 DB 约束冲突，blockStrategy 从未被咨询 |
| [G9-04-01] | `nop-file` FILE_HASH 列 | 全链路无任何写入者（唯一赋值点复制恒 null），而 `reusable-modules-overview.md` 宣传"Hash 去重"能力——宣称与行为不符，去重从不发生 |
| [G11-15-01] | `nop-graph-core/TarjanSCC.java:80-110` | 迭代版 Tarjan 恢复帧只合并最后一个子节点的 lowLink，特定图形状下 SCC 被错误拆分；决定性证据是 `CodeGraphService.tarjanSCC` 的自带修复版带解释该缺陷的注释，但库版本未回改（复核独立手工 trace 复现） |
| [G11-03-01] | `nop-code-service/app-service.beans.xml` | Go/C#/Rust 三个 ILanguageAdapter bean 从未被 import（NopIoC 仅自动装载 `app-*` beans），merged beans 实测只含 Java/Python/TypeScript——三语言索引静默失效，与 pom 六语言依赖直接矛盾 |
| [G2-10-01] | `nop-xlang/.../JsPromise.java` | XScript 全局 `Promise` 兼容层错误路径三处偏离 JS 语义：executor 抛错被吞致 promise 永不 settle（`.get()` 永久悬挂）、rejected promise 无 onR 时 rejection 被当正常值、finally 回调抛错被 `catch { // ignore }` 吞掉 |

### P2 亮点（按主题，全部经复核保留）

**安全与权限**：
- [G5-13-01] DevDocBizModel/DevToolBizModel 全部操作无 `@Auth`，`nop.debug` 部署下任意登录用户可导出 IoC 容器 XML、全部配置值、全量 schema（同包 DevStatBizModel 已整改，此两处漏改）
- [G5-13-02] `Sys__cancel` 公开 `@BizQuery` 无用户归属校验，且 reqId 撞车时主动取消先到请求（跨用户请求干扰面）
- [G12-13-01]（降级）OkHttp `useSsl=true` 默认安装 trust-all TrustManager 且零告警，与 Jdk 客户端语义相反——缺陷属实但模块仓内零接线
- [G12-13-02] SftpClient 硬编码 `StrictHostKeyChecking=no`；[G12-13-03] 飞书扫码回调直接信任客户端自声明 open_id
- [G8-13-01] custom_sql 沙箱黑名单族缺口（裸 `INTO` / `PG_TERMINATE_BACKEND`），兄弟校验器已收口
- [G3-13-02] BashExecutor `BASH_FUNC_` 黑名单条目精确匹配永不命中（死条目）
- [G3-07-01] NopAiRagBizModel 全仓唯一 `/` 前缀 bizObjName + 写操作无 `@Auth`

**并发与资源**：
- [G1-14-01] TaskExecutionGraph 幻影依赖静默跳过 + whenComplete 内异常被 CompletableFuture 丢弃（聚合 future 永不完成；IoC 并发启动门控下可达）
- [G4-14-01] BeanScopeImpl.close() 按哈希序销毁单例，启动已有拓扑序却未用于逆序销毁
- [G5-14-01] 网关流式链路非 demand-driven，慢消费者 + 长 LLM 流存在无界缓冲内存膨胀
- [G1-14-02] ICache.putIfAbsent javadoc 要求原子但默认实现 check-then-act，与 MapCache/JavaxCache 语义分裂

**错误处理纪律（伪错误码漂移，合计 ~28 处归 4 组）**：
- [G1-09-01] nop-core 12 文件 15 处、[G4-09-01] 框架核心 6 处、[G5-09-01] 服务框架 4 处、[G2-09-01] nop-xlang 6 处——以裸 IAE/ISE 抛 `nop.err.*` 风格字符串，码无 ErrorCode define、无 i18n 条目，绕过平台错误通道

**静默失效面**：
- [G10-15-01] nop-lint PatternMatcher bare `$`/`$$` 元变量永不匹配（规则静默零命中）；[G10-15-02] 规则 DSL 的 `files:`/`options:`/`settings:` 被解析但引擎零消费
- [G9-07-02]/[G7-07-01] 重命名后孤儿三件套残留（NopSysMakerCheckerRecord、NopBatchTaskState，含 _gen 与模板文件）

**测试缺口**：
- [G9-16-01] nop-file-dao 零测试（路径穿越防御等近期修复无回归锁）；[G12-16-02] nop-autotest 核心机制几乎零自测；[G2-16-01] MarkdownCodeBlockParser（LLM 响应解析唯一入口）零测试；[G7-16-01] DaoBatchStateStore 零测试（与两个 P1 一一对应）

## 正面结论（各组的"核对过不报"汇总）

- **无 P0**：SQL 注入面（字段白名单双层校验、分页参数化）、SSRF 主机规范化（nop-metadata 全套在位）、GraphQL selection 加固、`/q/`-`/r/` 同引擎同鉴权、登录/MFA/限流 fail-closed、nop-tcc 补偿守卫、飞书凭证 fail-closed 框架——均核实为收敛状态
- `@Inject private` 全仓 0 处；main 代码 System.out 仅个位数有意输出（CLI 边界豁免）
- nop-jq 官方 jq 1.7.1 测试套件宣称核实为真（430 用例在仓，421 ok + 9 fail 为已知基线）
- nop-lint/refactor/jq 新模块工程纪律属全仓第一梯队（依赖图全绿、main 裸异常 28 处全 P3）

## 总评

平台主干（kernel/service-framework/persistence）经多轮审计修复后整体纪律良好，本轮无 P0，P1 集中在三条线上：**审批流语义**（nop-wf canonical 模板未回改已知的 MA7.6-01 缺陷——教训已修但模板层未同步，是典型的"修复未闭环"回归源）、**数据完整性**（nop-batch/nop-retry 的唯一键与生命周期设计缺口，两者都属"单实例正确、并发/重放下失效"）、**半接线特性**（nop-code 三语言 bean 未注册、nop-file 去重无写入者、TarjanSCC 修复未回改库版本——共性是"功能宣称/依赖声明与运行时行为脱节"）。P2 层面伪错误码漂移（~28 处）说明两档错误处理策略在框架核心的执行有一致的局部漏洞，适合一次性批量迁移。新建工具链模块（lint/refactor/jq）与 nop-ai 高风险面质量最好；可复用业务模块（G9）与运行时外围（G12）是发现密度最高的两区，主因是 ORM 索引/约束缺失与外部协议边界（TLS/SSH/回调）的默认值不够保守。

## 优先修复建议

1. **P1 修复波次（7 项）**：按 `ai-dev/audits/README.md` 约定创建 plan 引用本审计为 baseline。建议分三批：① nop-wf 审批语义（G6-22-01，连同关联 P2 G6-22-02/03/04 一起修，对照 nop-metadata 已修范式）；② 数据完整性（G7-04-01 + G7-14-01，加唯一约束 + 索引 + 状态过滤修正，需 ORM 迁移）；③ 正确性四项（G2-10-01、G11-15-01、G11-03-01、G9-04-01）。
2. **安全 P2 批次**：G5-13-01/02（对齐 DevStatBizModel 的 `@Auth` 整改范式）、G12-13-01/02/03（trust-all/SFTP/回调验证）、G8-13-01（黑名单族对齐）。
3. **伪错误码批量迁移**：G1/G2/G4/G5 四组合并一个工作项，统一迁到各自 Errors 类的 `ErrorCode.define()`。
4. **测试补齐批次**：与上述修复同 plan 落地（G7 两个 P1 均无测试保护，修复时必须带回归锁）。

## 本次审核盲区自评

1. **P3 × 67 未做独立复核**——首轮子代理自判，可能有少量误报/判级偏差残留；使用 P3 清单时建议抽查。
2. **维度覆盖为各组 4–5 个维度的加权子集**，非 22 维度全量：维度 04（ORM）仅在业务模块组执行、维度 22（工作流）仅覆盖 nop-wf（nop-metadata 的 xwf 未重审，其 3 处 `*end` listener 复核时已顺带确认已修）、维度 05/06/11/18–20 大多未单独执行。
3. **大模块为抽样深读而非全量**：nop-kernel ~3000 文件按包优先级抽样；nop-biz/graphql 引擎核心深读、外围抽样；nop-task-core 仅抽样（近期已多轮审计，42 个测试文件）。
4. **复核中发现的附带线索**（未立项，已写入各报告复核节）：`_NopBatchTaskState.json` 模板属孤儿残留链一环（G7-07-01 修复时需一并删除）；`CodeCacheManager.addToSymbolTableCache` 与 [G11-15-02] 同源竞态；nop-http-client-okhttp 整模块"已发布但零接线"与 [G11-03-02] 休眠子系统同型，建议平台统一决策休眠特性的处理方式。
5. 12 组审计代理间的范围边界（如 nop-ai-gateway 与 nop-service-framework 的 gateway 部分）以各组报告"审计范围"节为准，跨组接缝处存在低密度重复检查。
