# 2339 full-code-review 4.1 错误处理与错误码一致性审计

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.1（MA4）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 09 + 共享前缀）+ `ai-dev/skills/nop-platform-conformance-audit-prompt.md`
> Related: owner doc `docs-for-ai/02-core-guides/error-handling.md`（两档策略权威定义）；Roadmap Details：「全仓 `NopException`/ErrorCode 使用一致性、bare RuntimeException、错误消息英文约束」。
> **两档策略基线**（error-handling.md）：①框架核心/跨模块公共 API/GraphQL 接口——必须 ErrorCode 模式（NopException + ErrorCode + .param()，错误码命名 nop.err.{模块}.{描述}，ErrorCode.define() 定义，ARG_* 参数常量）；②模块内部实现——可用模块级异常类（NopXxxException extends NopException）+ 英文消息。禁止 bare RuntimeException；**错误消息英文约束以 owner doc 例外条款为准**（ErrorCode.define() 描述在存量中文模块延续中文合规经 i18n，仅非 ErrorCode 路径消息强制英文；nop-metadata 连字符特例）；错误码命名含 [子域] 段；异常链 cause 保留、禁止吞异常（留证除外）。
> **已知先例与撞车输入**：3.5/3.7/3.8 深审各自做过错误码两档策略 throw 全量核对（nop-sys/rule/tcc-retry 域零违规结论在案）；check2 各报告的 :域错误码条目（如 nop-sys :285 ERR_SYS_NO_SEQ）；历史台账 `ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md` MA4.2-14（import 裁定）。历次深审已立的错误处理类发现（如 P2-3.9-002 等）为撞车去重输入。
> 只读审计（P0 走异步注入）。
> **标定口径**：全仓扫描口径在机械基线步写死（模式+排除项+窗口）；与历次深审已立案发现撞车的登记 duplicated 不重复计数；新发现按维度 09 判级。
> Draft Review: agent_d445d9ef 首轮 1 Blocker（量级 848 处单会话不可行——清算面收窄：bare RT 全量 8 处 + IAE/ISE 台账式一行一裁定违规项才展开）+ 3 Major（判级依据改共享前缀 :329-333/域内矛盾消解/四口径定义）+ 4 Minor（策略摘要补例外/吞异常计数细分/跨清单去重/dirty tree+P0 即时注入）全修；agent_1092df6d 聚焦复核全 PASS（1 Minor：Draft Review 行未记录——本行即补）+ 2 nit 非阻塞（8 处含注释与 lint corpus 由机械基线口径自纠）——进入执行。

## Purpose

执行 roadmap 工作项 4.1：全仓错误处理与错误码一致性审计（两档策略落地核对、bare RuntimeException 清算、错误消息英文约束），产出报告 `4.1-error-handling-audit.md`。

## Current Baseline

