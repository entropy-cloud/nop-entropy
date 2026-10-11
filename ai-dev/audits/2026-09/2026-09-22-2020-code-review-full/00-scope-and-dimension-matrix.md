# 全仓代码审查：范围与审计维度矩阵（M0 产物）

> 日期：2026-09-22
> 来源 roadmap：`ai-dev/backlog/full-code-review-roadmap.md`
> 编写依据：`ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md`（步骤 1 维度矩阵 + 步骤 2 未闭包发现汇聚）
> 调研方式：9 个只读调研子 agent 分组扫描全部顶层模块组（2026-09-22），叠加 `ai-dev/audits/`（206 项历史审计）与 `ai-dev/backlog/`（25 份 roadmap）盘点。

图例：✅ 已审计且已闭包 ｜ ⚠️ 已审计但有未闭包发现/残留 ｜ ❓ 未审计（新审计工作项来源）｜ N/A 不适用

---

## 1. 模块组清单与复杂度评级

评级标准（`audit-remediation-roadmap-authoring-prompt.md` 步骤 4）：S = Java ≥ 200 或子模块 ≥ 5；A = Java 100–199；B = Java 50–99；C < 50。
> 注：下表评级为量化规则基础上的**人工调整结果**——纯装配/适配层（如 nop-spring、nop-integration）按实际复杂度下调，跨模块风险面大的（如 nop-file 安全面）不因 Java 数少而升批；评级仅用于审计分批粒度，不是硬性分级。

