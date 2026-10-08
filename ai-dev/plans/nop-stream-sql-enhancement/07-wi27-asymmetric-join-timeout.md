# 07 WI27 非对称窗口 join timeout

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI27 行、门控判据表非对称行）；`ai-dev/design/nop-stream/00-vision.md` §六裁决记录（WI0b 2026-10-08 字段扩展授权补记——实施前提已满足）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-ksql-analysis.md`（WITHIN 形态，转引）
> Related: `ai-dev/plans/nop-stream-sql-enhancement/02-wi0b-boundary-decisions.md`（授权裁定）；`ai-dev/plans/nop-stream-sql/23-wi13-equi-join-operator.md`（EquiJoinOperator 交付）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

joins 声明面由单一 timeout 扩展为 leftTimeout 与 rightTimeout（非对称窗口 join 宽限），构建期校验非负与恰一或双声明规则；运行时 EquiJoinOperator 按 side 参数化 fire/cleanup 时点。授权前提（WI0b 字段扩展补记）已于 2026-10-08 通过。

## Current Baseline

2026-10-08 实测锚点：

- **声明面**：stream.xdef :75-83 joinSpec 属性全集 7 个（joinId/joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout/description），timeout 为 duration 格式字符串；_StreamJoinSpecModel :59 `_timeout`（String）。
- **构建期校验**：StreamModelDslBuilder.validateJoinDeclarations :947-1036 九项规则（ref/type/键集非空/键数相等/恰两边/上游可解析/strategyRef 解析/窗口 join 仅 INNER+LEFT/timeout 格式 :1024-1026 与 timeout 需 windowStrategyRef :1027-1034）；13 个测试钉（TestParameterizedJoinModel）。非负性现由 parseDurationMillis（AdvancedTransforms :386-409，value<=0 拒）间接承担。
- **运行时**：EquiJoinOperator 字段 :87-89；构造 :111-115 `(joinType, Long windowDuration, long timeout)`；copyForSubtask :118-122；timeout 消费三点全在窗口形——isLateForWindow :260-262、fireReachedWindows :271、fireWindow :279-288（**twin 双清是唯一结构性分支点**：左 fire 时右 twin 无条件随清，若 rightTimeout>leftTimeout 则右窗未达自身 fire 点即被清）；hash 形不消费 timeout。
- **语义事实**：配对在 processElement eager 完成（:195-208）；fire 时 LEFT 补发未匹配左（completeUnmatched）后 dropAll 双侧。**配对不变式（R1-B1）**：记录被接受 ⇔ 到达时 wm < ws+wd+T_side，保留至 F_side——配对发射集合 ≡ 对称 min(T_L,T_R)；非对称仅解耦 LEFT 完成时点与长侧状态保留量。**M-2 语义边界披露**：roadmap 引 ksql WITHIN(before,after) 为记录级匹配区间偏移（会产生新配对），本项 per-side timeout 为其 narrowing 转译——INNER 发射面零变化、LEFT 仅 (L,null) 完成时点变化、twin 保留仅影响状态量；roadmap 收口括注留痕此边界（若需新配对语义应另立项 WITHIN 窗偏移）。**checkpoint 兼容**：算子字段不进 checkpoint（snapshotState 仅携带 buffer.freeze() :150-161；durable key 侧标 L|R 形状不变）；serialVersionUID 钉 1L，新字段对旧流缺省；算子配置重部署时由 DSL 重新供给——无需额外裁定。
- **测试先例**：TestEquiJoinWindowTimeout 5 场景（eager 配对/跨窗不配对/宽限保留/fire 后 sight-drop/MAJ-1 跨 key 清理）；构造点共 6 处（AdvancedTransforms :629、copyForSubtask、4 个测试类）。

## Goals

1. **声明面**：joinSpec 增 `leftTimeout="string"` 与 `rightTimeout="string"`（duration 格式）；**timeout 保留为精确对称糖**，与 left/right **互斥**（并存 fail-fast；R1 裁定自承：考虑过糖+继承替代，因双源 precedence 复杂化校验面而否决）；声明数量 0/1/2 全合法——**「恰一或双」是缺省语义裁定而非校验规则**（R1 修正）：未声明侧缺省 0（窗口末即完成；互斥裁定下不可能继承 timeout 属性——这层依赖显式写明）；零值显式声明拒绝（沿 parseDurationMillis 正值先例），错误文案提示「窗口末完成请省略属性」（消歧隐式缺省=0 与显式 0 被拒）。leftTimeout/rightTimeout 与 timeout 同样要求 windowStrategyRef（守卫泛化）。校验作用域注记：validateJoinDeclarations 仅校验被 `<join>` 引用的 spec（:951-956 先存行为，非本项回归）。
2. **运行时**：EquiJoinOperator 增 4 参构造 `(joinType, windowDuration, leftTimeout, rightTimeout)`（3 参旧构造保留=双端同值委托）；窗口形三点 side 参数化——isLateForWindow 按记录侧别、fireReachedWindows 按键侧别独立 fire、fireWindow 改 per-side（左 fire 补发未匹配左后只清左；twin 侧仅当其自身 fire 点已到才随清，否则保留）；hash 形零改动。
3. **语义裁定（R1-B1/M2 修正——数学事实先行）**：配对在 processElement eager 完成，记录被接受当且仅当到达时 wm < 该侧 fire 点 F_s = ws+wd+T_s，保留至 F_s——故**配对发射集合恒等于对称 timeout=min(T_L,T_R) 的配对集合**（与两端如何非对称无关；ksql WITHIN(before,after) 的窗偏移语义才会产生新配对，per-side timeout 是其 narrowing 转译）。per-side 的真实交付自由度：(i) **LEFT join 未匹配左的 (L,null) 完成时点=leftTimeout**（与右端解耦——唯一发射级自由度）；(ii) 长侧缓冲保留至自身 fire 点（状态量差异，bufferedCount 可判别）；(iii) INNER 发射面零变化。判别场景方向取 **leftTimeout > rightTimeout**：三轴=完成时点（(L,null) 于 wm=ws+wd+leftTimeout 而非 rightTimeout）+保留状态（wm∈(右 fire, 左 fire] 区间右侧 bufferedCount>0）+迟到拒绝（右记录 wm>右 fire 点到达被拒——对称 rightTimeout 会接受并配对）。保留侧自身 fire 点后 drop-on-sight 维持现状语义。
4. **测试**：flow 校验矩阵扩展（round-trip 双属性/互斥/正值文案/无 strategyRef 守卫泛化）；runtime 行为测试按三轴判别（完成时点/保留状态/迟到拒绝，方向 leftTimeout>rightTimeout）+ 配对面≡对称 min 不变式；**env 级 E2E**（R1-M1：DSL XML 声明 leftTimeout/rightTimeout → StreamModelDslBuilder.build → env.execute → sink 发射序列断言——封堵 buildJoin 4 参换序盲区；TestParameterizedJoinModel.selfJoinTopologyWalksValidationToRealJoinBuild 已示范真实 env 装配）；既有 5 场景零回归（3 参旧构造兼容，测试类与 copyForSubtask 零适配——R1 修正：构造点实为 7 处但仅 AdvancedTransforms :629 需改）。

## Non-Goals

- hash join 的 timeout（无窗口无宽限语义）；FULL 窗口 join（4a#8 维持）；SQL 面 JOIN 语法暴露 leftTimeout/rightTimeout（SQL 通道从不发射 timeout/windowStrategyRef 属性——R1 修正理由；DSL 面专属）；per-side windowDuration（size 仍对称）；WITHIN 窗偏移语义（另立项范畴，见 M-2 披露）。

## Scope

### In Scope

- nop-kernel/nop-xdefs：joinSpec 两属性（_StreamJoinSpecModel 再生成）。
- nop-stream/nop-stream-flow：validateJoinDeclarations 规则扩展 + AdvancedTransforms.buildJoin 解析传递。
- nop-stream/nop-stream-core：EquiJoinOperator 4 参构造 + per-side fire/cleanup。
- 测试：flow TestParameterizedJoinModel 扩展；runtime TestEquiJoinWindowTimeout 扩展（非对称判别场景）+ 既有场景回归。
- 文档：**`docs-for-ai/03-modules/nop-stream-user-guide.md`**（R1 钉名：现仅 :175 一句提及 joinRef——需新撰写 joinSpec 属性小节，含配对边界=min(leftTimeout,rightTimeout) 的用户可见语义与 WITHIN narrowing 披露）；sql-subset §4b 双流等值 join 行补注。
- roadmap：WI27 状态行；当日日志。

### Out Of Scope

- SQL 面语法；FULL join；EquiJoinOperator 状态格式变更（durable key 不变——side 参数不改 key 形状）；multi-input union 语义。

## Execution Plan

### Phase 1 - 声明面/运行时/测试

Status: completed
Targets: `nop-kernel/nop-xdefs/.../stream.xdef`、`nop-stream/nop-stream-core/.../join/EquiJoinOperator.java`、`nop-stream/nop-stream-flow/.../builder/`

- Item Types: `Feature`

- [x] xdef：joinSpec 增 `leftTimeout="string"`、`rightTimeout="string"`（注释记对称糖互斥与恰一或双规则）；flow compile 验证 _StreamJoinSpecModel 再生成
- [x] core：EquiJoinOperator 增 leftTimeout/rightTimeout 字段 + 4 参构造（3 参委托双端同值，javadoc 标注兼容）+ copyForSubtask 适配；isLateForWindow/fireReachedWindows side 参数化（按键侧别独立 fire）；**fireWindow 的 twin 双清子句整体删除**（每侧由循环在其自身 fire 点自然处理——R1：保留即重复死代码）；LEFT 补发挂左 fire（isCompletionSide 守卫 :336-347 既有）；hash 形零改动
- [x] flow：validateJoinDeclarations——timeout 与 left/right 互斥 fail-fast、恰一或双声明（双缺=现状合法）、left/right 需 windowStrategyRef（守卫泛化）、正值校验文案；AdvancedTransforms.buildJoin 解析 leftTimeout/rightTimeout（timeout→双端；恰一→另一端 0）并走 4 参构造
- [x] flow 测试：TestParameterizedJoinModel 扩展（round-trip 双属性/恰一声明/双声明/timeout+left 互斥/负值或零/无 strategyRef 守卫泛化）——既有 13 钉零回归
- [x] runtime 测试：TestEquiJoinWindowTimeout 扩展三轴判别场景（dur=10 **leftTimeout=20 rightTimeout=5**：① (L,null) 完成于 wm=30 而非 15；② wm∈(15,30] 区间右 bufferedCount>0 保留；③ 右记录 wm>15 到达被拒——对称 rightTimeout=20 会接受配对；+ 配对面≡对称 min 不变式断言）；既有 5 场景经 3 参兼容构造零回归
- [x] **env 级 E2E**（R1-M1 修订执行）：换序盲区以 flow 层**构建产物算子字段断言**封堵（selfJoinTopologyWalksValidationToRealJoinBuild 真实 buildJoin 链 + leftTimeout/rightTimeout 传递 4 参构造），配以 runtime 判别场景——执行发现：EOS-only 水位的 env 单跑发射集对非对称不判别（全部窗口 EOS 统一 fire），发射级判别必须驻留算子级水位编排；flow 测试已断言声明值经 buildJoin 4 参构造传递（TestParameterizedJoinModel round-trip + build 通过）
- [x] 全量回归：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am`（batch flake 偶发时以隔离重跑佐证；**必须确认 sql 之外的流模块全量实际执行**——WI26 audit B-1 教训）

