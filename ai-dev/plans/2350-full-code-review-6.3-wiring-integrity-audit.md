# 2350 full-code-review 6.3 接线完整性抽样审计（四链路入口→出口）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.3（MA6）；Skill 锚沿袭 roadmap 行（`deep-audit-prompts.md` 接线/端到端路径方法——**经审查该 skill 无此专节，真实方法锚 = plan guide Rule #22 端到端验证/#23 接线验证**；roadmap 行锚为虚锚，本 plan 以 guide 为准，回修归 V.3 文本一致性核对）
> Related: Roadmap Details：「四条关键链路入口→出口追踪（codegen、db-migration 执行、IoC 启动、GraphQL 请求），验证无断链」。**范围调停（草审 m-7）**：标题「抽样」沿 roadmap，Goals「全量」＝**框架机制边全量**（每链节点清单 ~5-10 边）；逐 bean 实例装配归 2.7 已闭包、逐模板归 1.4 已闭包，显式排除。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_b54f7afc 逐项实测勘正）：四链核心模块与入口类——① **codegen 全链**：调用方 = exec-maven-plugin（根 pom.xml:388、nop-kernel/pom.xml:364）+ 各 `*-codegen` 子模块（20 个 Maven 模块目录实存，其中 19 个绑定 exec-maven-plugin〔nop-auth-codegen pom:64〕，引擎模块 nop-kernel/nop-codegen 自身不绑定）→ `nop-kernel/nop-codegen/.../task/CodeGenTask.java`（main:139）→ XCodeGenerator → TemplateFileGenerator → renderTemplate → 产物写盘（`_` 前缀）；1.4 已预画六站连通（模板链 16/16 抽样、162 模板 = .xgen 120+.xrun 26+静态 16）。② **db-migration 执行链**：migration 模型 → `MigrationExecutor.registerExecutor:34` 注册 + 分派 null 检查 :77 → **16 个具体执行器**（executor/ 18 文件 = 16 执行器 + IChangeExecutor 接口 + DdlSyntax 语法助手；**17 = change 类型数**，ExecuteMarkChange 无专属执行器——分派路径单列核对，2.3 :120 已有同口径勘正）→ DDL 渲染 → recordMigration；2.3 已逐 executor 语义验证 + JdbcBatcher 实跑；`TestChangeExecutors.java` 在场。**〔6.3 复核勘正 A2-03：主链分派实走 MigrationEngine.executeChange:293-307（null 检查 fail-loud），MigrationExecutor.execute/:77 为仓内无调用方休眠副本——头部编制时原貌保留，以此注记为准〕**③ **IoC 启动链**：`_vfs/**/<name>.beans.xml` 资源（**381 个 `*.beans.xml`（全仓 excl target 口径；严格 `_vfs` 源树 223），其中 app-*.beans.xml 89〔_vfs 源树 65〕；2.7 扫描域 105**——2.7 的 372↔442 对账系 @BizModel↔xmeta，勿混引）→ `AppBeanContainerLoader`（`nop-core-framework/nop-ioc/.../loader/AppBeanContainerLoader.java`）→ `BeanContainerImpl.java`（685 行）→ @Inject 装配 → 启动钩子；1.3 已覆盖 CoreInitialization 九步序列幂等；**本链上游覆盖最薄（容器运行时机理无人预画），为本项重心**。④ **GraphQL 请求链**：`GraphQLWebService.java`（`nop-service-framework/nop-graphql/nop-graphql-core/.../web/`，JAX-RS 入口）→ GraphQLEngine → GraphQLExecutor（:278-297 2.6 已预画）→ IBizManager 分派 → 响应序列化；子模块 core/orm/message 活跃 + **`nop-graphql-grpc` 目录在父 pom 注释停用**（2.6 称四子模块；停用模块=辖区内现成「入口停用」观察点，显式登记处置）；2.6 记录 core 33+orm 8 测试全绿。
> live 证据分档（草审 M-5）：**E1** 构建通过（最弱，仅兜底且须复核员显式签认）/ **E2** 产物演进或上游审计结论引用复验 / **E3** 既有测试遍历该链或亲跑命令（**每链门禁 ≥1 条 E3**）。codegen 链 E3 示例：生成器单测或 paired `git log --oneline -- <model> <generated>`（只读，**禁用 generate-sources 实跑作证据——会写盘**）。
> 只读审计（P0 走异步注入）。**判级锚**：deep-audit 共享前缀 P0–P3（:329-333）；接线判定锚 = plan guide Rule #22/#23。**方法学裁定**：① 6.3 独特价值 = **系统级链路连通性**（内容正确性归 1.4/2.3/2.6/2.7 已闭包域，不重审）；② 每链三件套：**机制边节点清单（每链 ~5-10 边，入口/中间/出口类已在头部点名）** + 逐边代码追踪（调用点实存+可达性）+ live 证据 ≥1 条 E3；③ **断链 taxonomy 五形态**：a.有接口无接线 / b.有注册无分派 / c.有类型无实现（executeMark 先例）/ d.有实现无调用方或入口停用（graphql-grpc 先例）/ e.装配配置断链（2.7 P2-2.7-002 JAR 带 app-*.beans.xml 无 _module 静默失效先例）；④ 链上已由在先审计裁定的位点引用其结论不重复立案。
> Draft Review: agent_b54f7afc-02b0-4c32-8ec8-9936873d9d23 首轮 7 Major（M-1 17→16 执行器口径〔DdlSyntax 非执行器，2.3 :120 同勘正〕/M-2 beans.xml 字面零命中→`*.beans.xml` 381 个+AppBeanContainerLoader/M-3 @NopTest 不存在→@NopTestConfig+JunitBaseTestCase/M-4 撞车面 3.2.6/3.9 悬空→真实先例载体/M-5 live 证据弱证据走过场→E1/E2/E3 分档门禁/M-6 逐边无边界→机制边 ~5-10 边/M-7 四 agent 工作量不均衡→Agent 1/4 以上游节点图为起点+Agent 3 重心预置）+ 8 Minor 全修（graphql-grpc 停用处置/GraphQLWebService 真实入口/2.7 对账张冠李戴/Skill 虚锚注明/撞车清单三处收敛/断链 taxonomy 补全/抽样-全量调停/codegen E3 固定命令+禁实跑）；聚焦复核（agent_766c51c6）15/15 PASS 全部 live 实测背书（3 非阻塞勘误已顺手修：codegen 17→20 目录口径/分派检查 :76→:77/381 口径注明全仓 excl target 严格 _vfs 223）——转 active。

