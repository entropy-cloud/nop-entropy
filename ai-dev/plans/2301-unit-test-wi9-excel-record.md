# 2301 unit-test-coverage-roadmap WI9 — nop-format 第一批（nop-excel / nop-record）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI9 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-excel（16.44%）模型解析/公式计算/导出（golden 快照模式）补强；nop-record 二进制编解码 roundtrip 补强（59.17%，已达标但 roundtrip 语义面增补）。

## Current Baseline

- nop-excel 16.44% 行 / 14.79% 分支 / 4604L；nop-record 59.17% / 4519L（已达标 ≥30%）。
- 既有测试：excel 11 个测试文件、record 24 个——写法参照。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi9-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-excel：≥15 个语义用例（xlsx 模型解析、公式模型解析/格式转换语义——模块内无公式求值引擎（求值在 POI/nop-report 侧），不作靶点、导出输出 golden 快照——按 testing.md「XPL Tag 输出 Golden JSON 快照」惯例，录入方法 @Disabled）。
- nop-record：≥6 个 roundtrip 用例（encode→decode 恒等、边界值、类型宽度）。
- 两模块测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；不做真实 Office 套件兼容性 E2E。

## Scope

### In Scope

- `nop-format/nop-excel|nop-record/src/test/**` 与 golden fixtures（test resources）。

### Out Of Scope

- 产品代码、pom；nop-format 其他模块（WI10）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: completed
Targets: `nop-format/nop-excel|nop-record/src/test/**`

- Item Types: `Fix`

- [x] excel 28 活动用例 + 1 @Disabled 录入（ExcelDateHelper 序列日期/ExcelFormatHelper/UnitsHelper/FileMagic/PredefinedColors/PageMargins/公式模型解析/导出 golden 2 场景）。
- [x] record 31 用例（BinaryWordType roundtrip/EOL/LV/FLS codec、XOR/zlib helper、模型级 roundtrip、RecordTemplateManager 语义）+ 3 个 record-template fixtures（仓库首个该类型样例）。
- [x] 两模块 `mvnq -- test -pl :<module> -am -fae` 全绿（excel 76 / record 192 tests，既有零回归）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 15 测试类 / 59 活动用例 + golden fixtures 入库（≥21）。
- [x] 两模块测试全绿。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI9 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删两模块 exec → baseline 脚本复测（label wi9-2026-10-02）：excel 16.44%→31.06%（+14.62，**首次达标 ≥30%**）、record 59.17%→64.48%（+5.31）；excel 低覆盖 34→24、record 11→5。
- [x] 残余缺口显式裁定（Deferred 段：excel imp 导入链需真实 xlsx fixture、chart 子系统、reader/IO 边角）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI9 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI9 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 两模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码/pom 零修改（git 证据：仅 src/test 新增）
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：导出/编解码测试断言产出内容而非仅调用（audit 抽查：golden 全结构比对+关键字段断言、u2be/u2le 字节反序与 IEEE754 位模式逐字节校验）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2301-unit-test-wi9-excel-record.md --strict` 退出码 0
- [x] roadmap WI9 checkbox 与 plan/log 一致

## 执行偏差记录

1. golden 录入：@Disabled 录入方法无法被 surefire 执行，临时移除 @Disabled 运行一次、复制 target/golden 产物后立即恢复——最终入库状态符合 testing.md 惯例。
2. 修复断言期间的中间调试轮次部分用 `-pl :nop-record -fae` 省时；两模块最终证据运行均为 plan 指定命令且退出码 0。

## Deferred But Adjudicated

### nop-excel 残余 24 类 / nop-record 残余 5 类

- Classification: `optimization candidate`（WI13 复裁）
- Why Not Blocking Closure: 两模块均已达 periphery 30% 目标（excel 首次达标）。excel 残余主体为 imp 导入链 5 类（需真实 xlsx 二进制 fixture，约 450 行）、chart 子系统 5 类、未测常量枚举 4 类（可低成本补齐）；record 残余 5 类全部为 reader/IO 分块缓冲边角（29.8%-37.5%），属 periphery 残余。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 7 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi9-defect-suspects.md）：UnitsHelper FixedPoint 负数不对称、parseYYYYMMDDDate 分隔符/lenient 归一化、PredefinedColors 索引冲突、FLS 空值缺省不一致、RecordTemplateManager vars 防御性拷贝、文本路径类型反推缺失、record-template 记录级 generator 疑似死代码。

## Closure

Status Note: 提交 amend 后纯净（无并行 WI10 夹带、无产品/pom 改动），15 测试类/59 用例全绿（76/192），excel 31.06%/record 64.48% 与 jacoco XML 逐位一致且双达标，golden 与字节级 Anti-Hollow 抽查通过，7 项缺陷嫌疑有源码依据。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_8542bbed-98a1-44d1-a1a4-d9a24b587bd6，fresh session）

Follow-up:

- （待填写）
