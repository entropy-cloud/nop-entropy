# Dispatch Log — CRA WI 6.5 nop-peripheral 并发审计

- **Plan**: `ai-dev/plans/2492-cra-wi6.5-nop-peripheral.md`（active；R1 agent_ddc31a98 3 Major[frontend 8 子+JavaScriptWorker 真实面/demo 14 子/前置预拆判定]+5 Minor 全修）
- **基线**: commit a20ca25130；基线文件 `_tmp/cra-baseline-nop-peripheral.txt`（附录 C + 调试器线程专项）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: 无 api 叶（nop-api-debugger 近失裁定：调试工具模块非契约叶）；idea-plugin IntelliJ 平台线程惯例校准；demo 降口径（结构性并发/资源缺陷才立案）；nop-benchmark 误报校准；源码树 `_` 前缀生成物只读
- **跨单元预登记**: 2026-09 G 系列对四模块零命中（R1/Phase 1 清点）——无预登记项

## Phase 1 锚点核实结论（主 agent，2026-10-23）

| 锚点 | 实测 | 结论 |
|------|------|------|
| dev-tools 原语 | 40 命中（BreakpointManagerImpl/SuspendedThread/XLangDebugger/XLangDebugProcess/NopProjectService 等） | C1/C6 主战场确认 |
| frontend 原语 | JavaScriptWorker（volatile×4+executeThread）+JavaScriptService | 真实 C1/C6 面（R1 订正成立） |
| demo 原语 | 仅 spring-gateway-demo DemoServiceBizModel 等零星 | demo 降口径适用 |
| e2e | 零 Java 面（Playwright/packages） | 零审计面显式登记（沿 2.5 nop-cdc 先例） |
| 前置预拆判定 | C6 锚点核实后执行（dev-tools 调试器面站点数判定） | 见 C6 派发记录 |

## owner doc 漂移登记

- 四模块无 owner doc（roadmap owner 列「—」）——整体 drift 登记，归治理轨。

## 历史审计对照口径

- cra-index 与 2026-09 G 系列对四模块零命中——CRA 内首报，无预登记项。

## 维度级派发记录

1. C1 全单元（agent_a94f1b59，串行 1/2）：载体 40 项（安全 31/立案 7/观察 2）→ 发现 7（**C1-002 P1** JavaScriptWorker 关闭路径 execute 检查-入队非原子→任务搁浅 future 永不完成[syncGet 无超时阻塞实证]、C1-001 P2 XLangDebugger doSuspend/monitorNotify 交接窗口幽灵挂起、C1-003 P2 NopProjectService 全局注册表多项目交叉销毁、C1-004 P2 nopVfsPathCaches 无移除 IDE 泄漏、P3×3）+ CL-001~004；demo 降口径与 idea-plugin 惯例校准落实。
2. C6 全单元（agent_44dae1e2，串行 2/2）：配对 24 项（dev-tools 17/frontend 6/demo 1）→ 发现 3（**C6-001 P1** XLangDebugConnector/Process 从不调用 proxy_destroy——每会话泄漏 1 fd+Disconnect 钉死 1 recv 线程、C6-002 P2 suspendedThreads 清理一次性 schedule 只进不出、C6-003 P2 ProjectFileHelper 静态 Map 无项目关闭清理[IntelliJ 惯例应弱键]）+ CL-201~204；JavaScriptWorker 资源侧独立结论=无泄漏（语义侧归 C1）。

   > **勘误（closure audit M1，2026-10-23）**：上行配对分桶「frontend 6/demo 1」与 C6 文件配对表实际分桶 **dev-tools 17/frontend 5/demo 2**（#18-22 frontend 5 项、#23-24 demo 2 项）不符；总数 24 无误、与全部权威计数链无涉，以 C6 文件配对表为准。

**Phase 2 汇总**：10 立案 = P1×2 + P2×5 + P3×3；CL 线索 8。已知跨维同站点：JavaScriptWorker 关闭路径（C1-002 语义侧/C6 资源侧无泄漏裁定互补）。


## Phase 3 深挖派发记录

