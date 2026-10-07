# 2349 full-code-review 6.2 静默跳过/吞异常全仓扫描

> Plan Status: active
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.2（MA6）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 09）
> Related: Roadmap Details：「空 catch/吞异常/no-op 全仓清单，逐个裁定『合法忽略（带注释）』或『静默跳过违规』」；**上游移交**：4.1 报告（`4.1-error-handling-audit.md` :33/:240/:255-273/:492）——①`_tmp/fcr-4.1-catches.tsv` 3348 行全量分类 ②94 处抽样裁定全量清单在 `_tmp/fcr-4.1-swallow.md` §3（13 张表逐处 file:line+裁定；TSV 无已抽样标记列，join 按 file:line 不按 class——TaskDispatchCoordinator 在 swallow.md 记 B2A、TSV 记 B1_EMPTY）③close-ignore 36 处派生规则在 `_tmp/fcr-4.1-swallow.md:96`（rg 多行模式；**聚焦复核重放勘误：实为 8 个产品位点〔"36"系 rg 输出行数 9×4〕，其中 3 ∈ 94，层余数 5**）④U-1～U-6 逐项裁定建议（U-2 位点 TaskDispatchCoordinator.java:157-166 完整在案 4.1 :269）。字节级复核副本 `_tmp/fcr-4.1-catches.initial-reviewer-backup.tsv`（cmp 实测与主 TSV 一致）。关联 6.1（plan 2348）：no-op 返回值域由 6.1 承接，本项不重扫。
> 机械基线（主 agent 实测，2026-10-07，`_tmp/fcr-4.1-catches.tsv` 复算 + Draft Review agent_54898db3 逐项复核勘正 + 聚焦复核 agent_7e607a22 重放勘正）：3348 行 = OK1 615 + OK2 1529（合规 2144/64.0%）+ **B1_EMPTY 117 + B2A_IGNORE 228（=345，其中 94 处 4.1 已抽样裁定：立案 2〔ToolSchemaConverter:19/ChatBiTypeConverter:88，均在 94 内不在未抽样内〕+ 合法 88 + 不确定 4 = 94，其中 1 行带 U-2 备注；§3 有一行覆盖 2 位点〔:68/:437〕，位点数 95）+ B2B_USED_NO_EVID 734 + B2C_LOG_DEFICIENT 43 + C1_RETHROW_NO_CAUSE 82**。B2B 分布：nop-kernel 217/nop-persistence 136/nop-ai 82/nop-stream 76/…（4.1 :240，TSV 实测吻合）；B2B 734 跨 **261 个唯一文件**。scanner（6.1 口径）P3b 空 catch 产品命中 0。**close-ignore 勘误（聚焦复核重放）**：swallow.md:96 字面规则在 4.1 窗口重放 = **8 个产品 java 位点**（StreamingRecordDeserializer:59/67/436、SourceCoordinatorRegistry:78、FileDownloadSink:180、HttpInputFileBodyPublisher:51/63、ProgressFileEntityProducer:117，TSV 全部 B1_EMPTY）+ 1 个 ai-dev 审计 md 误命中；上游"36"实为 rg 输出行数（9 匹配×4 行）——本项以 8 位点为准，其中 3 位点 ∈ 94，close-ignore 层余数 = **5**。**裁定宇宙（公式，Phase 1 口径固化落盘精确清单）**：Agent 层裁定行 = (345 − 94 已裁 − U-6 4 行〔归 U 承接层单一所有权〕− close-ignore 交集行〔归 close-ignore 层单一所有权〕) + 734 + 43 + 82 + close-ignore 5 + U×6 位点；不重不漏，任何行单一所有者。
> 只读审计（P0 走异步注入）。**判级锚**：deep-audit 共享前缀 P0–P3（:329-333）；裁定二分法（roadmap Details 原文）：**合法忽略（带注释/带留证）vs 静默跳过违规**，违规者按共享前缀判级。**方法学裁定**：① 4.1 已抽样的 94 处不重裁（清单源 = swallow.md §3，按 file:line join TSV 提取，Phase 1 口径固化落盘）；② **B2B 734 全量逐行亲读裁定**（roadmap「逐个裁定」口径不缩窄：按文件聚类批读 catch 体 ±5 行，261 文件分批；复核员独立复判 ≥40 作 QA 锚，抽样六要素冻结脚本化）；③ no-op 返回值域由 6.1 承接（plan 2348 Phase 2 已 PASS、报告已落盘；**6.2 Phase 1 启动前置：plan 2348 已 completed 且 roadmap 6.1 行已 done**）；④ 窗口口径沿用 4.1（排除 src/test、target、`_gen`、`_` 前缀生成物、nop-lint、nop-benchmark、nop-rg、`_tmp/`）。
> Draft Review: agent_54898db3-698e-4378-9f38-ea0bf30b64c6 首轮 2 Blocker（B-1 B2B 速裁证据基础不可执行——TSV 九列无 catch 体语义且模式归类鸡生蛋 → 改全量逐行亲读批读；B-2 close-ignore 36 无数据源〔swallow.md:96 派生规则 plan 未引用〕且与 251 重叠无去重 → 派生命令写死+单一所有权+宇宙公式重算）+ 6 Major（M-1 94 清单提取源锚定 swallow.md §3+file:line join/M-2 U-6 四行双重裁定去重/M-3 撞车面 3.5→3.1 勘正+补 3.9 四处 duplicated-adjacent〔SocketClient:150=P3-3.9-014 still-live〕/M-4 roadmap「逐个」偏离与触发器不一致 → B-1 全量亲读后消解/M-5 「6.1 已闭包」虚假断言 → 前置门改写/M-6 In Scope 算术 1611→公式化+「251 中 2 立案」前提错误删除）+ 4 Minor（抽样六要素脚本/Agent 1 拆批/先提交/备份 TSV 路径+U-1 行号以 4.1 §5.5 为准 1181-1194）——本轮全部修复，待聚焦复核。