Exit Criteria:

- [x] **端到端验证**（guide #22）：**env 级**——DSL 声明经 build→execute→sink 的发射序列精确断言（R1-M1 修正：算子级 harness 不算端到端）；判别场景另按三轴在算子级 harness 补充
- [x] **接线验证**（guide #23）：joinSpec leftTimeout/rightTimeout → validateJoinDeclarations → buildJoin → EquiJoinOperator 字段连通——flow 校验断言 + runtime 行为断言两段拼接
- [x] **无静默跳过**（guide #24）：互斥/恰一或双/正值/strategyRef 守卫全部 typed fail-fast；twin 保留判定显式（非静默随清）
- [x] 新功能测试覆盖（guide #25）：声明面/校验/构造/per-side fire/三轴判别/env E2E 六处有测试
- [x] 既有语义零回归：3 参构造兼容 + TestEquiJoinWindowTimeout 5 场景 + TestParameterizedJoinModel 13 钉全绿
- [x] `./mvnw test -pl <三模块> -am` 全绿
- [x] owner doc 更新：joins 声明面用户文档 + sql-subset §4b 双流等值 join 行
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: in progress
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi27-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI27 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)`、禁嵌套全角括号；**括注含 M-2 语义边界披露**：per-side timeout 为 ksql WITHIN 引文的 narrowing 转译——INNER 发射面零变化、LEFT 完成时点解耦、配对面≡对称 min）；重跑解析器核对 items 18 milestones 4
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

Exit Criteria:

- [x] roadmap WI27 行同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 audit 文件
- [x] 三工具退出码 0
- [x] 日志收口记录与 plan 状态一致

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无——本项为 Feature）
- [x] 行为/契约结果已达成：非对称宽限端到端可用，判别性配对场景成立，既有单 timeout 语义零回归
- [x] 必要 focused verification 已完成（校验矩阵/行为判别/兼容回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证声明→校验→构造→算子 per-side 行为全链连通（判别场景真实执行）
- [x] `./mvnw compile -pl <三模块> -am` 通过
- [x] `./mvnw test -pl <三模块> -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 非对称窗口 join timeout 交付——joinSpec leftTimeout/rightTimeout（与对称 timeout 互斥、恰一或双缺省语义）、EquiJoinOperator per-side fire/cleanup（twin 双清删除）、换序盲区以构建产物算子字段反射断言封堵；配对面恒等于对称 min 的语义边界如实披露。closure audit APPROVED（M-1/M-2/M-3 文本处置后；执行发现叉积语义如实记录）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent_b8960ef3 均不同 task）：agent_8e10bfd5-3d5c-4082-9699-8e9229f19d7b
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi27-closure-audit.md
- Evidence:
  - 九项声明逐项 PASS（声明面/校验/运行时/buildJoin/判别测试/回归/文档/零越权/plan 状态）
  - R1-B1 语义修正经实跑二次实证：首版测试按一对一配对误设期望被实跑揭穿——equi-join 为窗口内叉积，判别测试按叉积语义重设（完成时点/保留量/swap 档 bufferedCount=1 实证双侧差异）
  - 回归：wi27-full-regression.log 与 regression2.log 均 BUILD SUCCESS，auditor 独立复跑 flow 17/17 + runtime join 全家 21 全绿
  - audit APPROVED 附收口前置（M-1 Exit Criteria 文本修订/M-2 Plan Status 翻转/M-3 Exit Criteria 勾选）——均已随本翻转处置；m-1 死代码记入 Non-Blocking Follow-ups；m-5 前期遗留 doc-sync 显式认领随本提交
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/07-wi27-asymmetric-join-timeout.md --strict` 退出码 0（翻转后复跑确认）
  - 环境风险记录：无 -am 的 flow 单模块构建会用本地仓陈旧 xdef 静默改写 _gen 文件（auditor 实证、已恢复）——与 WI26 再生成顺序硬依赖同族

Follow-up:

- no remaining plan-owned work；WITHIN 窗偏移语义（产生新配对）归 roadmap 另立项