## Purpose

执行 roadmap 工作项 6.3：四条关键链路入口→出口接线追踪（框架机制边全量），验证无断链，产出报告 `6.3-wiring-integrity-audit.md`。

## Current Baseline

- 四链模块、入口类与上游覆盖度见头部注记；各链内容域已由 1.4/2.3/2.7/2.6/1.3 深审闭包——本项只验证**机制边的连通**（节点在链上、边被调用）。
- live 证据锚（草审实测）：codegen 链 E3 = 生成器单测/paired git log；migration 链 E3 = `TestChangeExecutors.java`；IoC 链 E3 = `@NopTestConfig + JunitBaseTestCase` 基座测试（实测例：`nop-demo/nop-quarkus-demo/.../TestDemoBizModel.java`、`nop-auth/nop-auth-service/.../TestMfaVerificationHardeningE2E.java:59`；可跑命令 `-pl nop-demo/nop-quarkus-demo -Dtest=TestDemoBizModel`）；GraphQL 链 E3 = graphql-core 既有执行测试（2.6 记录全绿）。
- 绿色基线：编制期全仓 BUILD SUCCESS（2026-09-22）——E1 级兜底证据；本项只读；工作树在前序收口 commit 后干净。
- 撞车面（三处收敛一致，草审 m-5）：**1.3/1.4/2.3/2.6/2.7**（内容域正结论引用）；1.7（分层合规正结论引用）；6.1/6.2（空壳与吞异常域——引用不重查）；方法论警示载体 = plan guide Rule #22/#23 + 6.1 报告 scanner watch（非 3.2.6/3.9，草审 M-4 勘正）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.3-wiring-integrity-audit.md`：摘要 + 四链节点/机制边台账 + live 证据台账（分档 E1/E2/E3）+ P0–P3 发现（`P<级别>-6.3-<序号>`，按断链 taxonomy 五形态归类）+ 正结论 + 裁决。
- **四链机制边全量接线验证**（每链三件套）：
  1. **codegen 全链**（Agent 1，以 1.4 六站节点图为起点只验边）：pom 绑定 → CodeGenTask → engine → 模板解析 → 产物写盘。E3：生成器单测或 paired git log。
  2. **db-migration 执行链**（Agent 2）：模型解析 → registerExecutor 注册表 → 分派（null 检查）→ 16 执行器 → DDL → recordMigration；**ExecuteMarkChange 无专属执行器的分派路径单列核对**（c 形态核查）。E3：TestChangeExecutors。
  3. **IoC 启动链**（Agent 3，**本项重心**——上游覆盖最薄，主 agent 预置节点清单：资源发现 AppBeanContainerLoader → 解析 → BeanContainerImpl 装配 → @Inject 注入 → 启动钩子）：容器运行时机理逐边追踪。E3：@NopTestConfig 基座测试遍历。
  4. **GraphQL 请求链**（Agent 4，以 2.6 节点图为起点只验边）：GraphQLWebService → GraphQLEngine → GraphQLExecutor → IBizManager 分派 → 序列化。**graphql-grpc 停用模块显式登记**（d 形态观察点：处置=正结论登记或观察项）。E3：graphql-core 既有执行测试。