## Purpose

执行 roadmap 工作项 6.2：空 catch/吞异常/no-op 静默跳过全仓清单裁定，产出报告 `6.2-silent-skip-swallow-scan.md`。

## Current Baseline

- 4.1 TSV 分类在位（`_tmp/fcr-4.1-catches.tsv` + 字节级复核副本），七桶计数主 agent 与草审 double 复算吻合；94 处抽样裁定清单源 = `_tmp/fcr-4.1-swallow.md` §3；U-1～U-6 登记与裁定建议见 4.1 §5.5（U-1 行号 1181-1194 以 4.1 §5.5 为准）。
- 上游状态：6.1（plan 2348）Phase 2 复核 PASS、报告落盘、closure audit 进行中；本项 Phase 1 启动前置 = 6.1 完全闭包（见方法学裁定③）。
- 撞车面（Phase 2 必查，草审 M-3 勘正）：**4.1**（同域上游）；**3.1**（WorkflowEngineImpl 域 23 条发现 + U-1 位点）；**3.9**（SocketClient:150 = P3-3.9-014 [维度14-11] still-live 等 4 处 duplicated-adjacent——SocketClient:150 在 TSV B2B 桶内，裁定层须带防重复标记；OkHttpClientImpl:247/InvokeProcessor:215/:226/AbstractServerEventSubscription:147）；3.2（TaskDispatchCoordinator U-2 位点——3.2 报告未在案该类，对照价值有限保留）；3.11（敏感面域）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读；工作树在本 plan 提交后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.2-silent-skip-swallow-scan.md`：摘要 + 承接台账 + 裁定台账 + P0–P3 发现（`P<级别>-6.2-<序号>`）+ 驳回台账 + 裁决。
- **裁定覆盖（四层，宇宙公式见头部，Phase 1 落盘精确清单后任何行单一所有者）**：
  1. **B1+B2A 未抽样余量 100% 全裁**（= 345 − 94 − U-6 4 − close-ignore 交集 3，Phase 1 固化精确清单；每处亲读 catch 体：合法忽略〔带注释/留证〕vs 静默跳过违规；违规者判级）。
  2. **B2B 734 全量逐行亲读裁定**（按 261 文件聚类批读 catch 体 ±5 行，分批执行+中间落盘防截断；socketClient:150 带防重复标记）。
  3. **B2C 43 + C1 82 + close-ignore 5 = 130 处全量速裁**（C1 修复方向 4.1 已统一 `throw new NopXxx(ERR,e).param(...)`——本项确认现状与判级；close-ignore 8 位点按 4.1 建议批量改 IoHelper.safeClose* 可行性确认〔3 位点已在 94 内裁定，余 5 本层速裁〕）。
  4. **U-1～U-6 六项承接裁定**（核验 4.1 建议时点后位点现状漂移，给出 修复/维持/升级 三选一终裁建议移交 R1.0；U-6 四行由本层单一所有权终裁）。
- P0 即时通道；index 登记 + 独立 closure audit + roadmap 6.2 翻 done（**closure audit 先于翻 done**——5.3 教训；**且 6.1 已 done 为前置**）。

## Non-Goals

- no-op 返回值重扫（6.1 闭包后承接——见前置门）；4.1 已闭包的 A 面域（bare RT/IAE/ISE/策略面）与 94 处已抽样裁定；check 系列标定（6.4-6.9）；修复执行（R1.0/R1.x——U-1～U-6 与全部违规仅产出裁定建议）。

## Scope

### In Scope

- `_tmp/fcr-4.1-catches.tsv` 裁定宇宙全部行（七桶中 B1/B2A 余量 + B2B + B2C + C1）+ close-ignore 8 位点清单（3 已在 94 内，余 5 本项裁定）+ U-1～U-6 六位点
- 只读输入：4.1 报告全量 + `_tmp/fcr-4.1-swallow.md`（94 清单 §3 + close-ignore 派生规则 :96）、6.1 报告（no-op 域引用）、3.1/3.2/3.9/3.11 报告（撞车对照）

### Out Of Scope

- 测试代码 catch（4.1 窗口已排除）；4.1 A 面域；94 处已抽样重裁；修复执行。

## Execution Plan

### Phase 1 - 口径固化与四层裁定（4 agent 并行）

Status: planned
Targets: `_tmp/fcr-4.1-catches.tsv` 裁定宇宙 + U-1～U-6 位点

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始；**前置门：plan 2348 已 completed 且 roadmap 6.1 行 done**
- [ ] 口径固化：①宇宙公式实算落盘（94 清单从 swallow.md §3 按 file:line join TSV 提取；close-ignore 8 位点按 swallow.md:96 派生规则 + 4.1 窗口重放落盘清单〔聚焦复核重放：8 产品位点，其中 3 ∈ 94，余 5 归本层〕；U-6 四行、各层归属清单）②裁定表模板落盘 ③QA 锚抽样脚本 `_tmp/fcr-6.2-qa-sample.py`（六要素：python3 random.Random(seed=2349)/seed 2349/单位 file:line/分层=层归属/各层锚样本数 = min(40, 层行数)〔close-ignore 层 5、U 层 6 全量作锚〕/冻结 csv）④4.1 已立案 2 处（ToolSchemaConverter:19/ChatBiTypeConverter:88）防重裁标记
- [ ] Agent 1a「B1 全裁」：B1_EMPTY 未抽样余量逐处亲读（合法忽略需带注释或留证）
- [ ] Agent 1b「B2A 全裁」：B2A_IGNORE 未抽样余量逐处亲读（扣除 U-6 四行与 close-ignore 交集后清单）
- [ ] Agent 2「B2B 734 全量批读」：261 文件聚类按批亲读 catch 体 ±5 行逐行裁定（分批落盘防截断；SocketClient:150 带 P3-3.9-014 防重复标记；4 处 duplicated-adjacent 域命中标"已裁定归属"）
- [ ] Agent 3「130 速裁 + U×6 承接」：B2C 43 + C1 82 + close-ignore 5 全量速裁 + U-1～U-6 位点现状核验（4.1 建议三选一终裁建议；U-6 四行本层终裁）
- [ ] 主 agent 抽查质量：裁定抽验 ≥12 条独立复判 + 层间边界抽验（宇宙清单 vs 各 agent 实裁行数对账）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-6.2-swallow-audit.md`（四 agent 分节）+ 宇宙清单 + QA 锚冻结表

