# 2348 full-code-review 6.1 空壳实现全仓扫描

> Plan Status: draft
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.1（MA6）；Skill=`ai-dev/skills/deep-audit-prompts.md` + `ai-dev/tools/scan-hollow-implementations.mjs`
> Related: Roadmap Details：「全仓空壳扫描 + 抽样确认（区分在建模块的合法空壳如 nop-lint-nop 与违规空壳；nop-jq 已于 2026-09-28 建成并独立成组，不再是空壳例证）」；关联 6.2（静默跳过/吞异常——空 catch 域归 6.2，本项不重扫）。
> 机械基线（主 agent 实测，2026-10-07，scanner JSON `_tmp/fcr-6.1-hollow-full.json` 全量 6118 findings/9 模式，按「产品 src/main/java 且排除 _tmp/ai-dev/deepwiki/_dump/target」过滤）：**high/critical 产品命中 13 条**（P1:high UnsupportedOperationException ×8：`DynCountArrayBinaryCodec.java:42/:65`、`TdMessageService.java:21/:26`、`GeometryTypeHandler.java:91`、`ChunkFileUploadHandler.java:19/:23/:30`；P6b:high "not yet implemented"/placeholder 注释 ×5：`ReActAgentExecutor.java:1327/:1456`、`PlanReplanner.java:273`、`NoOpProviderFailoverQueue.java:34`、`JavaEmailSender.java:216`）；P1:medium UOE 76 条；P2b:medium 单行 return null/0/false 2424 条；P6:medium 未用公共方法 645 条；P8:medium stub/placeholder 命名 1151 条；P4:low TODO/FIXME 44 条（产品域）。scanner 全量口径含 `_tmp` 探针文件与审计 control fixtures（`HollowControlFixture`、`_tmp/probe/stub-src` 等 7 条为非产品噪音，过滤规则固化于 Phase 1）。
> 合法在建空壳基线：`nop-lint/nop-lint-nop`（1 java，roadmap 点名合法在建）；nop-jq 已建成独立成组（1.9 已深审，非空壳例证）。历史撞车面：4.3 巨型文件审计（拆分裁定域）、4.2 测试有效性（TestStringHelper 等零断言域）、3.2/3.3（nop-batch/task 深审已裁定的死代码/恢复族）、5.2（草稿态文档域）。
> 只读审计（P0 走异步注入）。**判级锚**：deep-audit 共享前缀 P0–P3（:329-333）；scanner 自带 P1/P2/P3/P4/P6/P6b/P8 模式严重级仅作初筛，最终判级以共享前缀为准。**方法学裁定**：① scanner 模式语义锚 = 工具头注释（P1 UOE stub-style 违规 vs guard-style 合法 fail-fast；P2b 单行返回需逐条判断）；② 「排除自身模块 vs 含自身测试面」零引用口径必须显式声明（E7 教训——plan 2347 closure audit）；③ E7 结构性盲区借鉴：本项 scanner 口径天然含自身测试面（codeClass 字段区分 main/test），抽样裁定覆盖两侧。
> Draft Review: （待独立子 agent 对抗审查）

## Purpose

执行 roadmap 工作项 6.1：全仓空壳实现扫描 + 人工抽样确认，产出报告 `6.1-hollow-implementation-scan.md`。

## Current Baseline

