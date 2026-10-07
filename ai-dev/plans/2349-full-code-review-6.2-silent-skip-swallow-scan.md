# 2349 full-code-review 6.2 静默跳过/吞异常全仓扫描

> Plan Status: draft
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.2（MA6）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 09）
> Related: Roadmap Details：「空 catch/吞异常/no-op 全仓清单，逐个裁定『合法忽略（带注释）』或『静默跳过违规』」；**上游移交**：4.1 报告（`4.1-error-handling-audit.md` :33/:492）——①`_tmp/fcr-4.1-catches.tsv` 3348 行全量分类（B1 空 catch 117 + B2A 不记录不抛 228 = 违规两桶 345，其中 94 处已抽样裁定〔立案 2/合法 88/不确定 4+1〕，**未抽样 251 处待本项全裁**；外延 B2B 734/B2C 43/C1 82）②手写 close-ignore 36 处 ③U-1～U-6 逐项裁定建议 ④C1 82 处修复方向统一。关联 6.1（no-op 返回值域已由 6.1 P2b 抽样覆盖——引用其结果不重扫）。
> 机械基线（主 agent 实测，2026-10-07，直接读 `_tmp/fcr-4.1-catches.tsv` 复算）：3348 行 = OK1 615 + OK2 1529（合规 2144/64.0%）+ **B1_EMPTY 117 + B2A_IGNORE 228（345）+ B2B_USED_NO_EVID 734 + B2C_LOG_DEFICIENT 43 + C1_RETHROW_NO_CAUSE 82**。scanner（6.1 口径）P3b 空 catch 产品命中 0。B2B 分布：nop-kernel 217/nop-persistence 136/nop-ai 82/nop-stream 76/…（4.1 :240——主流形态为「catch→转 ErrorCode/错误响应」边界转换，多数合规）。
> 只读审计（P0 走异步注入）。**判级锚**：deep-audit 共享前缀 P0–P3（:329-333）；裁定二分法（roadmap Details 原文）：**合法忽略（带注释/带留证）vs 静默跳过违规**，违规者按共享前缀判级。**方法学裁定**：① 4.1 已抽样的 94 处不重裁（引用其裁定，U-1～U-6 与 2 处立案按 4.1 建议承接核验现状）；② B2B 734 采用「TSV head 台账速裁 + 亲读锚定抽样 ≥80」混合法（B2B 主流为边界转换模式，速裁按模式归类，抽样锚定模式判断准确率，锚定样本违规率 >10% 则升级全裁）；③ no-op 返回值域引用 6.1（plan 2348）P2b 抽样结果（300 条样本 a 类仅 1），不重扫；④ 窗口口径沿用 4.1（排除 src/test、target、`_gen`、`_` 前缀生成物、nop-lint、nop-benchmark、nop-rg、`_tmp/`）。
> Draft Review: （待独立子 agent 对抗审查）

## Purpose

执行 roadmap 工作项 6.2：空 catch/吞异常/no-op 静默跳过全仓清单裁定，产出报告 `6.2-silent-skip-swallow-scan.md`。

## Current Baseline

