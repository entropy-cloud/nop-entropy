# 367 nop-deepwiki 调研吸收落地：引用全量机检、mermaid 可选渲染校验、密度硬约束与存量页重生成

> Plan Status: active
> Last Reviewed: 2026-09-30（依赖收敛裁定：mermaid/jsdom 入 ai-dev/tools 既有 pnpm 根管理，废弃项目外工具引用）
> Draft Review: 一轮独立子 agent 对抗性审查（2026-09-29）：0 Blocker + 2 Major（nop-xlang 第四 wiki 未裁决、fixture git 前提不成立）+ 4 Minor，全部修复后转 active
> Source: ai-dev/logs/2026/09-29.md（deepwiki.com 对标测量 + 开源项目调研）、ai-dev/plans/363-nop-deepwiki-structure-and-consumption-upgrade.md（completed，前置）
> Related: deepwiki/analysis/2026-09-26-multitype-gap-analysis.md（密度基线来源）

## Purpose

把 2026-09-29 开源调研中裁定可吸收的机制落地到 nop-deepwiki skill（引用写前全量机检、mermaid 真实解析校验、skill 自检 fixture、代码块密度硬约束），并用存量 nop-task/nop-batch 不达标页重生成做端到端验证，收口"代码块/mermaid 密度低于 deepwiki.com 基线"的已确认质量差距。

## Current Baseline

（2026-09-29 live 核实）

- **引用行号机检覆盖不全**：gen-wiki-meta 重写松格式引用时不校验行号边界；check-wiki 的 verify-claims 仅抽样 20——抽样池外的越界行号锚（gitee 上指向不存在行）不被发现。deepwiki-open 的对照机制是写前重接地（`api/services/codemap.py:186` `_ground_citations`）；松格式引用不含 snippet 无法重定位，本计划采边界校验+降级方案。
- **mermaid 校验只有正则**：check-wiki 仅校验块首行图表类型白名单，无真实解析。openwiki 对照机制为无头 `mermaid.parse` + 解析失败转 text fence（`src/mermaid/validate.ts`）。依赖裁决（2026-09-30 用户裁定，取代本行前版的"可选增强"口径）：工具依赖收敛在仓库内既有 pnpm 根 `ai-dev/tools/`（`package.json` 增 mermaid + jsdom，node_modules 不入库，克隆后 `cd ai-dev/tools && pnpm install` 一次启用；不得另建第二个 pnpm 根）；**禁止引用项目外工具脚本路径**（既有例外仅 mission driver 的 AGE 模板）；裸 `import('mermaid')` 从脚本目录链解析不到、已废弃，check-wiki 改为从 `ai-dev/tools/node_modules` 显式解析（完整构建优先、core 回退）+ jsdom 全局注入后无头 parse，缺失时显式跳过并回退正则白名单。
- **密度硬约束缺代码块维度**：SKILL.md 仅有表格 ≥2、模块页 mermaid ≥3；逐页实测（2026-09-29 live，≥3 阈值复核口径以逐页清单为准）：
  - nop-task 不达标 6 页：architecture(0)、flows/task-execution(0)、flows/state-and-recovery(0)、modules/task-core(0)、modules/task-service-dao(1)、topics/error-model(0)
  - nop-batch 不达标 5 页：architecture(0)、flows/batch-pipeline(0)、flows/checkpoint-recovery(0)、modules/batch-dsl(0)、topics/error-model(0)
- **skill 无可重复自检**：2026-09-25 的 fixture 验证（`_tmp/deepwiki-fixture`）是一次性的，_tmp 已清。
- 现网三 wiki（nop-task/nop-batch/nop-orm）check-wiki `--strict` 全绿；源码引用为 gitee blob 永久链接（commit ea3e35e6d0 基线，后续随 gen 重跑前移）。
- **deepwiki/nop-xlang/ 为第四个 wiki**（并行会话新增并入库，12 页），当前 check `--strict` 1 ERROR / 5 WARN（reading-guide 空括号死链 + 5 页纯文本 Sources）。本计划不纳入（见 Deferred 裁决），Closure Gate 的"三 wiki 全绿"仅指本计划触碰的三个 wiki。

## Goals

- G1 Sources 松格式引用在重写时做全量行号边界机检（覆盖 ROOTABS 迁移与松格式两条重写路径、全部行区段），越界锚显式降级为文件级链接并汇报（不做静默）。正文裸断言（`path:line` 非链接形态）仍由 verify-claims 抽检覆盖，不在本项范围。
- G2 check-wiki 具备真实 mermaid 解析校验（依赖可用时），不可用时显式说明跳过；两种路径都有输出。
- G3 skill 交付物自带可重复自检（fixture + selftest 脚本），验收不再依赖一次性 _tmp。
- G4 内容密度硬约束补齐代码块维度；存量两 wiki 11 个不达标页重生成后，两 wiki `--strict` 恢复全绿且密度达到基线带。

## Non-Goals