3. C1+C6 R2 合并批（agent_da7b6dcb，串行）：C1 新增 1（C1-R2-001 P3 XLangDebuggerInitializer.sendStack 裸读非 volatile server 字段——停机窗口业务线程裸 NPE，同文件 sendBreakpointNotice 已有正确形态可修）；C6 新增 1（C6-R2-001 P3 demo 降口径 ReportController 临时文件生命周期缺口+修正初审 #24 甄别错误[createTempFile grep 漏 getTempResource]）；同族放大闭合（api-debugger 纯 DTO/maven-shaded 无进程/只进不出第 3 处不存在）；两维度终止。

**Phase 3 汇总**：12 立案 = P1×2 + P2×5 + P3×5；全维度显式终止；无必派触发。

## Phase 4 复核派发记录

4. C1+C6 独立复核合并批（agent_9474f388，串行）：12 条发现逐条重开源码核实全部**已保留**（无降级/驳回/升级，抽样「安全」判定 14 项失败数 0）；9 条 CL 线索裁定——CL-001/002/004 确认保留、CL-003 确认但**并入 C6-002**（同站点防双计）、CL-201/202/204 确认、CL-203 确认且 **cross-ref WI 2.1 C4-003（已知未修复，本体归 WI 2.1）**、CL-205 记录性确认。现状标注：12 条发现全部**新发现**（2026-09 G 系列 13 文件对四模块零提及 grep 证实；campaign 既有 WI 4.3 C1-005 / WI 2.1 CL-024/C4-003 为设施侧不同缺陷面）。复核修正注记 3 条（C1-003 destroy 反注册集精确化、C1-R2-001 行号勘误 L92→L89、C6-001 debuggee 被杀子场景经 autoReconnect=true 修正为无限重连僵尸循环——Disconnect 核心泄漏不变）。复核节已落盘两维度文件，复核状态行全部回填。

## Phase 5 C10 派发记录

5. C10 测试保护力（agent_91810baf，串行）：测试面穷举（40 测试文件逐目录盘点，勘误初审草稿 32）+ 12 条在案缺陷变异推演 → **立案 6（全部 P2 缺保护：C10-001~006，覆盖 P1×2+P2×4）** + watch-only 5（P3/demo 降口径的缺保护，W-1~W-5）+ CL-301（**nop-idea-plugin Gradle 独立构建不参与 `./mvnw test` 门禁**，campaign 首报）。保护力矩阵三态：会红 0/假保护 0/无测试 6——全单元对在案缺陷零钉死力。
6. C10 独立复核（agent_f0a17614，串行）：6 条立案全部**已保留**；C10-003「假保护」定性经逐交错变异重放**证伪并修正为「无测试（零可观测）」**（立案加强，矩阵三态联动修正）；CL-301 确认（pom.xml modules 三项 + build.gradle.kts 独立核实，WI 4.4 为 nop-host 不同范畴无 cross-ref）；watch-only 口径核对完全一致；现状标注全部 campaign 首报。

## Phase 6 合并裁定与登记记录

7. 独立合并裁定（agent_57f6c5a6，串行）：12 实体三态终裁定——同站点归并 1（C6-003→C1-004 别名）、族工单 1（W-01 C1-002 吸 CL-001/002 + 伴生 CL-201）、同站点吸并 1（W-06 C6-002 吸 CL-003）→ **11 工单 = P1×2[W-01/W-02] + P2×4[W-03~06] + P3×5[W-07~11]**；P0=0 显式空执行（两轮复核无候选 + 终审确认）；CL 10/10 确认全落位（并入工单 5/移交 1[CL-203→WI 2.1 C4-003]/独立线索 2[CL-004、CL-301]/watch 1/记录性关闭 1）；C10 缺保护 6 条单列（会红=0 第 13 例）；**CL-301 验证通道约束**：idea-plugin 站点工单回归验证不得依赖 `./mvnw test`（Gradle/harness 或 plain-JVM seam 下沉三选一）；跨单元查重零同站点重复、家族关联 6 组；数字自洽七步全闭合零勘误。
8. 主 agent 登记：summary.md 归档（附录 B 格式，数字与裁定 §九 一致）；cra-index 四处登记（报告清单 WI 6.5 行 / 6.5 计数口径块 / P1 台账 2 行 / WI 6.5 CL 小节）。
9. **P0 即时通道：显式空执行**（Phase 7 零产品代码变更，No owner-doc update required）。
