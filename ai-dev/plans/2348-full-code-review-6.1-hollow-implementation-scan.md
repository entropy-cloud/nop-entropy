# 2348 full-code-review 6.1 空壳实现全仓扫描

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.1（MA6）；Skill=`ai-dev/skills/deep-audit-prompts.md` + `ai-dev/tools/scan-hollow-implementations.mjs`
> Related: Roadmap Details：「全仓空壳扫描 + 抽样确认（区分在建模块的合法空壳如 nop-lint-nop 与违规空壳；nop-jq 已于 2026-09-28 建成并独立成组，不再是空壳例证）」；关联 6.2（静默跳过/吞异常——空 catch 域归 6.2，本项不重扫）。
> 机械基线（主 agent 实测，2026-10-07，scanner JSON `_tmp/fcr-6.1-hollow-full.json` 全量 6118 findings/9 模式键，按「`/src/main/java/` 且排除 `_tmp/ai-dev/deepwiki/_dump/target`」产品过滤——经 Draft Review agent_e853381a 逐项复算勘正）：**high/critical 产品命中 13 条**（P1:high UnsupportedOperationException ×8：`DynCountArrayBinaryCodec.java:42/:65`、`TdMessageService.java:21/:26`、`GeometryTypeHandler.java:91`、`ChunkFileUploadHandler.java:19/:23/:30`；P6b:high 注释命中 ×5：`ReActAgentExecutor.java:1327/:1456`、`PlanReplanner.java:273`、`NoOpProviderFailoverQueue.java:34`、`JavaEmailSender.java:216`——草审预判 5/5 为"placeholder"字样注释误命中，待裁定确认）；**P1:medium UOE 产品 47 条**（76 为未过滤全量——29 条 `_tmp/snap/t2023..t2025b` 快照重复噪声）；**P1:low 产品 29 条**（guard-style "does not support"/"is disabled" 倾向，预裁合法快裁）；P2b:medium 单行 return 2424 条；P6:medium 645 条；P8:medium 1151 条；P4:low TODO/FIXME 产品 44 条（format 31 + dev-tools 10 + 其他 3）；**P3b 产品 0**（5 条全为 `_tmp/snap` 噪声，归档）；P2a/P3 零命中。产品抽样命中大头：nop-kernel（P2b 697/P6 174/P8 194）/nop-ai/nop-format/nop-persistence/nop-stream。high 层非产品噪声 7 条（`_tmp/probe/stub-src` ×2 + `HollowControlFixture` ×3 + `_tmp` ×2）；全量非产品命中 1765 条（绝大多数为 `_tmp/snap` 快照重复）。**scanner 口径注记**：默认 src-only（JSON `scope:"src/main"`），codeClass 字段区分 main/unknown 非 main/test——测试面零命中是口径边界不是零空壳。
> **Phase 2 勘误注记（复核员 agent_0a0753ae 坐实，2026-10-07）**：本头部及 plan 早期草稿沿用的 scanner 模式语义描述有两处不准——P6 实际模式为 **stub/mock/dummy/temp/placeholder/template 名称子串匹配**（非"未用公共方法"，抽样 60 条中 47 条为子串误命中如 `temperature`/`TemplateExpression`）；P8 实际模式为 **continue 跳过循环体（疑似静默 no-op）**（非"stub/placeholder 命名"）。Agent 2/3 已按冻结 JSON 实际定义裁定并登记偏差，Phase 1 数字与裁定不受影响；scanner 模式改名/加词边界建议作工具改进项移交（watch，不立案）。
> 合法在建空壳基线：`nop-lint/nop-lint-nop`（main 1 java=package-info.java + test 11 个测试类，roadmap 点名合法在建；其 package-info:3 因注释含 "templates" 被 P6 误命中 1 条——预登记）；nop-jq 已建成独立成组（1.9 已深审，非空壳例证）。
> 历史撞车面（Phase 2 必查，草审 B4 补全）：**3.8**（ChunkFileUploadHandler 三 UOE 已裁"纯 stub 零发现"）+ **3.9**（check2 :331/:386 同位点标定 already-fixed——UOE 化即修复态，Agent 1 必读对照防翻案）；**4.4**（nop-format TODO 35 处三类评定台账——P4 format 31 条对照）；**4.5**（nop-dev-tools 域——P4 dev-tools 10 条对照）；**nop-ai arm 台账**（NoOpProviderFailoverQueue 类注释自带 arm plan 设计裁定引用）；1.2/1.3/1.4（nop-kernel 抽样大头域）；4.1/4.2/4.3/5.2（各自闭包域）。
> 只读审计（P0 走异步注入）。**判级锚**：deep-audit 共享前缀 P0–P3（:329-333）；scanner 自带模式严重级仅作初筛，最终判级以共享前缀为准。**方法学裁定**：① scanner 模式语义锚 = 工具头注释（P1 UOE stub-style 违规 vs guard-style 合法 fail-fast；P2b 单行返回逐条判断）；② **裁定四分法**：a.stub-style 违规空壳 / b.guard-style 合法 fail-fast / c.Rule #24 合法显式失败占位（判据：公共 API 无文档化暂缓声明且调用方期待完整功能 = 违规 a；有设计裁定/owner doc/注释声明的暂缓 = 合法 c 登记）/ d.注释误命中；③ **测试面覆盖**：scanner 不产 test 命中（口径边界），测试面核对 = Agent 1 按涉事类名对 `src/test` 批量 grep 生成触达表（每条违规发现必附：该路径是否有测试触达 + 测试断言什么）；④ 「排除自身模块 vs 含自身测试面」零引用口径显式声明（E7 教训——plan 2347 closure audit）。
> Draft Review: agent_e853381a-05dc-437c-ac67-c2c16ad5bdba 首轮 1 Blocker（B1 P1:medium 产品 76→47——原数含 `_tmp/snap` 快照重复，Closure Gates 89→60 连带）+ 6 Major（B2 scanner codeClass=main/unknown 非 main/test——裁定③与 Scope Out 重写/B3 P1:low 29+P3b 0 未分配——显式处置补齐/B4 撞车清单漏 3.8/3.9/4.4/4.5/nop-ai arm/1.x 域/B5 抽样可复现要素全缺——草法固化六要素/B6 裁定缺 Rule #24 第四类及判据）+ 3 Minor（B7 重跑绑定+噪声措辞/B8 盘点判据+nop-lint-nop 口径/B9 测试面核对批量法）——本轮全部修复，待聚焦复核。