Exit Criteria:

- [ ] 初稿落盘，每条违规发现 ≥9 行含 file:line 与修复方向；裁定台账逐行含二分归属与理由
- [ ] 宇宙清单落盘（各层行数 + 单一所有权对账：Σ各层 = 宇宙总数，零重叠零遗漏）
- [ ] No owner-doc update required（只读——修复归 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：违规发现逐条 保留/降级/驳回 + 裁定台账抽验（≥15% 跨四层）+ B2B QA 锚独立复判 ≥40（重放 `_tmp/fcr-6.2-qa-sample.py` 冻结表）+ 撞车核对（4.1/3.1/3.2/3.9/3.11）+ 宇宙对账复算
- [ ] 主 agent 汇总：判级统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.2-swallow-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.2-silent-skip-swallow-scan.md`；index 登记 6.2 行 + P1 汇总行（如有）+ P2 裁决台账行（如开辟例外）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：宇宙对账与 QA 锚重放由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；**通过后** roadmap 6.2 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 裁定口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（P3b 空 catch 产品 0 已由 6.1 口径覆盖）

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且四层裁定全覆盖——覆盖度量：宇宙清单全量（B1/B2A 余量全裁 + B2B 734 全量批读 + B2C/C1/close-ignore 全量 + U×6），Σ各层 = 宇宙总数零重叠
- [ ] 每条违规发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.2 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；吞异常修复工单归 R1.0/R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