- 两档策略 owner doc：`docs-for-ai/02-core-guides/error-handling.md`（443 行，权威定义）。
- 历次深审错误处理侧结论：nop-sys（3.5）/nop-rule（3.7）/tcc-retry（3.8）throw 全量核对零违规；nop-auth（3.10）/gateway（3.9）各自域内发现已立案。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；本项为扫描型工作项（验证以复核员独立重扫为锚）。
- **注意 P2-1.6-003 先例**（弱加密 WARN，3.11 报告交叉引用过其升级触发条款）——错误码域的同族「已知默认值/配置面」发现须对照。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.1-error-handling-audit.md`：摘要 + 扫描台账 + P0–P3 发现（`P<级别>-4.1-<序号>`）+ 驳回台账 + 裁决。
- **bare RuntimeException 全仓清算**（roadmap 字面点名）：`throw new RuntimeException` 全仓清单（排除测试/生成物，实测 8 处）——逐个三态裁定（合法/违规需修/watch），每处 ≥9 行展开。
- **IAE/ISE 台账式裁定**：`throw new IllegalArgumentException`（~489）/`throw new IllegalStateException`（~351）——**台账式一行一裁定**（编号+file:line+一句话判定〔合法基础设施/Guard 类/违规〕，仅违规项展开 ≥9 行条目）；合理化依据：平台大量 IAE/ISE 属 Guard/参数断言基础设施（Guard.notEmpty 等内部即用 IAE），逐个展开违反单会话纪律，roadmap 4.1 字面仅点名 bare RuntimeException。
- **两档策略落地一致性**（维度 09 核心）：公共 API 面（GraphQL BizModel/api 模块抛出点）的 ErrorCode 模式合规率抽样；模块内部异常类模式核对；错误码命名规范（nop.err.*）抽样；.param() 上下文传递抽样；异常链 cause 保留抽样。
- **错误消息英文约束**（roadmap 点名）：throw 消息中文/非英文全仓 grep（排除 i18n 资源文件与注释）。
- **吞异常横切**：空 catch / catch 后丢弃异常不抛出不留证的横切**计数**（按 owner doc 留证规则细分：catch + LOG.warn/error 且 throwable 作末参 = 合规留证不计；空 catch 或 catch 后不记录不抛出 = 计入）+ 高危抽样——与 6.2 的边界：本项仅横切计数+高危抽样，6.2 做全量逐个裁定。
- 历次深审撞车去重（3.5–3.11 已立案的错误处理类发现）。
- index 登记 + 独立 closure audit + roadmap 4.1 翻 done。

## Non-Goals

- 各模块错误处理逻辑的深审（历次深审已覆盖域）；6.2 全量吞异常裁定（本项仅横切计数+高危抽样）；生成物/测试代码；i18n 资源文件（错误消息本地化是特性）。

## Scope

### In Scope

- 全仓 src/main Java 的 throw 语句与 catch 块（扫描型；**撞车去重仅适用于历次深审已立案 finding ID；深审域内其余裸抛/违规仍逐个裁定**，横切计数全仓覆盖）
- 只读输入：`docs-for-ai/02-core-guides/error-handling.md`、`ai-dev/skills/nop-platform-conformance-audit-prompt.md`、历次深审报告（撞车去重）、`ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md`（先例）

### Out Of Scope

- 6.2 全量吞异常裁定；生成物/测试；i18n 资源；模块深审重复域。

## Execution Plan

### Phase 1 - 扫描与抽样（双 agent 并行）

Status: completed
Targets: 全仓 src/main

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（**若不干净：登记在途变更清单并核对本 plan 扫描域是否被触及；触及则待其提交后再开始，未触及则登记豁免继续**）
- [x] 机械基线（口径写死基线：bare RT src/main throw 语句 5 处〔8 处含注释与 lint corpus〕、IAE ~478/ISE ~307 台账实测）：扫描口径写死（三类模式+排除项+窗口）、历次深审已立案错误处理发现撞车清单、owner doc 两档策略要点提取
- [x] 裸抛与策略面子 agent：bare RuntimeException 全量裁定（8 处）+ IAE/ISE 台账式一行一裁定（~840 处，违规项展开）+ 两档策略合规抽样（**公共 API 面识别=BizModel 抛出点 + nop-*-api 模块 throw；抽样率=每模块组 ≥10 个抛出点或全量（<10 时），分母=该面 throw 总数**；核对 ErrorCode 模式合规/命名规范/.param()/cause 保留）+ 中文消息 grep（**口径：throw 语句字符串字面量含 CJK 字符，排除 i18n 资源/注释/测试**）+ 撞车去重
- [x] 吞异常横切子 agent：空 catch/仅 LOG catch 横切计数 + 高危抽样（吞掉影响正确性的场景）+ 与 6.2 边界登记
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A/B 面均第 2 轮收敛）
- [x] 产物 `_tmp/fcr-4.1-{throw,swallow}.md` + 可复现工具 `_tmp/fcr-4.1-scan.py`/`_tmp/fcr-4.1-catches.tsv`

Exit Criteria:

- [x] 两份初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [x] 扫描台账（口径可复现+计数分布）落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 口径重放（6 条全保留零降级零驳回；B 面重放零偏差 TSV 字节级一致；A 面 C 节合规率修正 88.7→84.3〔抽取器括号配平缺陷漏检 24 条〕+ 09-04 处数 9→28 + 09-20 加重）
- [x] 主 agent 汇总：判级统一（依据共享前缀 P0–P3 :329-333）+ 撞车去重终裁（NettySslEngineFactory duplicated 维持）
- [x] 扫描台账收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-4.1-{throw,swallow}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `4.1-error-handling-audit.md`；index 登记 4.1 行
- [x] 如有 P0：**发现即异步注入**（不等 Phase 3）+ 「P0 发现追踪」登记（本项零 P0）
- [x] 定向验证锚点：扫描口径重放由复核员独立执行（B 面 11 项计数零偏差 + TSV 字节级一致；A 面独立重写扫描器逐文件 diff）；doc-links exit 0
- [x] 独立子 agent closure audit；通过后 roadmap 4.1 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 扫描口径可复现（复核员重放计数一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重扫为验证锚点。

- [x] 报告落盘且裸抛清算/两档策略/英文约束/吞异常横切全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 4.1 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 全仓错误处理横切审计计划收口。报告 `4.1-error-handling-audit.md` 落盘：通过无 P0/P1 附 P2×3/P3×3（净 6 条，零驳回）。**bare RT 全仓仅 5 处且仅 1 违规 P3**（ByteHelper:360）；IAE/ISE 台账 785 行（违规 68：F1 错误码字符串伪装系统族 65 处/40 文件/14 模块组 P2 + 中文 6 处 P2）；BizModel 面 ErrorCode 为主合规率 84.3%（复核修正后口径，修正透明记载）+ .param() 91.3%、api 面校准后零违规；3348 catch 合规留证+rethrow 64.0%、吞异常高危仅 1 立案（维度09-20 ToolSchemaConverter——复核加重：null 系全部真实工具常态路径，升级触发=agent 切原生 tool-calling）。复核修正四项透明记载。6.2 移交 catches.tsv 3348 行清单已备。closure audit 一次 PASS（处方 R1/R2 机械项已落实：42→40 文件勘正、log/plan 补记）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_efe8413f-d2d2-4501-bafd-2085e071db56（fresh session，只读 closure auditor；一次 PASS 附 R1/R2 机械处方）
- Evidence:
  - 计数勾稽：P0=0/P1=0/P2=3/P3=3=6 ↔ 报告实点（程序化抽取 6 finding）↔ index 4.1 行（0/0/3/3）↔ 映射表 ↔ 复核净账（A 5 + B 1）
  - P1/P0 台账：两表无 4.1 新增正确
  - 关键判定亲读全命中：伪码 2 处（ExecutionContextImpl:86/:99）+ BatchTaskContextImpl ×10 逐行/ErrorMessageManager :236-237/:322-340 机制链/09-20 加重事实（ToolSchemaParser:24-26 javadoc 逐字自认 + getSchemaJson 主代码零消费 grep 证实）/ByteHelper:360/合规率复算（311/369=84.3% 等 6 组全对 + 漏 24 条分母差吻合）
  - 复核修正四项透明记载与 review 产物一一对应
  - 格式：6/6 条 9-10 要素程序化核验完整
  - 验证：TSV 3349 行 cmp 字节级一致独立复现；doc-links exit 0
  - 6.2 移交：3348 行全量清单 + 未抽样计入类 251 = 345−94 勾稽一致
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以复核员独立重扫 + closure auditor 独立复算为锚
  - Deferred 项分类检查：无 deferred；P2×3 进 R1.0 输入池；P3×3+watch 候选 watch-only；无 in-scope live defect 被静默降级

Follow-up:

- no remaining plan-owned work（P2×3 修复归属 R1.0 分族〔09-20 附升级触发条款〕；P3×3+watch 候选 watch-only/R1.0 低优先池；EqlASTBuildVisitor:458/462 存量中文例外 watch 候选登记；6.2 移交 3348 行分类清单与 U-1~U-6 备注）