| 模块组 | main/test Java | 子模块 | 评级 | 备注 |
|---|---|---|---|---|
| nop-kernel | 2788 / 451 | 17 | **S** | xlang/core/commons/api-core 占大头（2026-10-03 实测，src/main+src/test/java 口径）；jq 已于 2026-09-28 迁出为顶层组 nop-jq |
| nop-persistence | ~757 / 209 | 13 | **S** | 复杂度集中在 orm+orm-eql（会话状态机 + 查询语言编译器） |
| nop-core-framework | 229 / 124 | 6 | **S** | boot/config/ioc/log/plugin/security（plugin 嵌套 api/manager/support）；CORE-02 defer 未修 |
| nop-ai | ~1189 / 649 | 18+ | **S** | v16 审计-修复闭环（50/50 done），审计后 89 提交有 plan 背书 |
| nop-service-framework | 505 / 96 | 6 | **S** | 量化达标；代码卫生极好，风险集中在权限 fail-open 模式 |
| nop-metadata | 452 / 168 | 8 | **S** | arm 全维度 + invariant loop 全闭环 |
| nop-auth | 438 / 122 | 9 | **S** | deep-audit + security AUTH-01..06 已闭环 |
| nop-batch | 294 / 42 | 15 | **S** | **S 级中唯一零专项审计** |
| nop-wf | 287 / 30 | 10 | **S** | WorkflowEngineImpl 1941 行（全仓最大手写）；测试比最低 |
| nop-task | 284 / 64 | 10 | **S** | 仅 security 第 5 组组合覆盖，无独立 deep-audit |
| nop-stream | 770 / 784 | 11 | **S** | 全仓审计最密（40+ 轮）；残留 P2 台账 ~15 项；nop-stream-sql 2026-10-02 入库（首审 2.8） |
| nop-job | 227 / 58 | 12 | **S** | 多轮审计；R10 新代码 17 发现未跟踪 |
| nop-report | 201 / 48 | 12 | **S** | **零专项审计**；XPT 展开/公式函数为表达式注入面 |
| nop-sys | 192 / 27 | 7 | **A（近 S）** | 零专项审计；check 系列发现锁过期判断反向 P0 |
| nop-format | 917 / 82 | — | **S** | 零审计；TODO=27 全仓最多 |
| nop-dyn | 142 / 20 | 8 | **A** | 仅 DYN-01 单点；DynCodeGen 是动态模型信任边界 |
| nop-rule | 102 / 14 | 8 | **A** | 零专项审计（仅 check 系列单轮覆盖） |
| nop-code | 261 / 137 | 15 | **A**（人工维持：量化已过 S 线，但审计已闭包、增量归 7.4） | invariant Cycle 1 关闭；1 项 gated 待人工确认；nop-code-lang-go/rust/csharp 2026-09-29 入库 |
| nop-lint + nop-treesitter | 216 / 204 | 6+1 | **S**（js/-maven-plugin/-graphql 入库后过 200 线；在建 W4–6） | nop-lint 6 子模块（core/java/nop/js/maven-plugin/graphql）+ nop-treesitter 单模块；新增面首审归 4.6 |
| nop-datav | 150 / 81 | — | **A** | P0/P1 闭包；P2×~55 backlog |
| nop-dev-tools | 155 / 21 | — | **B** | 零审计 |
| nop-frontend-support | 109 / 18 | — | **B** | 零审计 |
| nop-network | 216 / 53 | 6 | **B** | HTTP 客户端错误路径语义模糊 |
| nop-integration | 61 / 16 | 10 | **B** | 零审计 |
| nop-cluster | 74 / 10 | — | **B** | 零审计 |
| nop-rg | 43 / 13 | 5 | **B** | roadmap 全 done 但零审计；FFM/incubator 重度使用 |
| nop-search | 17 / 3 | — | **B** | LuceneSearchEngine 1130 行单类，零审计 |
| nop-jq | 71 / 7 | — | **B** | 2026-09-28 自 nop-kernel 迁出的扁平单模块；官方 jq 1.7.1 套件 430/430 为语义基线；首审 1.9 |
| nop-message（非 pulsar） | 30 / 14 | — | **B** | PulsarConsumeTask seek TODO 未实现 |
| nop-tcc / nop-retry | 53 / 8、52 / 7 | 各 8–9 | **B** | security NET 组覆盖过；修复状态未跟踪 |
| nop-credential | 46 / 25 | — | **B** | D1–D6 已闭环；W9–W16 二期 todo |
| nop-graph | 27 / 3 | 2 | **C** | **从未被独立审计**；13 算法类仅 3 个测试文件 |
| nop-spring / nop-quarkus | 29 / 1、21 / 4 | 8、7 | **C** | nop-spring 测试真空（29 main/1 test） |
| nop-runner / nop-autotest | 37 / 12、41 / 5 | — | **C** | CLI 双入口；测试基建自身覆盖低 |
| nop-file | 18 / 3 | 8 | **C** | FILE-01..02 已覆盖 |
| nop-entropy-e2e | 0 Java（~44 Playwright TS） | — | **C** | 零审计；Node/Playwright 测试资产（auth/code/job/web），归属裁定见 1.8 |
| nop-refactor | 33 / 16 | 3 | **C** | 在建 AI-first 代码修改工具链（core/java/graphql）；首审 1.9 |
| nop-bytecode | 27 / 13 | — | **C** | 字节码分析通道（report-only 发现流）；首审 1.10 |
| nop-duckdb | 6 / 22 | — | **C** | 进程内分析执行层（扁平单模块）；首审 1.10 |
| nop-migration / nop-benchmark / nop-utils | 207 / 25 | 16（migration 1、benchmark 5、utils 10） | **A**（量化已过 S 线，人工下调：迁移工具/基准/工具库性质） | 零专项审计；nop-record 非顶层组（实位于 nop-format/nop-record、nop-format/nop-record-netty、nop-kernel/nop-record-mapping）；nop-benchmark 含 nop-benchmark-stream（16 main JMH，2026-09-26） |

---

## 2. 审计维度 × 模块组覆盖矩阵

维度列：①结构/依赖/边界 ②模型层（ORM/XDSL/生成管线） ③服务层（BizModel/IoC/GraphQL/API） ④安全与权限 ⑤运行时正确性（事务/异步/状态机/错误处理） ⑥工程质量（测试/巨型文件/风格） ⑦文档与契约一致性 ⑧反模式专项（空壳/静默跳过/接线）

