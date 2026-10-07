# 2350 full-code-review 6.3 接线完整性抽样审计（四链路入口→出口）

> Plan Status: active
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.3（MA6）；Skill 锚沿袭 roadmap 行（`deep-audit-prompts.md` 接线/端到端路径方法——**经审查该 skill 无此专节，真实方法锚 = plan guide Rule #22 端到端验证/#23 接线验证**；roadmap 行锚为虚锚，本 plan 以 guide 为准，回修归 V.3 文本一致性核对）
> Related: Roadmap Details：「四条关键链路入口→出口追踪（codegen、db-migration 执行、IoC 启动、GraphQL 请求），验证无断链」。**范围调停（草审 m-7）**：标题「抽样」沿 roadmap，Goals「全量」＝**框架机制边全量**（每链节点清单 ~5-10 边）；逐 bean 实例装配归 2.7 已闭包、逐模板归 1.4 已闭包，显式排除。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_b54f7afc 逐项实测勘正）：四链核心模块与入口类——① **codegen 全链**：调用方 = exec-maven-plugin（根 pom.xml:388、nop-kernel/pom.xml:364）+ 各 `*-codegen` 子模块（20 个 Maven 模块目录实存，其中 19 个绑定 exec-maven-plugin〔nop-auth-codegen pom:64〕，引擎模块 nop-kernel/nop-codegen 自身不绑定）→ `nop-kernel/nop-codegen/.../task/CodeGenTask.java`（main:139）→ XCodeGenerator → TemplateFileGenerator → renderTemplate → 产物写盘（`_` 前缀）；1.4 已预画六站连通（模板链 16/16 抽样、162 模板 = .xgen 120+.xrun 26+静态 16）。② **db-migration 执行链**：migration 模型 → `MigrationExecutor.registerExecutor:34` 注册 + 分派 null 检查 :77 → **16 个具体执行器**（executor/ 18 文件 = 16 执行器 + IChangeExecutor 接口 + DdlSyntax 语法助手；**17 = change 类型数**，ExecuteMarkChange 无专属执行器——分派路径单列核对，2.3 :120 已有同口径勘正）→ DDL 渲染 → recordMigration；2.3 已逐 executor 语义验证 + JdbcBatcher 实跑；`TestChangeExecutors.java` 在场。③ **IoC 启动链**：`_vfs/**/<name>.beans.xml` 资源（**381 个 `*.beans.xml`（全仓 excl target 口径；严格 `_vfs` 源树 223），其中 app-*.beans.xml 89〔_vfs 源树 65〕；2.7 扫描域 105**——2.7 的 372↔442 对账系 @BizModel↔xmeta，勿混引）→ `AppBeanContainerLoader`（`nop-core-framework/nop-ioc/.../loader/AppBeanContainerLoader.java`）→ `BeanContainerImpl.java`（685 行）→ @Inject 装配 → 启动钩子；1.3 已覆盖 CoreInitialization 九步序列幂等；**本链上游覆盖最薄（容器运行时机理无人预画），为本项重心**。④ **GraphQL 请求链**：`GraphQLWebService.java`（`nop-service-framework/nop-graphql/nop-graphql-core/.../web/`，JAX-RS 入口）→ GraphQLEngine → GraphQLExecutor（:278-297 2.6 已预画）→ IBizManager 分派 → 响应序列化；子模块 core/orm/message 活跃 + **`nop-graphql-grpc` 目录在父 pom 注释停用**（2.6 称四子模块；停用模块=辖区内现成「入口停用」观察点，显式登记处置）；2.6 记录 core 33+orm 8 测试全绿。
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

Status: planned
Targets: 四链模块

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] Agent 1「codegen 全链」：机制边清单 + 逐边追踪 + E3 证据（以上游节点图为起点）
- [ ] Agent 2「db-migration 执行链」：机制边清单 + 16 执行器注册→可达核对 + ExecuteMark 分派单列 + E3
- [ ] Agent 3「IoC 启动链（重心）」：主 agent 预置节点清单逐边追踪 + E3（@NopTestConfig 基座）
- [ ] Agent 4「GraphQL 请求链」：机制边清单（以上游节点图为起点）+ graphql-grpc 停用登记 + E3
- [ ] 主 agent 抽查质量：每链抽 1 条边独立复判 + 每链 E3 证据亲验（测试类定位与断言读取；只读命令）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（**链路断链类 P0 = 立即异步注入修复 plan**）
- [ ] 产物 `_tmp/fcr-6.3-wiring.md`（四 agent 分节）

Exit Criteria:

- [ ] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向；每链节点/机制边台账 + live 证据完整
- [ ] **每链 ≥1 条 E3 证据落盘**（E1 仅兜底且须复核员签认；E2 须注明上游结论出处）
- [ ] No owner-doc update required（只读——修复归 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：发现逐条 保留/降级/驳回 + 每链抽 2 条边独立追踪复判 + E3 证据抽验 + E1 兜底签认审查 + 撞车核对（1.3/1.4/1.7/2.3/2.6/2.7 + 6.1/6.2 域引用）
- [ ] 主 agent 汇总：判级统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.3-wiring-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.3-wiring-integrity-audit.md`；index 登记 6.3 行（替换占位行）+ P1 汇总行（如有）+ P2 裁决台账行（如开辟例外）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：E3 证据抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；**通过后** roadmap 6.3 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] E3 证据可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（6.1 全仓扫描已覆盖空壳面）

## Closure Gates

> 只读审计计划，免除全量构建验证（live 证据以既有测试/构建产物为锚）；以复核员独立抽验为验证锚点。

- [ ] 报告落盘且四链机制边验证全覆盖——覆盖度量：四链节点/机制边台账（~5-10 边/链）+ **每链 ≥1 条 E3**（E1 兜底已签认或无）
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；断链 taxonomy 五形态归类完整；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；接线修复工单归 R1.0/R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
