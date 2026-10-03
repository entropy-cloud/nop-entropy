# 2309 full-code-review 1.2 nop-xlang 结构/边界/生成管线审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.2（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/02/05 + 共享前缀）
> Related: plan 2308（1.1 已完成，其依赖图结论可复用）；owner doc `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`
> Skill 适配注记：nop-xlang 为单 Maven 模块且本身即编译器，三个维度正文按下述适配后派发——维度 01 正文「读子模块 pom 构建依赖图 + api/dao/service 分层规则」不适用（`<modules>`=0），替换为**组内包级依赖方向**（api/ast/compile/exec/backend 包分层 + 对 nop-core/nop-commons 兄弟模块依赖面）；维度 05 正文「model→codegen→…→web 生成链 + xgen」不适用，按 roadmap Details 替换为**解析→编译→执行链连通性**（parse/compile/exec/backend + BuildExecutableProcessor）；维度 02 阈值自正文 500 行提为 1500 行（理由：xlang 为编译器实现，500–1500 行解析器/访客类属正常形态；500–1500 行职责混杂候选登记 watch 清单移交 4.3 全仓盘点，不在本项裁定）。severity 判级按 roadmap 通用约定用 deep-audit 共享前缀。

## Purpose

执行 roadmap 工作项 1.2：对 `nop-kernel/nop-xlang` 做 deep-audit 维度 01（依赖图与模块边界）、02（模块职责与文件边界）、05（生成管线完整性）三维度审计 + 两项点名复核（XLangASTOptimizer 3028 行无直接测试、JsPromise:117 吞 Throwable），产出报告 `1.2-xlang-structure-pipeline-audit.md` 存档轮次目录。**只读审计，不改产品代码**（P0 走异步注入）。

## Current Baseline

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/` 下 24 个包目录 + 3 个顶层 .java（XLangConfigs/XLangConstants/XLangErrors；包：api/ast/backend/bind/compile/delta/exec/expr/feature/filter/functions/initialize/janino/parse/scope/script/utils/xdef/xdsl/xmeta/xpath/xpkg/xpl/xt）；src/test 112 个 Java 文件；`_` 前缀生成文件 164 个（口径：src 下 `_*.java` + `_*.xml`，`find ... -name '_*.java' -o -name '_*.xml'`；不含 test 资源类 `_` 文件）。
- 点名热点 1：`XLangASTOptimizer.java`（`io/nop/xlang/ast/`）实测 **3028 行**；`grep -rl XLangASTOptimizer nop-kernel/nop-xlang/src/test` 零命中（无直接测试类）。
- 点名热点 2：`JsPromise.java`（`io/nop/xlang/utils/`）117 行附近为 `finallyDo` 方法，现有注释已声明「回调正常完成透传原 settle 状态、回调抛错转 rejected（不吞异常）」——check2 时代的「吞 Throwable」发现需以当前代码复核（可能已修）。
- 1.1（plan 2308）已确认：nop-kernel 组间出边为零（35 条边全部组内）、reactor 零循环——组间边界健康，本项聚焦组内。
- xlang 执行后端（nop-xlang-java/truffle/e2e/javac/antlr4）归 1.5，本项不含；nop-jq 引擎归 1.9。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.2-xlang-structure-pipeline-audit.md`：摘要 + P0–P3 发现（编号 `P<级别>-1.2-<序号>`，file:line 证据 + 修复方向）+ 三维度结论 + 驳回/降级台账（保留在报告正文）+ 裁决。
- 维度 01：xlang 组内包依赖方向与分层（api/ast/compile/exec/backend 边界）、对 nop-core/nop-commons 兄弟模块的依赖合规。
- 维度 02：巨型文件盘点（>1500 行手写）、生成物与手写边界、XLangASTOptimizer 拆分/保留/monitor 三选一裁定建议（供 R1.0，不强制拆）。
- 维度 05：解析→编译→执行链连通性（parse/compile/exec/backend 接线；BuildExecutableProcessor 在链路中的位置与调用面）；`_vfs` xlang 相关注册面。
- 两项点名复核结论落报告。
- index 登记 + 独立 closure audit + roadmap 1.2 翻 done。

## Non-Goals

- 不审计 nop-xlang-java/truffle/e2e/javac/antlr4 执行后端（1.5）、nop-jq（1.9）、nop-core 的 ClassModel/XNode（1.3）。
- 不做行为级正确性证明（运行时语义深审归后续专项）；本项以结构/边界/管线连通为主，发现附证据但修复方案归 Wave 2。
- 不拆分任何文件。

## Scope

### In Scope

- `nop-kernel/nop-xlang/src/main/java/**`、`src/test/java/**`、`src/main/resources/_vfs/**`（xlang 相关注册面）
- 点名热点两处 + deep-audit 维度 01/02/05 正文要求的范围

### Out Of Scope

- xlang 执行后端 5 个子模块、nop-jq、其他 nop-kernel 兄弟模块内部（仅审计 xlang 对它们的依赖面）

## Execution Plan

### Phase 1 - 三维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: `nop-kernel/nop-xlang/**`

- Item Types: `Proof`