| 模块组 | ① | ② | ③ | ④ | ⑤ | ⑥ | ⑦ | ⑧ |
|---|---|---|---|---|---|---|---|---|
| nop-kernel | ⚠️（1.3 结构深审） | ⚠️（1.3；1.2 执行链） | ⚠️（1.4 api-core/commons 支撑面） | ⚠️（1.3 VFS/反射/XNode/类型体系） | ⚠️（1.2 执行后端设计 review r5 PASS） | ⚠️（4.2 测试比核定） | ⚠️（4.1/6.2 扫描覆盖） | ⚠️（6.2 吞异常清单） |
| nop-core-framework | ⚠️（1.6 六模块结构边界） | N/A | ⚠️（2.7 IoC 全域扫描：211 @Inject 零违规） | ⚠️（1.6 复核：CORE-01..03 标定） | ⚠️（1.6 boot/config/log 面） | ⚠️（4.2 抽样） | ⚠️（5.1/5.2 owner doc 核对） | ⚠️（6.2 扫描覆盖） |
| nop-persistence | ⚠️（2.3 dao/migration/dbtool 深审） | ⚠️（2.2 深审后 P0 已修，P1/P2 闭账） | ⚠️（2.2 eql 深审 60 发现） | ⚠️（2.7 注入全域扫描） | ⚠️（2.3/2.4 复核：check2 P0 全 already-fixed） | ⚠️（4.2 抽样） | ⚠️（4.1/6.2 扫描覆盖） | ⚠️（6.2 吞异常清单） |
| nop-service-framework | ⚠️（1.7 分层与边界合规） | N/A | ⚠️（2.6/2.7 BizModel 规范扫描） | ⚠️（2.6 fail-open 终裁+R1.12 双配置键收口） | ⚠️（R1.10 事务装饰器） | ⚠️（4.3 巨型文件台账） | ⚠️（4.1 扫描） | ⚠️（6.2 扫描） |
| 业务样板（auth/job/task/wf） | ⚠️（3.1/3.2/7.5 深审+增量） | ⚠️ | ⚠️ | ⚠️（3.10 增量闭环；WF-01..03 标定+R1.13 收口） | ⚠️（3.2 复核：continuation-skip already-fixed） | ⚠️（4.2 核定） | ⚠️（4.1 扫描） | ⚠️（6.2 扫描） |
| 可复用业务（sys/report/rule/batch/dyn/file/retry/tcc/metadata） | ⚠️（3.3–3.8 逐模块深审） | ⚠️（2.5 全仓模型扫描；metadata 闭环） | ⚠️ | ⚠️（FILE/DYN 单点已由 R1.19/R1.20 收口；batch/report/sys/rule 深审完成） | ⚠️（sys 锁反向 P0/tcc 误标 P0 已修） | ⚠️ | ⚠️（4.1/6.2 扫描覆盖） | ⚠️（6.1/6.2） |
| nop-ai | ✅ | ✅ | ✅ | ✅（AI-01..04 增量已覆盖） | ✅ | ⚠️（3 文件回涨破千行） | ✅ | ⚠️（3 处 ignored 复核级） |
| nop-stream | ✅ | ✅ | ✅ | ✅ | ⚠️（backlog P2×~15） | ✅ | ✅ | ⚠️ |
| nop-code | ✅ | ✅ | ⚠️（api/dao 0 测试） | ⚠️（Phase 9 @Auth gated） | ✅ | ⚠️ | ✅ | ⚠️ |
| nop-graph | ⚠️（6.7/6.8 溯源标定 P2-6.7-005..008） | N/A | N/A | N/A | ⚠️（6.7 PathQuery/Bfs/Leiden 终裁） | ⚠️（4.2/6.7 测试面） | ⚠️（4.1/6.2 扫描覆盖） | ⚠️（6.2/4.1） |
| nop-rg | ⚠️（6.8 批次 d 标定） | N/A | N/A | ⚠️（4.1 错误码面） | ⚠️（6.8） | ⚠️（4.2 抽样） | ⚠️（6.8） | ⚠️（6.2/4.1） |
| nop-lint + nop-treesitter | ⚠️（4.6 增量首审） | ⚠️（4.6 规则白名单 fail-closed） | N/A | N/A | ⚠️（4.6 js 适配面） | ⚠️（4.3 GLRParser 台账） | ⚠️（4.6/5.2 设计 vs W4–6 裁定） | ⚠️（6.1 lint-nop 在建空壳合法裁定） |
| 集成运行时（spring/quarkus/network/integration） | ⚠️（1.8 集成外围+归属裁定） | N/A | ⚠️（3.9 网关/HTTP 客户端深审；R1.40 适配层测试） | ⚠️（3.9 深审后 P0 全 already-fixed） | ⚠️（3.9/R1.25） | ⚠️（4.2/R1.40 适配层 22+13 用例） | ⚠️（4.1 扫描） | ⚠️（6.2 HTTP 宽 catch 清单） |
| runner / autotest / demo | ⚠️（4.5 深审+R1.27 数据面） | N/A | N/A | ⚠️（R1.27 autotest 数据面三修复） | ⚠️（4.2/R1.27） | ⚠️（4.2 zero-test 清单核定） | ⚠️（4.5 CLI 双入口） | ⚠️（4.5 EmptyMain 死占位裁定） |
| nop-entropy-e2e | N/A | N/A | N/A | ⚠️（1.8 归属裁定） | N/A | ⚠️（4.2 zero-test 口径） | ⚠️（5.1 路由核对） | ⚠️（6.1/6.2 扫描） |
| 其他（format/record/utils/search/message/cluster/dev-tools/frontend-support/migration/benchmark） | ⚠️（4.4/4.5 深审；R1.31/R1.37-R1.39 修复） | ⚠️（R1.31 record readCollection 判别） | ⚠️（R1.39 mermaid 生成器-文法对齐 13/13） | ⚠️（4.4 search 深审+R1.28 fail-closed） | ⚠️（4.4/4.5 深审） | ⚠️（4.4 format TODO 三类评定；4.3 Lucene 台账） | ⚠️（5.1/5.2 文档核对） | ⚠️（6.1/6.2 扫描） |
| nop-datav | ✅ | ✅ | ⚠️ | ✅ | ✅ | ⚠️（P2×~55 backlog） | ✅ | ⚠️ |
| nop-credential | ✅ | ✅ | ⚠️ | ✅（D1–D6 闭环） | ⚠️（RESP3 P0 关联未修） | ✅ | ✅ | ✅ |
| nop-jq（v6 新增） | ⚠️（1.9 首审+R1.3 资源护栏） | N/A | N/A | ⚠️（1.9 四段契约/信任边界） | ⚠️（1.9 官方套件 430/430 基线+R1.3） | ⚠️（1.9 569 tests） | ⚠️（4.1/6.2 扫描覆盖） | ⚠️（6.1/6.2） |
| nop-refactor（v6 新增） | ⚠️（1.9 四段契约首审） | N/A | ⚠️（1.9 Refactor__ GraphQL 面单实现+cap 门） | ⚠️（1.9 EditPlanApplier 信任边界） | ⚠️（1.9） | ⚠️（4.2 抽样覆盖） | ⚠️（4.1/6.2） | ⚠️（6.1/6.2） |
| nop-bytecode（v6 新增） | ⚠️（1.10 首审+R1.4 双缺陷修复） | N/A | N/A | N/A（report-only，仅读自家构建产物，无鉴权/求值面） | ⚠️（1.10 exit 契约+ObligationToken+R1.4） | ⚠️（4.2 口径） | ⚠️（4.1/6.2 扫描） | ⚠️（6.2 扫描） |
| nop-duckdb（v6 新增） | ⚠️（1.10 首审+R1.4） | N/A | N/A | ⚠️（1.10 文件读写面；3.11 兜底覆盖） | ⚠️（1.10 单写者/配置指纹） | ⚠️（4.2 口径） | ⚠️（4.1/6.2） | ⚠️（6.1/6.2） |