## Purpose

执行 roadmap 工作项 6.1：全仓空壳实现扫描 + 人工抽样确认，产出报告 `6.1-hollow-implementation-scan.md`。

## Current Baseline

- 机械基线见头部注记（13 high / 47 P1:medium / 29 P1:low / 2424 P2b / 645 P6 / 1151 P8 / 44 P4 / P3b 0，产品域口径；扫描口径边界：src-only，测试面由 Agent 1 手工 grep 覆盖）。
- 合法空壳先例：nop-lint-nop（在建）；nop-jq 已建成；guard-style UOE（"not supported"/"only available on"）为平台认可的 fail-fast 模式非空壳。
- 绿色基线：编制期全仓 BUILD SUCCESS（2026-09-22）；本项只读；工作树在 5.3 收口 commit（7546b4b8ea）后干净。
- 撞车面（Phase 2 必查）：3.2/3.3/3.4（batch/task 深审已裁定项）、4.2（测试反模式）、4.3（巨型文件）、4.1（错误处理——NoOpProviderFailoverQueue 吞异常面若涉 catch 归 6.2）、5.2（文档草稿态）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.1-hollow-implementation-scan.md`：摘要 + scanner 口径台账 + 裁定台账 + P0–P3 发现（`P<级别>-6.1-<序号>`）+ 合法空壳清单 + 驳回台账 + 裁决。
- **裁定覆盖（四层，四分法见方法学裁定②）**：
  1. **high 13 条 + P1:medium 47 条 = 100% 全裁**（60 条逐条亲读：a 违规/b 合法 fail-fast/c Rule #24 合法占位/d 注释误命中；违规者按共享前缀判级，每条附测试触达表结果）；**P1:low 29 条按模式语义快裁**（guard-style 预裁 b 合法，抽验 ≥10 条确认无误判后登记台账，不逐条全裁）；**P3b 产品 0**（5 条 `_tmp/snap` 噪声归档记录）。
  2. **P2b 2424 + P6 645 = 分层抽样 ≥180 条**（P2b ≥120 + P6 ≥60，抽样草法见 Phase 1 固化脚本），逐条四分裁定。
  3. **P8 1151 抽样 ≥60 + P4 44 全量速裁**（P4 format 31 条对照 4.4 台账、dev-tools 10 条对照 4.5 域，不翻案重裁）。
  4. **合法在建空壳清单核定**：nop-lint-nop 全读 + 全仓在建/占位模块盘点（机械判据：`find` main java ≤2 的模块清单 × roadmap 规则 8 边界逐个核对——功能开发类空壳归各自 roadmap，本项仅登记不判缺陷）。
- P0 即时通道；index 登记 + 独立 closure audit + roadmap 6.1 翻 done。

## Non-Goals

- 空 catch/吞异常域（6.2）；check 系列发现的标定（6.4–6.9）；修复执行（R1.0/R1.x）；测试有效性域（4.2 已闭包）；文档空壳（5.2 已闭包）。

## Scope

### In Scope

- 全仓产品 `src/main/java`（scanner 全量口径 + 抽样裁定）；合法在建空壳清单核定
- 只读输入：scanner JSON `_tmp/fcr-6.1-hollow-full.json`、3.2/3.3/3.4/4.1/4.2/4.3 报告（撞车对照）

### Out Of Scope

- 测试代码空壳整域（scanner src-only 口径无 test 命中；测试侧仅按 Agent 1 触达表核对违规位点，整域归 4.2 域）；`_tmp/ai-dev/deepwiki/_dump/target` 非产品路径；修复执行。

## Execution Plan

### Phase 1 - 全量口径固化与三层裁定（3 agent 并行）

Status: completed
Targets: 产品 src/main/java 全仓

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 口径固化：scanner 重跑（`--severity low --format json`，输出绑定 `_tmp/fcr-6.1-hollow-full2.json`）+ 与修正后基线核对（13/47/29/2424/645/1151/44/0 精确吻合）+ 产品过滤规则写死 + **抽样脚本落盘 `_tmp/fcr-6.1-sample.py`**（六要素齐备：RNG=python3 `random.Random(seed=2348)`；seed=2348；单位=唯一 file:line；分层=路径片段匹配；比率=五模块 ×2 其余 ×1 无零配额碎片层；产出=冻结抽样表 csv 三份 240 条，确定性重放验证通过）
- [x] Agent 1「high+P1:medium 全裁 + P1:low 快裁」：60 条全裁（b×47/c×9/d×4——**a 类违规 0 条**）+ P1:low 29 条快裁（29/29 亲读全 b）→ `_tmp/fcr-6.1-hollow-a1.md`；防翻案对照 3.8/3.9/nop-ai arm 全部一致
- [x] Agent 2「P2b+P6 抽样」：180/180 逐条裁定（a×1〔P3 ParseTreeFormatter 空壳类〕/b×120/c×12/d×47），零执行偏差 → `_tmp/fcr-6.1-hollow-a2.md`；口径校准：P6 实为 stub/mock/dummy/temp/placeholder/template 名称子串模式（78% 误命中）登记 scanner 改进建议
- [x] Agent 3「P8+P4+合法空壳」：P8 60/60（0 违规；口径偏差登记——P8 冻结定义实为 continue 跳过循环体）+ P4 44 全量速裁（format 30 对 4.4 台账 + 1 口径差新裁 + 5 条 4.4 在案未被扫描器收录；dev-tools 8 c + 2 新裁；散项 3 新裁）+ 在建空壳盘点（66 个 main java ≤2 模块全数合法定位，既有终裁 3 件对上）→ `_tmp/fcr-6.1-hollow-a3.md`，新登记 [A3-01] P2 Pulsar seekToPosition 空方法体静默忽略 seek 配置 / [A3-02][A3-03][A3-04] P3 **〔closure audit R3 勘误：盘点计数 66→69（48×1+21×2；a3 自身分组枚举 19+9+29+11+1 逐模块实为 69，初稿"66/19+10+29+8"与其枚举不符）——定性结论不变，正式面数字以 69 为准〕**
- [x] 主 agent 抽查质量：裁定抽验 10 条独立复判全部证实（A3-01 空方法体+TODO 亲读/A2-01 整类 null 亲读/TdMessageService·GeometryTypeHandler·DynCountArrayBinaryCodec c 类注解亲读/credential UOE 族 javadoc+英文声明亲读/ILoginService No-Silent-No-Op javadoc 亲读/ProxyCell cloneInstance 亲读/P6 temperature 子串误命中实证/P8 continue 误命中实证）+ 抽样脚本确定性重放（run2≡run3）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0）
- [x] 产物 `_tmp/fcr-6.1-hollow-audit.md`（三 agent 分节：a1/a2/a3）+ `_tmp/fcr-6.1-sample.py` + 冻结抽样表

Exit Criteria:

- [x] 初稿落盘，每条违规发现 ≥9 行含 file:line 与修复方向；裁定台账每条含四分归属与理由
- [x] 冻结抽样表落盘（脚本可重放：确定性验证通过）
- [x] No owner-doc update required（只读——修复归 R1.0）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：5 条违规发现 100% 亲读全部保留（0 驳回 0 升降级，P0=0 终裁）+ 台账抽验 56/373≈15% 跨层全证实（防 c 误判 b/真违规误判 d 专项无漏）+ 抽样重放（三 CSV SHA-256 逐字节一致 + 产品计数八值精确吻合）+ 撞车史补全（A3-01 在案 2026-05-20 pulsar 审查发现 7 附注 residual + 矩阵 :47 + roadmap :213 + 7.7 工作项——首次独立判级挂接 7.7/R1.0，不与 4.4 撞车）+ plan 头部 P6/P8 模式语义勘误坐实（已回写）+ a2 两条移交线索降级不立案
- [x] 主 agent 汇总：判级统一（P2-6.1-001 / P3-6.1-002..005）、撞车去重终裁（A3-04 路径勘误 nop-ai/nop-spring-mcp-server）
- [x] 台账收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-6.1-hollow-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `6.1-hollow-implementation-scan.md`；index 登记 6.1 行 + P1 汇总行（如有）+ P2 裁决台账行（如开辟例外）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0——closure audit 终裁确认）
- [x] 定向验证锚点：抽样口径重放由复核员独立执行（SHA-256 逐字节一致 + 产品计数八值吻合）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——首轮 FAIL 附处方 R1-R3〔覆盖度量 389→373 算术/§三 台账行级归属错行/盘点计数 66→69〕，处方落实后轻量复核转 PASS）；通过后 roadmap 6.1 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 抽样口径可复现（复核员重放一致——SHA-256 逐字节）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：本项即该工具的全仓审计执行本身，无新增产品代码

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [x] 报告落盘且四层裁定全覆盖——覆盖度量：high+P1:medium 100%（60 条）+ P1:low 29 快裁 + 抽样 240 条 + P4 44 全量（=373）+ 盘点附带观察 1 + 69 模块在建空壳盘点（closure audit R3 勘正后口径）
- [x] 每条违规发现 file:line 可复现；判级依据共享前缀；误报校准留痕（closure audit R1-R3 勘误 3 处透明落实）
- [x] index 计数与报告发现清单一一对应（5 条 = 0/0/1/4；P0/P1/P2 三台账节零新增正确）
- [x] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done——5.3 教训修正落实；FAIL→处方 R1-R3 落实→轻量复核）
- [x] roadmap 6.1 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项（scanner 口径校准三条 watch 不立案为显式裁定）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；空壳修复工单归 R1.0/R1.x。）

## Closure

Status Note: 6.1 空壳实现全仓扫描收口。净发现 P2×1（Pulsar seekToPosition 配置静默失效，挂接 7.7/R1.0 与 4.4 M-03 同批）+ P3×4 + 线索 4 条；373 裁定样本扫描行违规仅 4 条、69 个在建空壳模块全数合法定位零意外——**平台空壳卫生度高正结论**。closure audit（agent_bc940723）首轮 FAIL 附处方 R1-R3（纯文本勘正：覆盖度量 389→373 算术、§三 台账行级归属错行〔P2b a=1/P6 a=0/P4 a=3〕、盘点计数 66→69〔a3 自身枚举逐模块实数〕）——实体裁定、判级、撞车处理、抽样确定性全部经受住审计（关键判定 4/4 亲读成立、15+ 路径全命中、九值计数精确吻合、SHA-256 一致）；处方落实后轻量复核 PASS。流程合规：本 audit 先于 roadmap 翻 done（5.3 教训修正落实）。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_bc940723-9301-4a39-8c1f-36f3c4b43ea8（fresh session，只读 closure auditor；VERDICT: FAIL（处方 R1-R3）→ 处方落实后轻量复核 PASS）
- Evidence:
  - 计数勾稽：5 条发现（0/0/1/4）↔ index 6.1 行 ↔ 复核净账 ↔ A3-01→P2-6.1-001/A2-01→P3-6.1-002/A3-02..04→P3-6.1-003..005 编号映射零漂移；P0 发现追踪/P1 汇总/P2 裁决台账三节零新增正确；线索 CL-1..4 来源可溯
  - 关键判定亲读 4/4：P2-6.1-001（空方法体+seek 三字段公开面+KafkaConsumeTask:45 javadoc 逐字点名 Pulsar stub + :106-135 完整实现亲读）/P3-6.1-002（全仓七类文件 grep 仅声明自身）/P3-6.1-004（wc -l=699）/P3-6.1-005（servrer 包名实证+nop-ai 路径勘误成立）
  - 引用路径 15+ 全命中（含 4.4 M-03 :103、2026-05-20 pulsar 审查 :174 发现 7 附注、1.2 P3-1.2-011、scanner P6/P8 正则源码行——口径校准记载双重证实）
  - 覆盖度量算术：产品九值独立复算精确吻合、三 CSV SHA-256 一致、373=89+180+104 闭合；FAIL 三处（389/台账错行/66 vs 69）全部为文本勘正——R1/R2/R3 落实
  - 验证：doc-links exit 0；check-plan-checklist --strict exit 0（见 commit 前 double --strict 记录）
  - Anti-Hollow：本项即 hollow 扫描工具的全仓审计执行本身；验证锚 = 复核员 SHA-256 重放 + closure auditor 九值复算 + 亲读
  - Deferred 项分类检查：无 deferred；P2×1+P3×4 进 R1.0 输入池（P2-6.1-001 挂接 7.7 对账；P3-6.1-002 归并 1.2 域 slice；003/004/005 watch）；线索 4 条登记去向；scanner 三模式校准为显式 watch 裁定非静默降级
  - 流程合规：closure audit 先于 roadmap 翻 done——5.3 顺序偏差修正在本轮实际执行中落实（index 先标"closure audit 进行中"，PASS 后翻 done）

Follow-up:

- no remaining plan-owned work（P2×1 挂接 7.7/R1.0 与 4.4 M-03 同批；P3-6.1-002 归并 1.2 域 P3-1.2-011 slice；线索 CL-2/CL-3 归 R1.0 微项、CL-4 scanner 改进归工具维护方；P3-6.1-003/004/005 watch）