- 4.1 TSV 分类在位（`_tmp/fcr-4.1-catches.tsv` + 字节级复核副本），六桶计数主 agent 已复算吻合；94 处抽样裁定与 U-1～U-6 登记见 4.1 §5.5。
- U-1～U-6 均为 4.1「不立案 + 6.2 裁定建议」状态：U-1（WorkflowEngineImpl:1181-1194 收窄匹配码+debug 升 warn）/U-2（补 interrupt 恢复）/U-3（DockerSandboxBackend 空表语义专项确认）/U-4（ProgressFileEntityProducer.failed 留证）/U-5（XLangDebugProcess 连接失败留证）/U-6（datav TableExists 族留异常 message 日志不改控制流）。
- 6.1 已闭包 no-op 返回值抽样面（P2b 300 样本）；scanner P3b 产品 0。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读；工作树在 6.1 Phase 1 commit（cbafcb59c5）后干净。
- 撞车面（Phase 2 必查）：4.1（同 TSU 域上游——本项为承接裁定非重扫）；3.5（WorkflowEngineImpl 域）/3.2（TaskDispatchCoordinator U-2 位点）/3.11（敏感面 grep 复扫域）；4.2/4.3 各自闭包域。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.2-silent-skip-swallow-scan.md`：摘要 + 承接台账 + 裁定台账 + P0–P3 发现（`P<级别>-6.2-<序号>`）+ 驳回台账 + 裁决。
- **裁定覆盖（四层）**：
  1. **B1+B2A 未抽样 251 处 100% 全裁**（每处：合法忽略〔带注释/留证〕vs 静默跳过违规；违规者判级；251 处中 4.1 已有 2 处立案不重复）。
  2. **B2B 734 台账速裁 + 亲读锚定 ≥80**（按模式归类：边界转换/降级处理/中断恢复/迭代跳过；抽样锚定准确率 <90% 违规漏检则该模式升级全裁）。
  3. **B2C 43 + C1 82 + close-ignore 36 = 161 处全量速裁**（C1 修复方向 4.1 已统一 `throw new NopXxx(ERR,e).param(...)`——本项仅确认现状与判级；close-ignore 36 按 4.1 建议批量改 IoHelper.safeClose* 可行性确认）。
  4. **U-1～U-6 六项承接裁定**（核验 4.1 建议时点后位点现状是否漂移，给出 修复/维持/升级 三选一终裁建议，移交 R1.0）。
- P0 即时通道；index 登记 + 独立 closure audit + roadmap 6.2 翻 done（**closure audit 先于翻 done**——5.3 教训）。

## Non-Goals

- no-op 返回值重扫（6.1 已闭包）；4.1 已闭包的 A 面域（bare RT/IAE/ISE/策略面）；check 系列标定（6.4-6.9）；修复执行（R1.0/R1.x——U-1～U-6 与全部违规仅产出裁定建议）。

## Scope

### In Scope

- `_tmp/fcr-4.1-catches.tsv` 六桶 1611 行（345+734+43+82+36 行 + U×6 位点）的裁定
- 只读输入：4.1 报告全量（裁定先例+裁定建议）、6.1 报告（no-op 域引用）、3.5/3.2 报告（撞车对照）

### Out Of Scope

- 测试代码 catch（4.1 窗口已排除）；4.1 A 面域；修复执行。

## Execution Plan

### Phase 1 - 四层裁定（3 agent 并行）

Status: planned
Targets: `_tmp/fcr-4.1-catches.tsv` 六桶 + U-1～U-6 位点

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 口径固化：TSV 六桶行号清单切分（251/734/161/6）+ 裁定表模板落盘 + 4.1 抽样 94 处与 2 处立案清单提取（防重裁）
- [ ] Agent 1「B1+B2A 251 全裁」：逐处亲读上下文（合法忽略需带注释或留证，静默跳过违规判级），4.1 已裁 94 处对照去重
- [ ] Agent 2「B2B 速裁+锚定」：734 台账速裁（模式归类）+ 亲读锚定 ≥80（按桶分层抽样，固定规则写死：每模式前 20 + 随机 60）+ 锚定违规率 >10% 模式升级全裁
- [ ] Agent 3「161 速裁 + U×6 承接」：B2C 43 + C1 82 + close-ignore 36 全量速裁 + U-1～U-6 位点现状核验（4.1 建议三选一终裁建议）
- [ ] 主 agent 抽查质量：裁定抽验 ≥12 条独立复判 + B2B 模式归类抽验 ≥6 组
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-6.2-swallow-audit.md`（三 agent 分节）

Exit Criteria:

- [ ] 初稿落盘，每条违规发现 ≥9 行含 file:line 与修复方向；裁定台账逐行含二分归属与理由
- [ ] B2B 模式归类表落盘（模式→数量→代表例→锚定样本数→锚定违规率）
- [ ] No owner-doc update required（只读——修复归 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：违规发现逐条 保留/降级/驳回 + 裁定台账抽验（≥15% 跨四层）+ B2B 锚定样本独立复判 ≥20 + 撞车核对（4.1/3.5/3.2/3.11）
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
- [ ] 定向验证锚点：TSV 计数口径重放由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；**通过后** roadmap 6.2 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 裁定口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（P3b 空 catch 产品 0 已由 6.1 口径覆盖）

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且四层裁定全覆盖——覆盖度量：251 全裁 + 734 速裁（锚定 ≥80）+ 161 速裁 + U×6 承接（= 1146 处 + 6 位点）
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