`❓` 格终态 **0 格**（基线 79 格 = 基线 18 行 55 格 + v6 新组 4 行 24 格，Wave 1 MA1–MA7 已全部消化；**2026-10-10 V.3 终态回填**——原 ❓ 格按覆盖工作项映射为 ⚠️（审计完成且有残留发现/watch），映射指针见各行括注；残留均为 P2/P3 watch 级，P0/P1 已全部修复闭环（见 index P0/P1 表与 MR 节）。`⚠️` 格是复核/标定类工作项（MA6.4、MA7）的来源。

### v6 新增子模块登记（0.5）

> 编制基线（2026-09-22）当日或之后入库、未入 v5 任何工作项覆盖的新子模块；归属工作项与 roadmap Details 一致。

| 新增子模块 | 入库日期 | 所属模块组（§1 行） | 审计归属 |
|---|---|---|---|
| nop-stream-sql | 2026-10-02 | nop-stream（11 子模块） | 2.8 首审 |
| nop-code-lang-go | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
| nop-code-lang-rust | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
| nop-code-lang-csharp | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
| nop-lint-js | 2026-09-22 | nop-lint（6 子模块） | 4.6 首审 |
| nop-lint-maven-plugin | 2026-09-24 | nop-lint（6 子模块） | 4.6 首审 |
| nop-lint-graphql | 2026-09-25 | nop-lint（6 子模块） | 4.6 首审 |
| nop-benchmark-stream | 2026-09-26 | nop-benchmark（其他行） | 4.2 测试有效性（JMH 基准为测试基建、非生产代码） |

---

## 3. 已有审计覆盖摘要（已闭包，避免重复审计）

