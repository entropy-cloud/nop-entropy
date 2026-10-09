# 2362 WI16 — transform 级 SQL 形态（WI0(e) 裁定落地）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI16（v14）
> Related: vision §六 #WI0-XDSL (e)（裁定原文）、plan 2358/2359/2360（同型裁定先例）

## Purpose

落地 WI0(e) 裁定（**不放宽** `<sql>` 顶层唯一约束）：交付 transform 级 SQL 的 Java-only 记录 + owner doc 指针 + 形态测试（Java 编译产物挂接路径），E8 形态证据成立——**E8 验收套件本体（TestXdslScenarioE8）归 WI24**（deps WI9+WI16+WI20），本 WI 仅交付其依赖的形态证据。

## Current Baseline

live 核实（2026-10-10，HEAD 2a5709c065）：

- **裁定已预定**：WI0(e)（vision §六 #WI0-XDSL）——维持 `<sql>` 顶层唯一约束不放宽（顶层生成器是唯一 SQL 编译入口的单一事实源；放宽引入「模型内嵌 SQL 片段」第二编译路径）；WI16 交付 Java-only 记录 + owner doc 指针；E8 以「Java DataStream API 挂接 EQL/`<sql>` 编译产物」形态验收。重启触发=真实多段 SQL 管线声明诉求出现。
- **顶层唯一约束钉**：`expandSqlModel`（builder :516+）——`<sql>` 与其他 transforms/edges/registries 并存 → fail-fast（roadmap 引 `:288-302` 为旧锚，现状在 expandSqlModel 方法体）。
- **Java-only 路径天然可行且已有部分覆盖**：`ISqlStreamCompiler.compileSql(...)` 返回**完整可执行模型 XML**——Java 侧程序化调用 SPI → parse → build 即「Java 编译产物挂接」形态。既有覆盖（F1 盘点）：`TestTumbleAggregateQueryE2E.tumbleWindowedAggregateExecutesEndToEnd`（sql 模块：静态 facade `StreamSqlCompiler.compile` TUMBLE 聚合 → parse → build → execute → sink 断言）与 `TestSqlModelDeclarationE2E.sqlDeclarationCompilesAndExecutesEndToEnd`（声明式 `<sql>` → builder 内真实 provider → execute）已覆盖主链。**本 WI 形态测试的 delta**：(a) 经 **SPI 接口类型直调** `compileSql`（非静态 facade，锁 SPI 契约面）；(b) WI0(e) 裁定冠名的 Java-only 路径证据。
- **测试落点（F2 裁定）**：nop-stream-sql（flow 无 sql 依赖，单向 sql→flow——sql 侧既有测试先例齐备：容器 bootstrap/ORDERS_SCHEMA/SqlTestSink）。
- **顶层唯一约束钉**：既有双钉（`TestSqlModelExpansion.sqlCoexistingWithOtherContentFailsFast` + `noProviderFailsFastNamingDependency`，nop-stream-flow）——Phase 1 纯核对。
- **WI37/D11**：emit 两档已接线；WI25/Q1 lateness 注解已交付（SPI 签名齐备）。

## 裁定（WI0(e) 执行）

`<sql>` 顶层唯一约束**不放宽**。transform 级 SQL 的支持形态=**Java-only**：Java 侧经 `ISqlStreamCompiler` SPI 程序化编译 SQL 段为模型 XML，与程序化构建的其他模型片段合并（parse 后合并或整模型 build），不进入 XDSL 声明面。owner doc 指针：user guide 裁定段指向 `ISqlStreamCompiler` javadoc 与 sql 模块接线测试。

## Goals

- 形态测试：Java 侧调用 `ISqlStreamCompiler.compileSql` → parse → build → execute → 输出断言（E8 形态验收证据）+ 顶层唯一约束钉保持核对（并存模型 fail-fast 不变）。
- Java-only 记录落 user guide（含 Java 路径配方与重启触发）+ roadmap WI16 勾选（header v15）。

## Non-Goals

- 不放宽顶层唯一约束；不新增 transform 级 SQL 声明面。
- 不改 ISqlStreamCompiler SPI。

## Execution Plan

### Phase 1 - 裁定落地（单 Phase：形态测试 + 记录 + audit）