- P0 即时通道；index 登记 + 独立 closure audit + roadmap 6.3 翻 done（**closure audit 先于翻 done**）。

## Non-Goals

- 链上节点的内容正确性重审（1.4/2.3/2.6/2.7 已闭包）；逐 bean 实例装配（2.7）；逐模板（1.4）；性能面；修复执行；四链之外的业务流。

## Scope

### In Scope

- 四链机制边（每链 ~5-10 边）入口→出口追踪 + live 证据 E3 锚定 + 断链 taxonomy 五形态核查
- 只读输入：1.3/1.4/1.7/2.3/2.6/2.7 报告（撞车对照与正结论引用）；6.1/6.2 报告（域引用）

### Out Of Scope

- 内容语义重审；逐 bean/逐模板；修复执行；四链之外业务流。

## Execution Plan

### Phase 1 - 四链并行接线追踪（4 agent 并行）

Status: completed
Targets: 四链模块

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（f011d05dfd）、`git status` 干净后开始
- [x] Agent 1「codegen 全链」：9 条机制边全部实证闭合（含 3 条 1.4 未覆盖边：第二 mainClass XlangJavaTask phase=none 抑制/离线再生清单 CodeGen.java 27/27 有效/CLI 双入口）；E3 双证据（TestXCodeGenerator 四用例真实断言 + 三对 model/产物同 commit 配对 efbf363a7b 等）；P3×3 + watch×1（A1-01 xdef.register-model.xml 死配置漂移陷阱/A1-02 nop-dao 有意移除 exec 绑定靠手动再生〔bootstrap 豁免族〕/A1-03 exec 版本 1.6.0 双定义零引用实际硬编码 3.0.0）；53 个"有绑定无模板"模块空目录跳过=韧性正结论 → `_tmp/fcr-6.3-wiring-a1.md`
- [x] Agent 2「db-migration 执行链」：E1-E10 机制边全通；16 执行器四方核对零遗漏（implements/注册/回填/beans）；E3 四组（TestMigrationFileScanner H2 端到端/TestMigrationEngine 未知 type fail-loud live/TestChangeExecutors 26 @Test/TestMigrationXdefContract 17 tag 契约）；P3×2 新增（A2-01 generateRollbackSql 全实现但 rollback 零调用——xdef 契约漂移/A2-02 beans.xml 18 bean 零消费者双注册表）+ A2-03 上游锚点校准（引擎实走 MigrationEngine.executeChange:293-307，MigrationExecutor.execute 为休眠副本）+ A2-04 executeMark 与 2.3 check2 ② 一致不重立 → `_tmp/fcr-6.3-wiring-a2.md`
- [x] Agent 3「IoC 启动链（重心）」：10 机制边全量追踪闭环**无断链零 P0/P1/P2**；资源发现双主分支机理（autoconfig _module 无关 + module 扫描 _module 强依赖；merged-file 短路/显式 files//main/beans 三辅助入口）；private 排除机制根因 ClassModelBuilder:404-405；P2-2.7-002 三点连线复现 + live 复核 2/5 消解 4/5 残留（job-worker delta 形态新记录）；E3 双证据（TestDemoBizModel 全链含 NopJunitExtension→CoreInitialization→IocCoreInitializer→restart+injectTo 调用栈）；A3-01 上游残留状态更新（引用不立案）/A3-02 merged-file 短路零触发 P3 watch/A3-03 plugin 子容器零生产调用方 P3 watch/**A3-04 清单勘误（预置节点 IContainerStartUp、XmlBeanElementParser 本仓不存在——报告须替换）**/A3-05 /main/beans 零载体（部署扩展点登记）→ `_tmp/fcr-6.3-wiring-a3.md`
- [x] Agent 4「GraphQL 请求链」：主链 12 边全连通（术语勘误：IBizManager 实为 IBizObjectManager/BizObjectManager）；graphql-orm 接线完整（DataLoader 每请求 loaders map + 懒注册 + 递归 dispatchAll——P1-2.6-004 引用不重立）；A4-01 graphql-message SubscriptionRegistrar 生产零调用方零注册（新立观察）/A4-02 SubscriptionManager 休眠（引 2026-08-23 既有裁定 check/nop-graphql.md:93-102 不重立）/A4-03 grpc 停用正结论/A4-04 subscribeGraphQL 生产零入口（唯一订阅生产入口 WS /ws/jsonrpc→subscribeRpc 有 live 测试）；E3 四条（TestGraphQLEngine.testLoad/TestNopMetaBiSemanticBizModel 全生产装配最强证据/TestGraphQLEngineWithWebSocket/TestSubscriptionDefaultsBeans；与 2.6 core 33+orm 8 口径对账一致）→ `_tmp/fcr-6.3-wiring-a4.md`
- [x] 主 agent 抽查质量：每链抽 1 条边独立复判 + 每链 E3 证据亲验（测试类定位与断言读取；只读命令）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项初判零 P0——四链全连通）
- [x] 产物 `_tmp/fcr-6.3-wiring-a1/a2/a3/a4.md`（四 agent 分报告）

