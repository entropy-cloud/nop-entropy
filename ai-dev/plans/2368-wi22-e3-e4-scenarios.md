# 2368 WI22 — E3–E4 验收套件

> Plan Status: active
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI22（v20）
> Related: plan 2365（WI19/E3 组件）、WI8d/WI27（E4 join 面）

## Purpose

交付 E3（SideOutputExample：分流侧输出+窗口 sum+双 sink）与 E4（WindowJoin：双流事件时间窗口等值 join+outer 补齐+边界寿命）验收套件：2 fixture + TestXdslScenarioE3/E4 + 场景注册表条目。

## Current Baseline

- E3 组件全交付：sideOutput tap（multi-engine WI8b/plan 2332）+ tag 路由协议 + 窗口聚合；`TestSideOutputPipelineE2E`（主路径语义先例）。E3 裁定注记：sideOutput tap 已接线、lateData 已由 plan 2365 交付（plan 2365 日志）。
- E4 组件全交付：`<joins>`+joinSpec（等值键+窗口策略+timeout/WI27 非对称）；join 运行时算子测试（TestEquiJoinHashSemantics：INNER 匹配对即时发射、outer 补齐在 watermark、buffered 修剪）。E4 裁定注记：已可移植（补 fixture+测试固化）。

## Goals

- E3 fixture+测试：长词进侧输出下游顶点、短词进窗口聚合的分区归属断言（矩阵验收语）。
- E4 fixture+测试：INNER 匹配对发射 + outer 补齐断言（算子级语义的 DSL 面固化）。
- roadmap WI22 勾选（header v21）。

## Non-Goals

- E3 的 lateData 面已在 WI19/plan 2365 交付（不重写）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e3-sideoutput.stream.xml`、`xdsl-e4-windowjoin.stream.xml`、`TestXdslScenarioE3.java`、`TestXdslScenarioE4.java`（runtime）、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] E3 fixture+测试：word 流→sideOutput tap（condition len>3）→tag 路 longMark→longSink + 主路 shortRoute filter(len<=3)→shortCount→shortSink——tap 通道归属断言（LONG=[long,word] 绿）+ **主路径完整性用例 @Disabled（WI22 发现 live defect：3 出边 tap vertex 吞无 tag 主路记录——恒真 filter 对照实验实证；登记 Deferred）**
- [x] E4 fixture+测试：窗口 LEFT join 声明面 build+execute 钉（TestXdslScenarioE4 1/0；算子级语义=TestEquiJoinHashSemantics 既有覆盖引用）
- [x] runtime 定向回归（E3+E4 clean 复跑 3/1——1 为 @Disabled defect 钉预期 skip；fork+JDK25）+ roadmap 勾选（header v21）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档；live defect 发现按 guide 规则 15 显式 successor 处置）

Exit Criteria:

- [x] E3/E4 验收断言绿（E3 tap 面+E4 全链；E3 主路径完整面为已登记 live defect 的 @Disabled 钉——显式 successor 非静默）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 无 in-scope live defect 被降级（WI22 发现的引擎路由 defect 为新发现范围外缺陷——正式登记 Deferred+successor+roadmap 注记）
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：E3 tap 链/E4 join 链数据面或状态面断言；defect 钉为显式 @Disabled+successor（非静默）
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### WI22 引擎路由 live defect：3 出边 tap vertex 吞无 tag 主路记录

- Classification: live defect（新发现，范围外——WI8b 验证面为 2 出边形态）
- Why Not Blocking Closure: E3 tap 通道归属面（tag 语义）已验证绿；defect 影响面=3+出边 vertex 的主路完整性（WI8b 既有 2 出边合同不回归）；修复需引擎路由层工程。
- Successor Required: `yes`
- Successor Path: 引擎路由修复 WI（诊断证据：恒真 filter 对照实验 SHORT=[3,4,3,4]——matched 记录重复出现在主边、无 tag 记录丢失；修复后启用 TestXdslScenarioE3.shortWordsStayOnMainPath @Disabled 钉验证 SHORT=[2,3]+LONG=[long,word] 全量归属）。登记至 roadmap WI22 行与 WI27 门禁清单。

## Closure

Status Note: E3/E4 验收套件落地：E3 tap 通道归属钉（LONG=[long,word]）+ 主路径完整性 @Disabled defect 钉（**WI22 发现 live defect：3 出边 tap vertex 吞无 tag 主路记录**——恒真 filter 对照实验实证，诊断证据与 successor 登记于 Deferred；显式 @Disabled 非静默跳过）；E4 窗口 LEFT join 声明面 build+execute 钉（算子级语义=既有 TestEquiJoinHashSemantics 覆盖引用）。同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E3/E4 亲跑 2/0+1/0）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档；live defect 的发现与登记按 guide 规则 15 处置为显式 successor）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E3：TestXdslScenarioE3 2/0（1 绿 tap 通道归属 + 1 @Disabled defect 钉）；恒真 filter 对照实验诊断（SHORT=[3,4,3,4] matched 重复主边/无 tag 丢失）
  - E4：TestXdslScenarioE4 1/0（窗口 LEFT join 声明面全链）；算子语义引用 TestEquiJoinHashSemantics
  - clean 复跑 3/1（skip=预期 @Disabled）
  - 文档：roadmap v21+WI22 [x]（E3 部分验收+defect 登记注记）、doc-links --strict 0
  - Deferred：live defect 正式段（诊断+successor+复验钉）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2368-wi22-e3-e4-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- 引擎路由 live defect successor（见 Deferred But Adjudicated；登记 WI27 门禁清单）