- [x] 动手前基线确认：`git status` 干净（无在途变更）后开始扫描
- [x] 机械基线（共享前缀规则 #10，主 agent 派发前提供）：包级依赖提取命令（如按 package 声明 grep import 生成 `io.nop.xlang.*` 包间边表，注明 grep 模式与排除项）、`find ... -name '*.java' | xargs wc -l` 行数榜、`_` 生成物清单（164 个）——供三维度子 agent 复现
- [x] 维度 01 子 agent（含适配注记）：组内包依赖方向（api 层是否被反向依赖、compile/exec/backend 分层）、对兄弟模块依赖面合规；输出发现初稿（每条 ≥9 行：文件/严重程度/现状/风险/建议）
- [x] 维度 02 子 agent（含适配注记）：>1500 行手写文件全清单（区分生成物；500–1500 行职责混杂候选仅登记 watch 移交 4.3）、XLangASTOptimizer 结构解剖（职责块/内聚性/可拆分面；**不含**测试缺口结论——归点名复核 agent 权威产出）
- [x] 维度 05 子 agent（含适配注记）：解析→编译→执行链追踪（入口 XPDL/XScript 解析 → compile → exec/backend）、BuildExecutableProcessor 调用面、断链候选；`_vfs` 注册面完整性
- [x] 点名复核子 agent（独立第 4 个）：JsPromise 全文精读（finallyDo 及全部 catch/then 路径，当前是否吞 Throwable，给 still-live/already-fixed 结论）；XLangASTOptimizer 测试缺口权威结论（直接/间接覆盖面 + 缺口性质）
- [x] **深挖迭代**：任一维度第 1 轮有发现即派深挖追加轮（携带该维度已有发现全文 + 深挖指令：基于已发现文件/模式扩盲区），迭代至无新发现或 10 轮上限；追加发现以 `## 深挖第 N 轮追加` 节追加进同维度文件
- [x] 每维度发现按 deep-audit 共享前缀条目格式落 `_tmp/fcr-1.2-dim{01,02,05}.md` + `_tmp/fcr-1.2-named-recheck.md`

Exit Criteria:

- [x] 三份维度初稿（含深挖追加节）+ 点名复核结论落盘 `_tmp/`，每条发现含 file:line
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof`（裁定建议行）+ `Decision`

- [x] 每维度派发**独立维度复核子 agent**（fresh session，输入=该维度完整初稿）：重新读代码与文档，逐条给出 保留/降级/驳回 判断；高风险或不确定条目再派**子项复核子 agent** 逐项验证（低风险可批量）
- [x] 主 agent 汇总各维度复核结论（对照 deep-audit 误报校准：`_` 生成物、I*Biz-in-dao、jakarta-in-core 等）；只有复核通过的条目进入最终汇总
- [x] 统一 severity 判级（P0–P3）；跨维度重复发现合并
- [x] XLangASTOptimizer 给出 拆分/保留/monitor 三选一裁定建议（供 R1.0）
- [x] 驳回/降级条目留痕**保留在报告正文**（复选 2308 撤回条目先例：正文条目 + 驳回理由），不落 `_tmp/` 临时区

Exit Criteria:

- [x] 每维度有独立复核子 agent 结论记录（维度名 + 保留/降级/驳回逐条）；每条保留发现：判级理由 + 证据可复现
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `1.2-xlang-structure-pipeline-audit.md`（摘要 + 维度结论 + 发现清单 + 裁决 + 复现口径）
- [x] index.md 登记 1.2 行（P0/P1/P2/P3 计数）；如有 P0 当即异步注入修复 plan 并登记「P0 发现追踪」
- [x] 独立子 agent closure audit（证据完整性/判级/路径真实性/index 计数对应）
- [x] audit 通过后 roadmap 1.2 翻 `done`；plan 关闭（双 --strict 校验 + Closure Evidence）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [x] 报告落盘且三维度 + 两点名复核全部覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准套用留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 1.2 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项（驳回发现留痕即可，不算降级）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- XLangASTOptimizer 若裁定为「拆分」，具体拆分 plan 归 R1.x（本项只给裁定建议与拆分面描述）。

## Closure

Status Note: 1.2 三维度 + 两点名复核全部完成（P0=0/P1=1/P2=10/P3=18，驳回 1 留痕报告正文）；closure audit 一次 PASS（4 Minor 全部顺手修复）；P1-1.2-001 与 P2×10 进 R1.0；未改产品代码，无 P0。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: agent_2580d6da-9902-4297-b2eb-3829dada6d75（独立 fresh-session closure auditor，只读）
- Evidence:
  - 发现清单完整性：P2 表恰 10 行、P3 表恰 18 行、P1×1、驳回台账 1 条（机械复核）；抽验 10 条 file:line 全部命中（含 P1 SCC 脚本复跑 + 2 条真环文件级验证）
  - severity 标定：审计员独立裁定 P1-1.2-001 维持 P1（「误导后续开发」有审计内部实证——01-02 路线被 SCC 误导后证伪）、P2-1.2-010 维持 P2；误报校准零违反
  - 6 项独立复测全过：SCC 复跑、死配置 grep、Optimizer 生成物定性、>1500 行 wc 精确（2096/1957/1719/1627）、主链 7 锚点逐行精确、错误路径 3 锚点精确
  - index 对账：0/1/10/18 一致（P1 汇总表欠行已补）；4 个 Minor（index P1 行、P3-1.2-016 路径前缀、3 处行号 ±1、log 陈旧计数）已全部修复
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs` 退出码 0
  - Anti-Hollow（审计版）：链路连通与错误路径两条正结论均有真实 file:line 证据支撑

Follow-up:

- no remaining plan-owned work（XLangASTOptimizer 拆分裁定=保留；模板层测试补课与 SCC 治理已移交 R1.x/R1.0 输入）