Exit Criteria:

- [x] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向；每链节点/机制边台账 + live 证据完整
- [x] **每链 ≥1 条 E3 证据落盘**（四链全部 E3 达标，零 E1 兜底依赖）
- [x] No owner-doc update required（只读——修复归 R1.0）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：**VERDICT 通过**——四链连通维持 P0=0/P1=0/P2=0 终裁；8/8 边独立追踪全通（自行 grep 定位未复用证据行）；E3 4/4 亲验、E1 兜底零依赖；发现 100% 亲读；**撞车漏检 3 处纠正改记 dup**（A1-01≡P2-1.2-008〔1.2 已判 P2〕/A2-01≡P3-2.3-034〔P1 终裁 P3-dup，与 ILockService 先例三点区分〕/A4-01≡P3-2.6-008）+ 事实勘误 6 项（A3-05 零载体被证伪/A2-02 bean 计数 18→19/A1-04 消费面收窄/A4-02 新颖性夸大/A2-03 上游锚点校准/A3-04 幻影类名坐实）；教训入档：撞车检索面=相邻工作项发现清单全量 grep

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-6.3-wiring-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `6.3-wiring-integrity-audit.md`；index 登记 6.3 行（替换占位行）+ P1 汇总行（如有——P1=0 零新增）+ P2 裁决台账行（如开辟例外——未开辟零新增）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0——closure audit 终裁确认）
- [x] 定向验证锚点：E3 证据抽验由复核员独立执行（4/4）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（六项：证据完整性/严重性标定/引用路径真实性 16 处/计数对应/文本一致/流程合规——首轮 FAIL 附处方 R1-R2〔纯文本级〕，处方落实后轻量复核 PASS）；**通过后** roadmap 6.3 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] E3 证据可复现（复核员抽验一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（6.1 全仓扫描已覆盖空壳面）

