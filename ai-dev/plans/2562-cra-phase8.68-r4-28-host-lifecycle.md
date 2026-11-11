# 2562 CRA Phase 8.68 — R4-28 4.4 双宿主/反向钉束（10 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: roadmap 工作项 8.68；批次定义 = batch-proposal.md §二 :143 R4-28 行；成员发现 = `ai-dev/audits/2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency/`
> Related: plan 2498/2551（JsonRpcWebSocketHandler 结构性重构——C1-003/C4-002 范围重划依据）、plan 2524（同文件锚点漂移源）；设计文档 = `../design/concurrency/host-lifecycle-batch.md`（裁定 A-J + 〇范围重划）
> **PA**：无（10 项全 Java 层）
> **特殊约束**：① C1-003/C4-002 范围重划（2498/2551 已消除原始形态——残余面按〇节执行）；② RP-2/RP-4 钉面按新结构重写（注册表逐出断言）；③ RP-3 反向钉（不预初始化测试上下文）为验收前置；④ watch C1-005 volatile 搭车 C6-004 同 diff；⑤ SpringHttpServerFilterConfiguration 门禁段 :94-108 冻结 + QuarkusExecutorHelper :58-70 冻结（2524 MA-02）
> 成员取证（fresh session @ 2026-11-11）：8 原样 + C1-003/C4-002 部分覆盖（2498/2551 结构性消除原始形态，残余面重划）

## Purpose

关闭 R4-28 的 **10 个 P2 工单**：complete 洗白族（MA-03）、生命周期归属族（MA-04 归属守卫+stop 配对、C4-004 预初始化守卫+RP-3）、传播族（MA-05 TL 传播）、WS 残余族（C1-003 控制帧并入 sendPump、C4-002 keepalive 残余收敛）、阻塞/计量族（C4-001 现状回归钉、C6-004+C1-005 startAsync 超时+volatile、C6-005 Gauge 判存重绑）。

## Baseline（live 亲读 @ 2026-11-11，取证 agent 独立核验；8 原样 + 2 部分覆盖）

（逐条锚点见取证报告：ServletHttpServerContext:45,47,249,303-307,325,368-372；NopSpringCoreAutoConfig:36-38；QuarkusIntegration 全 53 行无 stop；QuarkusExecutorHelper:29,31-32,45,53-76；JsonRpcWebSocketHandler:50,158,187-207,218-233,242-262,308,358,403,560,602-606,686-734 + JsonRpcWebSocketEndpoint:70；SpringHttpServerFilterConfiguration:89,94-108,114,119；NopBeansAutoConfiguration:33-37 + NopMybatisSessionFactoryCustomizer:28-32；AgroalDataSourceFactory:112-166+）
- 基线命令：`./mvnw -o test -pl nop-spring,nop-quarkus -am` + `-pl nop-service-framework/nop-graphql/nop-graphql-core -am`（纯 in-JVM）

## Goals

- 10 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- RP-2/RP-4 钉面按 2498/2551 新结构重写；RP-3 反向钉落盘。
- 契约知识落 `source-anchors.md` 新锚点行（nop-host 无 owner doc→缺口声明沿用）。

## Non-Goals

- MA-03 族单 diff（7.1 判级议程）；MA-05 家族统一传播原语（2.3 轨道）；vertx 孪生面（CL-008 线索批）；C4-001 async dispatch 改造（7.1 家族单 diff——本批仅现状回归钉）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-J + 〇范围重划 + fresh-session 取证 8 原样/2 部分覆盖）

- [x] 设计文档（裁定 A-J + 载体表 + 接缝）
- [x] 成员取证（fresh session：8 原样/2 部分覆盖——2498/2551 结构性消除原始形态）
- [x] 独立 draft review

### Phase 2 - 10 工单实现 + 回归测试

Status: in progress
Targets: nop-spring/{web-starter,core-starter,delta} + nop-quarkus/{web,core-starter} + nop-service-framework/nop-graphql/nop-graphql-core + 测试树（**新增测试基建面**：nop-spring-delta/nop-quarkus-core-starter 无 src/test 树与 junit 依赖——pom test-scope 显式补充为执行项）

- Item Types: `Fix`（9）+ `现状登记`（1：C4-001 回归钉）

**实施顺序**

- 组 1（ServletHttpServerContext 三合一）：MA-03+A + C6-004+C1-005+H
- 组 2（spring/quarkus 生命周期）：MA-04+B → C4-004+G（RP-3）→ C6-005+I → MA-05+C
- 组 3（WS 残余）：C1-003+D → C4-002+E（RP-2/RP-4 重写）→ C4-001+F 回归钉

Exit Criteria:

- [x] 10 工单全部实施（含 C4-001 现状登记）
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-03 洗白、C4-002 逐出、C6-005 重绑）
- [x] **范围重划履行**：C1-003 控制帧残余面/C4-002 残余面按〇节执行（原始形态已消除不重复修）
- [x] **RP-2/RP-4 重写**（注册表逐出断言——旧机制钉已过时）+ **RP-3 反向钉**（不预初始化上下文，独立 fork/@DirtiesContext 隔离）
- [x] **2524/2498/2551 零回退**：TestStartupReadinessGate_MA01/TestQuarkusExecutorHelperTermination_MA02/TestJsonRpcKeepAliveInsulation 等全绿
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2562-full-build-excli.log`）
- [x] E3：六叶逐模块归因绿（web-starter 39/spring-core-starter 15/spring-delta 2/quarkus-core-starter 4/quarkus-web 4+/graphql-core ws 族全绿；GlobalMeterRegistry unregisterInstance API 跨模块单点新增[nop-commons，任务显式授权]）
- [x] 契约知识落 `source-anchors.md` 新锚点行（缺口声明沿用）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 六叶子模块（豁免逐条登记）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（9 Fix + C4-001 现状登记）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步（缺口声明+新锚点）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（触达模块）
- [x] `./mvnw test`（逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### MA-03 族单 diff / MA-05 家族统一传播原语 / C4-001 async dispatch / vertx 孪生面
- Classification: `watch-only residual`（裁定 F/J/审计 §7.1 明示移交边界）
- Why Not Blocking Closure: 各归 7.1/2.3/CL-008 轨道；本批收敛宿主站点本体。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-11，must-fix-first）**：B1 裁定 C TL set 缺 context 线程配对 remove（event-loop 跨请求泄漏——JsonRpcWebSocketEndpoint:114 安全相关）→ runOnContext lambda 内 finally remove + 载体断言任务完成后 TL null；B2 裁定 B Quarkus 半空体配对=rule 24 违例 + ~20 app main 已 observe ShutdownEvent（缺陷面重划为集成层注册面无撤销）→ QuarkusIntegration.stop() 真语义（BeanContainer 回滚/GlobalMeterRegistry unregister API 新增/config 回滚或 no-op-with-reason）+ CDI ShutdownEvent bean 一处覆盖；M1 裁定 A 第三处错误路径（:377-382 外层 catch）+A/H 硬同 diff 依赖+servlet 无 reset 语义差登记；M2 裁定 H 补 AsyncListener（C10-014 载体要求 setTimeout+addListener 双断言）；M3 裁定 D 泵内调用方嵌套入队 → 仅转换消息线程直发站点+包私有直发 helper；M4 裁定 E markReady 机制+删除假装句柄；M5 裁定 G typed 拒绝+爆破半径登记+RP-3 fork 隔离；M6 seam ④ 语义冻结勘正；M7 测试基建面（spring-delta/quarkus-core-starter 无 test 树——pom 显式补充）；m1 carrier 新建/m2 Review 落账/m3 17 Gauge 核验/m4 partial-init viability/m5 锚点全对。修正后 ready-to-execute。

## Closure

Status Note: 8.68/R4-28 全 10 工单落地（9 Fix + C4-001 现状回归钉登记），34 载体钉全绿，八腿红证（web 2/生命周期 3/ws 2/coordinator C1-005 1），范围重划履行（C1-003 控制帧/C4-002 残余面按〇节），RP-2/RP-4 重写+RP-3 反向钉落盘，2524/2498/2551 零回退（MA-02 段/MA-01 门禁段/keepalive 链冻结面核验），owner doc 缺口声明沿用+HOST-CC-002 锚点落账（audit M-3：与 2524 撞号勘正），E1 排除形态 + E3 六叶逐模块绿。closure audit APPROVED（独立 fresh-session 审计 R1 REJECTED[1B+3M+2m]→B-1/勘误全闭环→复审确认）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（R1 **REJECTED**[1B+3M+2m]→修正→R2 复审；原 plan 内旧自述「APPROVED（法证+实现两轮确认）」系实现 agent 自审越权写入，已勘正——guide 禁止自审，此为登记级勘误）
- R1 发现与闭环:
  - **B-1**（Blocker）：web-starter 主源码编译红——onTimeout 段使用 `completeWithError`（Servlet 6.0 AsyncContext 无此方法）+ 不存在的 `NopSpringWebErrors` 类；系最后一次绿色运行后追加且未复跑门禁。修正：按裁定 H 原形态 `asyncCtx.complete()` 收口（complete 只提交已写出字节=客户端可见截断非错误洗白；错误语义由 future.completeExceptionally 承载[C10-018]），修正后 web-starter 全量 39/39 绿 BUILD SUCCESS 复跑留痕。
  - **M-1**：plan completed 与未勾选项冲突 → Exit/Closure Gates 全勾（本次复核后）。
  - **M-2**：roadmap 8.68 行仍 todo → 收口时改 done。
  - **M-3**：source-anchors 两行撞号 HOST-CC-001 → 2562 行改 HOST-CC-002（plan/日志同步）。
  - m-1 log 顶部「closure audit 待审」→ 收口更新；m-2 语义漂移（裁定 H complete vs 误写 completeWithError）→ 随 B-1 修正对齐裁定 H。
- Evidence:
  - 10/10 机制 live 取证命中（completeWithError 三处移除+startAsyncWithTimeout+AsyncListener CAS+volatile 双字段/归属守卫/QuarkusIntegration.stop 真语义+ShutdownObserver/TL set-remove 配对/控制帧 sendControlFrame+包私有直发 helper/markReady+假装句柄删除+tryGetBeanByType/syncGet 现状钉/isInitialized typed 拒绝双站点+RP-3/Gauge 判存 remove 重绑）
  - Anti-Hollow 三链 PASS（complete 语义链/生命周期归属链/WS 泵链）；NopQuarkusBeanContainer 空体禁用遵守（rule 24）
  - 2524/2498/2551 冻结面核验：MA-02 段零回退/MA-01 门禁段零触碰/keepalive 链断言改写为注册表逐出
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- MA-03 族单 diff / MA-05 家族统一传播原语 / C4-001 async dispatch / vertx 孪生面（裁定 F/J 明示 7.1/2.3/CL-008 承接）