| 对象 | 覆盖情况 | 证据 |
|---|---|---|
| nop-ai（18 子模块） | arm 50/50 done；13 个 P2/P3 successor 批次全 closed；MV open=0；2026-08 后 89 提交均有 plan+closure audit；security AI-01..04 覆盖增量安全面 | `ai-dev/backlog/audit-remediation-roadmap.md`（v16）、`ai-dev/audits/arm-index.md` |
| nop-metadata | arm MA1–MA7 全收口（P0=0）；invariant loop 3 Cycle 稳态 | `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`、arm-unclosed-findings-nop-metadata.md |
| nop-stream | 40+ 轮 deep/adversarial/invariant；production/productization/independent-audit 全 done | `ai-dev/backlog/nop-stream-*roadmap*.md` |
| nop-datav | 主 roadmap + 5 篇审计，P0/P1 闭包 | `ai-dev/backlog/nop-datav-audit-followups.md` |
| nop-code | deep+adversarial 13 轮至 2026-06-06f；invariant Cycle 1 关闭 | `ai-dev/backlog/nop-code-invariant-loop-roadmap.md` |
| nop-auth / nop-credential | deep-audit D1–D7 / D1–D6 + phase2 closure + security AUTH/CRED 族 | `ai-dev/audits/2026-08/`、`ai-dev/audits/security-audit/` |
| nop-job | 2026-05~08 多轮；R10（2026-06-19）17 发现 | `ai-dev/audits/2026-06-19-0931-*`、`340-nop-job-dao-deep-audit` |
| xlang 执行后端 | 设计 review 5 轮（r5 PASS）+ workitem audit | `ai-dev/audits/xlang-execution-optimization/` |
| 全仓安全（单维） | security-audit 13/14 done；8 族 36 报告 | `ai-dev/audits/security-audit/`、`ai-dev/backlog/security-audit-roadmap.md` |
| 全仓 41 模块组（单轮快扫） | check 系列 54 报告，930 发现（P0=36） | `ai-dev/audits/check/`（SUMMARY+ROADMAP） |

---

## 4. 未闭包发现清单（步骤 2 产物，修复波次输入）

> 处置原则：本清单只登记与**去向**。已确认 live defect（P0/P1）在 Wave 1 对应审计工作项中**先复核仍 live**，再经 R1.0 展开器转入修复工作项；不得在 roadmap 中直接复制发现内容。