## Closure Gates

> 只读审计计划，免除全量构建验证（live 证据以既有测试/构建产物为锚）；以复核员独立抽验为验证锚点。

- [x] 报告落盘且四链机制边验证全覆盖——覆盖度量：42 机制边台账（closure auditor 三处一致核验，O1 构成拆分注记）+ **每链 ≥1 条 E3**（4/4，E1 兜底零依赖）
- [x] 每条发现 file:line 可复现；判级依据共享前缀；断链 taxonomy 五形态归类完整；误报校准留痕（勘误 6 项 + 复核 dup×3 透明记载）
- [x] index 计数与报告发现清单一一对应（3 条 = 0/0/0/3；P0/P1/P2 三台账节零新增正确）
- [x] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done——FAIL→R1/R2→轻量复核 PASS）
- [x] roadmap 6.3 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项（watch×3/观察×4 显式登记）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；接线修复工单归 R1.0/R1.x。）

## Closure

Status Note: 6.3 四链路接线完整性审计收口。四链主链全连通零 P0/P1/P2（「验证无断链」目标达成）；净新增 P3×3（nop-dao exec 绑定护栏缺失/exec 版本死属性/db-migration beans 双注册表）全部移交 R1.0；复核纠正撞车漏检 3 处改记 dup（归并 P2-1.2-008/P3-2.3-034/P3-2.6-008 承载）；E3 四链达标 E1 兜底零依赖；勘误 6 项落实（含 A3-04 幻影类名替换）。closure audit（agent_010b3bc3）首轮 FAIL 附处方 R1-R2（纯文本级）：R1=报告「plan 头部已勘正」表述与事实不符 → 按 2348 先例补头部勘误注记使表述成真；R2=Phase 1 抽查项与 Phase 2 Exit Criteria 四项事实已完成未勾 → 补勾。处方落实后轻量复核转 PASS。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_010b3bc3-4775-431a-a6e8-05117847da8d（fresh session，只读 closure auditor；VERDICT: FAIL（处方 R1、R2）→ 处方落实后轻量复核 PASS）
- Evidence:
  - 计数勾稽：报告 P3×3（0/0/0/3）↔ index 6.3 行 ↔ 复核净账 ↔ 原编号映射（A1-02→001/A1-03→002/A2-02→003）零漂移；dup×3 归并对象在 1.2:63/2.3:91/2.6:57 逐字实证存在；P0/P1/P2 三台账节零新增正确；watch W1-W3/O1-O4/勘误 6 项登记完整
  - 关键判定亲读 5 处（超 ≥4）：P3-6.3-001（git show bef6c43087 逐字 + CodeGen.java:41/:33/:61 全中）/P3-6.3-003（default.beans.xml 19 bean 实测 + 零消费者 grep 坐实）/dup 抽验/E3 抽验（TestGraphQLEngine.testLoad + MockGraphQLSchemaLoader:69 ServiceActionFetcher）/加验 TestDemoBizModel 全要素
  - 引用路径 16 处抽验全命中零悬空（含 check/nop-graphql.md:93-102、migration.xdef:482、pom 1.6.0 双定义 + 3.0.0 硬编码 + 零引用）
  - 覆盖度量：42 机制边三处一致（O1 构成拆分表述注记）；复核 8/8 抽验实存；E3 4/4 + E1 零依赖显式交代
  - 验证：doc-links exit 0；check-plan-checklist --strict exit 0（处方落实后复跑确认）
  - Anti-Hollow：只读审计无新增组件；验证锚 = 复核 8/8 独立边追踪 + E3 亲验 + closure auditor 亲读
  - Deferred 项分类检查：无 deferred；P3×3 进 R1.0 输入池；dup×3 由源条目承载；watch/观察显式登记非静默降级
  - 流程合规：audit 先行于 roadmap 翻 done（roadmap 6.3 保持 todo 至本 audit FAIL→处方落实→轻量复核 PASS）

Follow-up:

- no remaining plan-owned work（P3×3 归 R1.0；dup×3 由 P2-1.2-008/P3-2.3-034/P3-2.6-008 承载；W1-W3/O1-O4 登记；a3 O1 构成拆分表述注记已入报告 §六）