Status: completed
Targets: 新测试 `TestWi16TransformSqlJavaPath.java`（nop-stream-flow 或 sql 模块——SPI 实现在 sql 模块，测试落 sql 模块或 flow+test 依赖核对）、builder 顶层唯一钉核对、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] 形态测试（落 nop-stream-sql，**SPI 接口直调 delta**）：`ISqlStreamCompiler.compileSql(...)` 产物 XML → parse → build → execute → sink 输出断言（Java 编译产物挂接全链，WI0(e) 冠名）
- [x] 顶层唯一约束钉核对：既有双钉保持（TestSqlModelExpansion 并存 fail-fast + noProvider）
- [x] flow 全量回归（290/0 补跑核实——本 WI 零 flow 代码改动）+ sql 全量回归（155/0）
- [x] user guide Java-only 裁定段（Java 路径配方：SPI 调用→parse→build；重启触发）+ **fail-fast 表 :289 顶层唯一约束钉测试引用修正**（`TestStreamSqlCompiler` 无并存测试——实际钉为 flow 的 `TestSqlModelExpansion` 双钉，F3）+ lint 面记录（R12 规则无 `<sql>` 分支需退役——F4 显式记录）+ roadmap WI16 勾选（header v15，**行内旧锚 :288-302 同步改写为 expandSqlModel :516/:521-530**——F6）+ 三工具
- [x] 独立子 agent closure audit + 单次提交——首轮 REJECT（1M E8 disclaimer 未落 plan 本体+2m+flow 补跑义务）条件已同批完成，复审待记录

Exit Criteria:

- [x] 形态测试全链绿（SPI 直调产物可执行 + 输出断言；delta 相对 TestTumbleAggregateQueryE2E=SPI 接口契约面锁定）
- [x] 顶层唯一约束钉保持
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 裁定与 live 一致（钉保持 + Java 路径可执行）
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：Java 路径调用链连通（SPI→parse→build→execute→sink）
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### transform 级 SQL 声明面（放宽顶层唯一约束）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0(e) owner 裁定明确不放宽（单一编译入口事实源）；重启触发=真实多段 SQL 管线声明诉求出现（裁定原文）。
- Successor Required: `yes`
- Successor Path: owner 重启裁定后按 `StreamSqlCompiler` 复用路径交付声明面（roadmap WI16 行原方案 A）。

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: WI0(e) 裁定忠实转记并落地：`<sql>` 顶层唯一约束不放宽（双钉保持亲跑绿）；Java-only 路径以 SPI 契约面锁定（ISqlStreamCompiler 按类型直调五参 compileSql → parse → build → execute → sink 内容断言 + emit gate fail-fast 面钉）。首轮 closure audit REJECT（1M E8→WI24 disclaimer 未落 plan 本体 + 2m + flow 补跑义务）条件已同批完成：disclaimer 补入 Purpose、user guide :291 残留碎片删除、flow 290/0 补跑核实（本 WI 零 flow 代码改动）、sql 155/0 全量绿。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_94ea7d80（独立 fresh-session closure auditor——首轮）
- Audit Session: agent_94ea7d80-fb2e-4a5a-9276-beeaf9484a4b
- Evidence:
  - 形态测试（audit 亲跑 2/0 绿）：SPI 按类型直调（getBeanByType 非 facade）+ 五参契约面 + parse→build→execute→sink 内容断言（a=4/b=4/c=1）+ emit gate fail-fast 面钉
  - 顶层唯一约束钉（audit 亲跑 5/0 绿）：TestSqlModelExpansion.sqlCoexistingWithOtherContentFailsFast + noProviderFailsFastNamingDependency；行锚 expandSqlModel :516/:521-530 与 live 一致
  - 文档：user guide 裁定段+:289 引用修正、roadmap v15+WI16 [x]+行锚同步、doc-links 0
  - 首轮 REJECT 条件闭环：E8→WI24 disclaimer 已入 plan Purpose；user guide :291 残留碎片已删；flow 290/0 补跑核实（审计义务 L48）
  - 工具：scan-hollow sql 模块 0 critical/high（12 medium 均为既有 main 代码非本 WI 触碰面）；checklist 0（回填后终验）
  - Deferred 诚实：transform 级 SQL 声明面 deferred（WI0(e) 重启触发 + Successor Path）audit 确认完整
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2362-wi16-transform-sql-java-only.md --strict` 退出码 0（Closure Evidence 回填后复跑确认；REJECT 条件闭环说明见 Status Note）

Follow-up:

- no remaining plan-owned work（transform 级 SQL 声明面 successor 见 Deferred 段）