- 机械基线见头部注记（13 high / 76 P1:medium / 2424 P2b / 645 P6 / 1151 P8 / 44 P4，产品域口径）。
- 合法空壳先例：nop-lint-nop（在建）；nop-jq 已建成；guard-style UOE（"not supported"/"only available on"）为平台认可的 fail-fast 模式非空壳。
- 绿色基线：编制期全仓 BUILD SUCCESS（2026-09-22）；本项只读；工作树在 5.3 收口 commit（7546b4b8ea）后干净。
- 撞车面（Phase 2 必查）：3.2/3.3/3.4（batch/task 深审已裁定项）、4.2（测试反模式）、4.3（巨型文件）、4.1（错误处理——NoOpProviderFailoverQueue 吞异常面若涉 catch 归 6.2）、5.2（文档草稿态）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.1-hollow-implementation-scan.md`：摘要 + scanner 口径台账 + 裁定台账 + P0–P3 发现（`P<级别>-6.1-<序号>`）+ 合法空壳清单 + 驳回台账 + 裁决。
- **裁定覆盖（三层）**：
  1. **high 13 条 + P1:medium UOE 76 条 = 100% 全裁**（每条：stub-style 违规空壳 / guard-style 合法 fail-fast / 注释误命中三分裁定；违规者按共享前缀判级）。
  2. **P2b 2424 + P6 645 + P8 1151 = 分层抽样 ≥240 条**（按模块组分层，高优模块〔nop-ai/nop-biz/nop-wf/nop-orm/nop-stream/nop-report〕加密；每条同三分裁定；抽样表在报告中固化可复现）。
  3. **合法在建空壳清单核定**：nop-lint-nop 全读 + 全仓在建/占位模块盘点（Roadmap 8 边界：功能开发类空壳归各自 roadmap，本项仅登记不判缺陷）。
- P0 即时通道；index 登记 + 独立 closure audit + roadmap 6.1 翻 done。

## Non-Goals

- 空 catch/吞异常域（6.2）；check 系列发现的标定（6.4–6.9）；修复执行（R1.0/R1.x）；测试有效性域（4.2 已闭包）；文档空壳（5.2 已闭包）。

## Scope

### In Scope

- 全仓产品 `src/main/java`（scanner 全量口径 + 抽样裁定）；合法在建空壳清单核定
- 只读输入：scanner JSON `_tmp/fcr-6.1-hollow-full.json`、3.2/3.3/3.4/4.1/4.2/4.3 报告（撞车对照）

### Out Of Scope

- 测试代码空壳（scanner test 命中仅登记中性台账，归 4.2 域）；`_tmp/ai-dev/deepwiki/_dump/target` 非产品路径；修复执行。

## Execution Plan

### Phase 1 - 全量口径固化与三层裁定（3 agent 并行）

Status: planned
Targets: 产品 src/main/java 全仓

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 口径固化：scanner 重跑（`--severity low --format json`）+ 产品过滤规则写死（`/src/main/java/` + 排除前缀清单）+ 分层抽样表生成（可复现：seed 固定）
- [ ] Agent 1「high+P1 全裁」：13 high + 76 P1:medium 逐条亲读裁定（含测试面核对：该路径是否有测试触达）
- [ ] Agent 2「P2b+P6 抽样」：分层抽样 ≥180 条（P2b ≥120 + P6 ≥60），高优模块加密，逐条三分裁定
- [ ] Agent 3「P8+P4+合法空壳」：P8 抽样 ≥60 + P4 44 全量速裁 + nop-lint-nop 全读 + 在建模块盘点
- [ ] 主 agent 抽查质量：裁定抽验 ≥10 条独立复判 + 抽样表口径重放 ≥3 组
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-6.1-hollow-audit.md`（三 agent 分节）

Exit Criteria:

- [ ] 初稿落盘，每条违规发现 ≥9 行含 file:line 与修复方向；裁定台账每条含三分归属与理由
- [ ] 抽样表固化（模块分布 + 抽样数 + 选取规则可复现）
- [ ] No owner-doc update required（只读——修复归 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：违规发现逐条 保留/降级/驳回 + 裁定台账抽验（≥15% 跨三层）+ 抽样口径重放（≥3 组）+ 撞车核对（3.2/3.3/3.4/4.1/4.2/4.3）
- [ ] 主 agent 汇总：判级统一（P0–P3 共享前缀）、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.1-hollow-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.1-hollow-implementation-scan.md`；index 登记 6.1 行 + P1 汇总行（如有）+ P2 裁决台账行（如开辟例外）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：抽样口径重放由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——**报告判定完成前不预设 roadmap 翻 done，杜绝 5.3 顺序偏差复发**）；通过后 roadmap 6.1 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 抽样口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：本项即该工具的全仓审计执行本身，无新增产品代码

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且三层裁定全覆盖——覆盖度量：high+P1 100%（89 条）+ 抽样 ≥240 条 + 合法空壳清单
- [ ] 每条违规发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.1 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；空壳修复工单归 R1.0/R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