| 来源 | 对象 | 未闭包项 | 去向 |
|---|---|---|---|
| `ai-dev/audits/check/`（2026-08-19~21） | 全仓 41 模块组 | 930 发现（**P0=36**，含 9 条安全 P0；P1=209；P2=355；P3=330）；"静默失败"为最大族群；无修复跟踪 | MA6.4–6.9 分层复核标定（P0 全量 → P1 四批抽样 → P2/P3 归档）→ R1.0 展开修复 |
| `ai-dev/backlog/security-audit-roadmap.md` item 14 | 全仓 | successor-deferred **MEDIUM×14**（F-API1-1、F-API4-1、F-WF-01-*、F-WF-02-1、F-AI1-1、F-AI2-2、F-N1-1/N1-2/N2-2/N3-2、F-F2-1、F-D1-1 等） | MA3.11 现状标定 → R1.0 |
| `ai-dev/audits/check2/`（2026-08-23） | nop-persistence | db-migration 2 P0（9/17 change 解析 CCE、数据变更不可用）；orm-eql 1 P0（集合操作符 join 放大）；nosql 1 P0（RESP3 RateLimiter 必抛）+ P1×3 | MA2.2/2.3/2.4 复核 → R1.0 |
| `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Follow-up Backlog | nop-stream | P2 ~15（abort 路径泄漏、evictor 永不驱逐、pane 键错位、flow DSL 142 处裸 IAE 等） | MA7.2 标定 |
| `ai-dev/audits/arm-unclosed-findings-nop-metadata.md` | nop-metadata | P1×1 live（OrmModelImporter:58,68 currentTimeMillis）+ watch×2 + P2×16 | MA7.3 |
| `ai-dev/audits/2026-06-19-0931-*` R10 | nop-job | 5 P1 / 8 P2 / 4 P3 修复无索引跟踪 | MA7.5 |
| `ai-dev/backlog/nop-datav-audit-followups.md` #18–72 | nop-datav | P2×~55 backlog | MA7.6 抽样 |
| `ai-dev/backlog/nop-code-invariant-loop-roadmap.md` | nop-code | Phase 9 @Auth 1 项 gated 待人工确认 | MA7.4 |
| `ai-dev/backlog/nop-lint-roadmap.md` | nop-lint | W4–6 共 21/43 todo（在建，非缺陷） | 不入本 roadmap 修正范围（属功能开发 roadmap） |
| `ai-dev/backlog/nop-jq-roadmap.md` / `nop-credential-mfa-roadmap.md` / `nop-ai-agent-autonomous-execution-improvement-roadmap.md` | jq / credential / ai-agent | 功能开发类 todo（13 / 9 / 20 项） | 不入本 roadmap（各自 roadmap 负责） |

**边界裁定**：功能开发类 todo（nop-lint/jq/credential-mfa/ai-agent 自治改进）不是缺陷，不属于"代码审查修正"范围；本 roadmap 只收口**缺陷修复与质量收敛**。已知 P2 裁决通道：审计报告显式开辟例外时，R1.0 展开器逐项裁决（修 / deferred 带 Why Not Blocking Closure + Successor），裁决结果写回 roadmap 行。

---

## 5. 跨组风险热点 Top 10（调研证据）

| # | 热点 | 证据 |
|---|---|---|
| 1 | check 系列 36 个 P0 无修复跟踪（含认证绕过、路径遍历、账号接管 9 条安全 P0） | `ai-dev/audits/check/SUMMARY.md` |
| 2 | nop-db-migration 核心功能不可用（9/17 change 解析 CCE、DDL 方言硬编码） | `ai-dev/audits/check2/db-migration.md`、`nop-db-migration/.../executor/` |
| 3 | 权限 fail-open 模式三处同构（checker==null 即放行，无启动期 fail-fast） | `GraphQLActionAuthChecker.java:122-124`、`ObjMetaBasedValidator.java:332-333`、`DefaultBizAuthChecker.java:51-63` |
| 4 | nop-kernel 从未被整体审计：XLangASTOptimizer 3028 行无直接测试、JsPromise:117 吞 Throwable | `nop-kernel/nop-xlang/` |
| 5 | WorkflowEngineImpl 1941 行 + WF-01..03（check-start-auth 未调用等）未闭环 | `nop-wf-core/.../WorkflowEngineImpl.java:1193`、security WF 族 |
| 6 | nop-batch S 级（15 子模块）零审计；nop-report/nop-sys/nop-rule 零审计 | 本矩阵 §1 |
| 7 | nop-nosql RESP3 P0 + PubSub 串投 + codec 不对称（影响 nop-auth MFA） | `ai-dev/audits/check2/nosql-cdc.md` |
| 8 | EQL 编译器 P0 + 221 类仅 8 测试 | `ai-dev/audits/check2/nop-orm-eql.md` |
| 9 | 巨型手写文件带：StringHelper 4932、XNode 2829、ExecToJavaTranslator 2656、CrudBizModel 2272、JobCoordinator 2366、GLRParser 2061、CodeIndexService 2130、ReActAgentExecutor 1417 | 各组调研报告 |
| 10 | zero-test 面扩大：nop-code-api(64)/nop-code-dao(35)/nop-spring(29)/nop-autotest(41)/nop-biz-auth-api(28)/nop-benchmark(69) | 各组调研报告 |

---

## 6. check 系列 P1 抽样分批权威归属（roadmap 6.5–6.8）

> 本节是 6.5–6.8 四批成员的**唯一权威定义**；check 系列报告按模块名映射到批次成员，四批并集 = 本矩阵 §2 全部 18 个基线行（v6 新增 4 行不参与，见下方注记）。metadata 唯一归属批次 c，search 唯一归属批次 d——杜绝双认领/漏认领。

> **v6 基线刷新注记**：check 系列（2026-08-19~21）早于全部 v6 新增组/子模块入库，4 个新顶层组（nop-jq/nop-refactor/nop-bytecode/nop-duckdb）无 check 系列发现，不参与 6.5–6.8 的 P1 抽样分批。

| 批次 | 成员（§2 行名） | 对应 roadmap 工作项 |
|---|---|---|
| a | nop-kernel、nop-core-framework、nop-persistence | 6.5 |
| b | nop-service-framework、业务样板（auth/job/task/wf）、可复用业务（sys/report/rule/batch/dyn/file/retry/tcc） | 6.6 |
| c | nop-ai、nop-stream、nop-code、nop-graph、nop-credential、nop-datav、nop-metadata | 6.7 |
| d | 集成运行时（spring/quarkus/network/integration）、runner/autotest/demo、nop-entropy-e2e、nop-rg、nop-lint + nop-treesitter、其他（format/record/utils/search/message/cluster/dev-tools/frontend-support/migration/benchmark） | 6.8 |