- nop-code 符号索引接入 Phase 2（需 live 索引服务，见 Deferred）。
- Claims 断言级增量、CI 自动更新、MCP/llms.txt、后台增量托管（调研登记为远期产品形态项）。
- 两 wiki + nop-orm 中已达标页（modules/batch-core、topics/assembly-interceptors、quickstart、overview、glossary、reading-guide）的重生成。
- PLAN.md 结构变更/页面增删（沿用各 PLAN.md 既有页面契约：路径/标题/职责不变）。

## Scope

### In Scope

- `.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`
- `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`
- `.opencode/skills/nop-deepwiki/scripts/selftest.mjs`（新增）
- `.opencode/skills/nop-deepwiki/scripts/selftest-fixture/`（新增：迷你 wiki + 迷你目标仓库）
- `.opencode/skills/nop-deepwiki/SKILL.md`
- `deepwiki/nop-task/`：architecture、flows/task-execution、flows/state-and-recovery、modules/task-core、modules/task-service-dao、topics/error-model（6 页重生成）
- `deepwiki/nop-batch/`：architecture、flows/batch-pipeline、flows/checkpoint-recovery、modules/batch-dsl、topics/error-model（5 页重生成）
- `deepwiki/nop-orm/`：architecture、flows/entity-lifecycle、flows/query-pipeline、modules/session-factory、modules/persister-sql（5 页重生成；2026-09-29 范围变更：Phase 3 新规则下该 5 页代码块密度同样不达标，实测 0/0/0/1/2——不纳入则 nop-orm 在新门禁下 strict 常红，Closure Gate 无法自洽；topics/assembly-interceptors 实测 5 个代码块达标不重生成）
- `deepwiki/README.md`、`ai-dev/logs/2026/09-29.md`

### Out Of Scope

- Java/Maven 代码（本计划零 Java 变更，构建验证以 node --check + selftest + check-wiki 替代 ./mvnw）
- docs-for-ai/（skill 属 .opencode 工具面，deepwiki 产出不入 docs-for-ai 为既定边界）

## Execution Plan

### Phase 1 - gen-wiki-meta 引用行号全量边界校验

Status: completed
Targets: `scripts/gen-wiki-meta.mjs`

- Item Types: `Fix`

- [x] 重写每条带行号引用前校验目标文件实际行数：**ROOTABS 迁移与松格式两条重写路径都过校验；行区段校验全部区段（多区段引用任一区段越界即降级）**；越界时降级为不带锚的文件级链接，并逐条汇总输出"行号越界降级"清单（页面+路径+原行号），退出摘要含计数
- [x] 越界降级不阻断其余重写（迁移/重写统计口径不变）

Exit Criteria:

- [x] 对含越界行号引用的临时页面运行 gen：越界条目降级为文件级链接、降级清单与计数可见（合成用例：`pom.xml:99999-100001` 降级 + 多区段 `20-29,88888` 整条降级，报告 2 条）
- [x] 现网三 wiki 重跑 gen：降级计数输出且无异常（0 条越界），重写/迁移统计口径与改动前一致
- [x] `node --check` 通过
- [ ] No owner-doc update required（SKILL.md Phase 5 对 gen 职责的描述若因本项产生出入，同行内更新）
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 2 - check-wiki mermaid 真实解析校验（可选增强）+ skill 自检固化

Status: completed
Targets: `scripts/check-wiki.mjs`、`scripts/selftest.mjs`（新增）、`scripts/selftest-fixture/`（新增）

- Item Types: `Fix` + `Proof`

- [x] check-wiki 增 mermaid 解析校验：动态 import('mermaid') 成功则对每个 mermaid 块在 try/catch 内 parse——**语法解析失败报 ERROR（含页面与块定位）；模块缺失或环境性失败（如缺 DOM）则输出一行"跳过渲染校验（原因）"**——三条路径都有显式输出，环境性失败不得误报为坏块 ERROR，无静默（2026-09-30 依赖路径升级：加载改为 `ai-dev/tools/node_modules` 显式解析 + jsdom 全局注入，真解析实启用，见 Exit Criteria 追加行）
- [x] 新增 selftest.mjs：对 selftest-fixture 运行 gen（实跑）+ check `--strict`，断言预期检出清单与退出码；fixture 覆盖：越界行号引用、空括号死链、坏 mermaid 块、纯文本 Sources、正常页（五类）
- [x] fixture 迷你目标仓库**由 selftest 运行时复制到 `_tmp/` 并 `git init`（不配 origin）**——否则 gen 会命中本仓库 gitee origin 走 github 模式、topLevel 错位；断言按相对链接模式书写

Exit Criteria:

- [x] `node .opencode/skills/nop-deepwiki/scripts/selftest.mjs` 退出码 0，五类缺陷全部被断言检出（14/14 断言，含 fixture 自身越界引用被边界机检抓出的实例）
- [x] 现网三 wiki 跑 check：mermaid 路径输出跳过提示（依赖不可用），其余结果保持 0 ERROR 0 WARN 不变
- [x] **2026-09-30 追加（ai-dev/tools 依赖收敛 + 真解析实启用）**：mermaid 11.17.2 + jsdom 26.1.0 并入 `ai-dev/tools/package.json` 既有 pnpm 根（初版误在根 tools/ 另建 pnpm 根，同日裁正并入）；check-wiki 加载改为 `ai-dev/tools/node_modules` 显式解析（`dist/mermaid.esm.min.mjs` 优先、core 回退）+ jsdom 全局注入（openwiki dom-shim 同做法）；四 wiki（task/batch/orm/xlang）存量 65 个 mermaid 块全部真实解析通过；坏块 fixture 报 `Mermaid 解析失败（块 1）：Parse error...` ERROR；模拟无 ai-dev/tools/node_modules 环境输出显式跳过行、无误报；解析失败时通过计数不再误报"全部通过"（0/1 修正输出）
- [x] 两脚本 `node --check` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 3 - SKILL.md 代码块密度硬约束

Status: completed
Targets: `SKILL.md`、`scripts/check-wiki.mjs`

- Item Types: `Fix`

- [x] Phase 4 派发模板增硬约束：内容页（architecture/flows/modules/topics）≥3 个源码摘录代码块（每段 5–20 行、来自本页映射源文件真实行段，附 `路径:行段`）；overview 定位页豁免，glossary/reading-guide/quickstart 豁免
- [x] check-wiki 密度 WARN 增代码块计数（豁免面与模板一致，阈值 3，`--min-code-excerpts` 可调）
- [x] 最终检查清单增对应条目；模板/检查清单/检查器三处口径一致

Exit Criteria:

- [x] check-wiki 现网输出与新规则一致：不达标页 WARN 可见（nop-task 6 / nop-batch 5 / nop-orm 5，与逐页清单吻合）、达标页（modules/batch-core、topics/assembly-interceptors）无新增 WARN
- [x] SKILL.md 三处口径一致（人工核对）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 4 - 存量不达标页重生成（端到端验证）

Status: planned
Targets: `deepwiki/nop-task/`（6 页）、`deepwiki/nop-batch/`（5 页）、`deepwiki/nop-orm/`（5 页；范围变更见 In Scope）

- Item Types: `Fix` + `Proof`

- [ ] 按 Phase 3 新模板派发子代理重生成 16 页（沿用各 PLAN.md 页面契约：路径/标题/职责/互链不变，保留既有正确内容，补 ≥3 源码摘录代码块；architecture 页符号级图）；并发触发配额限制时按 SKILL 纪律降级串行重试
- [ ] 三 wiki 重跑 gen + check `--strict` + `verify-claims 20 --seed 42`
- [ ] 密度复测：16 页代码块 ≥3、三个 architecture 页含符号级图；改前/改后对比数字记入日志

Exit Criteria:

- [ ] 三 wiki check `--strict` 0 ERROR 0 WARN；verify-claims 0 ERROR
- [ ] 16 页密度复测达标（量化数字留档日志）
- [ ] `deepwiki/README.md` 状态行同步
- [ ] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> 纯 node 脚本/文档计划，无 Java 变更：`./mvnw` 构建验证不适用，以下列替代验证。

- [ ] 四个 Phase 全部 completed，Exit Criteria 全勾
- [ ] **端到端验证**：新管线（gen 越界降级 → check 新密度 WARN/渲染校验路径 → selftest → 存量页重生成）在 Phase 4 真实重生成流程中完整跑通
- [ ] 三 wiki check `--strict` 全绿（含 verify-claims 抽检 0 ERROR）
- [ ] 无 in-scope live defect 被降级为 deferred/follow-up
- [ ] 受影响 owner docs（deepwiki/README.md、ai-dev/logs/）已同步；docs-for-ai 明确不需要更新
- [ ] 独立子 agent closure audit 完成并把证据写入本文件 Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0

## Deferred But Adjudicated

### nop-code 符号索引接入 Phase 2

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 live nop-code 索引服务与 GraphQL 面；收益在生成前结构提取精度（文件级 fan-in → 符号级），不影响本计划的门禁收口与密度达标
- Successor Required: `yes`
- Successor Path: 未立项（登记于本计划，后续按需立项）

### deepwiki/nop-xlang wiki 的链接与密度治理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该 wiki 由并行会话新增并入库，不属于本计划触碰的三个 wiki；其 1 ERROR / 5 WARN 为链接类机械缺陷（同 2026-09-29 已修复的类别），密度治理需按新规则重生成其内容页——两者均不影响本计划"三 wiki 全绿"门禁的成立
- Successor Required: `yes`
- Successor Path: 未立项（登记 Non-Blocking Follow-ups，随 nop-xlang 下次增量更新一并处理）

### Claims 断言级增量 / CI 自动更新 / MCP 暴露 / 后台增量托管

- Classification: `out-of-scope improvement`（远期产品形态项，调研已留档 ai-dev/logs/2026/09-29.md）
- Why Not Blocking Closure: 工程量大且不改变当前静态 wiki 契约的成立；产品形态项此前已裁定 Deferred
- Successor Required: `no`

## Closure

Status Note: （执行完成后填写）
Completed: （日期）

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）

Follow-up:

- （待填写）
