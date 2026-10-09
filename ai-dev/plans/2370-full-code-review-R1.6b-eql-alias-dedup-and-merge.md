# 2370 full-code-review R1.6b — nop-orm-eql 别名查重+合并提升修复（簇 B/C：P1-2.2-006/007）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.6b 行；finding `P1-2.2-006`（簇 B，dim10b-03）/`P1-2.2-007`（簇 C，dim10b-11=`dim16-03`）（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.2-orm-eql-deep-audit.md`；过程细节 `_tmp/fcr-2.2-coll.md`/`_tmp/fcr-2.2-coll-r2.md`）

## Purpose

修复 nop-orm-eql 集合操作符的两条 P1：①生成别名绕过用户别名查重（`t1` 遮蔽→关联自比/条件恒真，静默数据级错误）；②`_some` 跨 OR 分支合并 logicUnitNode 被行 154 覆盖（合法 EQL 编译失败 unknown-alias[t1] 或静默篡改）。

## Current Baseline

- **簇 B**：`CollectionOperatorTransformer.makeAlias`（`compile/CollectionOperatorTransformer.java:488-495`）直接 `aliasGenerator.genTableAlias()`，与 `EqlTransformVisitor.makeTableAlias`（:1195-1207 do-while 查重 `assignedAliases`）共享同一 `SeqAliasGenerator` 却绕过查重；`assignedAliases` 由 collectNames 预收用户别名（:233）。审计端到端探针：`select t1.id from AppUser t1 where t1.roles._some.name='admin'` → 子查询同名 `t1` 遮蔽，`(t1.SID = t1.USER_ID)` 同表自比。transformer 构造于 `EqlTransformVisitor`（:191，仅传 aliasGenerator）。
- **簇 C**：`mergeConditions`（同文件 **:154** `scope.setLogicUnitNode(findLogicUnitNode(scope2.getLogicUnitNode().getASTParent()))`，位于 `canMergeScope(scope, scope2)` 返回 true 之后）无条件覆盖 `canMergeSomeScopes`（:242 附近）刚设置的 `commonAnd`。审计五探针（D1/M1/M2/M1b/SILENT1）：`where o.roles._some.name='a' and (o.roles._some.userId='b' or o.name='x')` → unknown-alias[t1]；SILENT1（外层用户别名恰为 t1+同名列）静默篡改并叠加簇 B。
- **行 154 承载性核验（plan 审查 R1 勘正，修正初版「恒等或有害可直接删」的错误裁定）**：`collectCollectionScopes` 不设 logicUnitNode，由 `buildLogicNodes`（:101-107）统一设置；`canMergeSameBranch` true 时不经行 154（唯一安全出口）；**`canMergeSomeScopes` 路径 1（平铺 parent1==parent2）与 `canMergeAllScopes` 均不设节点——行 154 是这两条路径唯一的共享父提升点**，直接删除将打破 6 个既有测试（testMultipleSomeAndConditionsMerge 等）。正确修法=提升下沉分支+删无条件行（见 Phase 1）。
- 既有测试嵌套/合并用例均为平铺形态（恰好绕开覆盖路径，:155 删除不破坏既有 21 测试——审计核对）。
- 测试基建同 R1.6a（`TestEqlCompileSql.relationContext()`、`compile(ctx,eql)`、SQL 文本断言）。

## Goals

- 生成别名与用户别名查重一致：`makeAlias` 跳过 `assignedAliases` 并回登记（与 `makeTableAlias` 同纪律）。
- 行 155 覆盖删除：合并后 logicUnitNode 由 canMerge* 自身决定（commonAnd/自身分支），D1/M1/M2/SILENT1 形态编译正确。
- 回归测试钉住审计五探针形态。

## Non-Goals

- 不改 `transformCollectionScope:311-314` 的 owner 改写校验（行 155 删除后悬空 owner 的生产者已消除；校验属防御纵深——行 155 删除 + 簇 B 别名查重后，重造该缺陷需要新的未知 bug，登记 follow-up）。
- 不改 SeqAliasGenerator 前缀命名（审计建议可选项，非必需）。
- 不修 dim10b-04/05（P2 文档/语法，台账通道）。

## Scope

### In Scope

- `CollectionOperatorTransformer`：构造函数增 `Set<String> assignedAliases` 参数；`makeAlias` do-while 查重+回登记；`canMergeSomeScopes` 路径 1 与 `canMergeAllScopes` 分支内提升 + 删除 :154 无条件覆盖行（附 P1-2.2-007 注释）。
- `EqlTransformVisitor`：构造 transformer 时传入 `assignedAliases`。
- 回归测试：审计探针五形态（D1 编译成功且单 exists 含双条件 / SILENT1 无自比恒真 / t1 用户别名关联正确 / M1/M2 对称 OR 形态）+ 既有全量不回归。

### Out Of Scope

- P2-2.2-003（CTE 别名）/004（钩子）等；P3。

## Execution Plan

### Phase 1 - 两修复 + 回归测试

Status: completed
Targets: `CollectionOperatorTransformer.java`、`EqlTransformVisitor.java`、`src/test/java/.../TestEqlCollectionAliasAndMerge.java`（新）

- Item Types: `Fix`

- [x] `makeAlias`：do-while 跳过 assignedAliases + 选中后回登记（与 makeTableAlias :1195-1207 同纪律）
- [x] 构造函数与 EqlTransformVisitor :191 调用点接线
- [x] `makeTableAlias`（:1193-1205）生成成功后回登记 assignedAliases（审查 advisory：FROM/join 自动别名先于 transformer 执行且不回登记，残余碰撞面一并闭合；表别名 t 前缀与列别名 c 前缀互不干扰）
- [x] 簇 C 修正（审查 R1 处方，非简单删除）：**把提升动作下沉进需要它的分支**——`canMergeSomeScopes` 路径 1（parent1==parent2 平铺形态）与 `canMergeAllScopes` 内各自 `setLogicUnitNode(findLogicUnitNode(共享父))`；`canMergeSomeScopes` 路径 2 保留 ：242 的 commonAnd 设置；然后删除 mergeConditions :154 的无条件覆盖行（附 P1-2.2-007 注释）。审查实证：该行对平铺/ALL 形态是唯一提升点（承载性），直接删除会打破 6 个既有测试〔testMultipleSomeAndConditionsMerge 等〕；审计「仅当 node==原始节点才回填」变体对链式合并不安全，不采纳
- [x] 新增回归测试（模型：局部 context 构造器在 relationContext 基础上**给 AppRole 增加 name 列**〔原模型 AppRole 无 name——审查 B2 实证〕，形态同 R1.6a 的 keyPropRelationContext 先例）：①D1 形态编译成功、SQL 单 exists 含双条件且无 unknown-alias；②`select t1.id from AppUser t1 where t1.roles._some.name='admin'` 关联条件为 `t1.SID = t<next>.USER_ID`（非自比）；③SILENT1 形态（t1 别名+同名列组合）无恒真谓词；④M1/M2 双 OR 对称形态**钉 SQL 形状**（非语义等价断言——合并契约归 dim10b-04 文档通道）；⑤无集合操作符的普通查询别名行为不变
- [x] 修复前判别：stash 两处修复跑新测试，D1/SILENT1/t1 关联三用例必须 FAIL（unknown-alias 或自比谓词），留输出入 daily log

Exit Criteria:

- [x] 回归测试五形态全绿；stash 判别记录在案
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql` 全绿（191+新增）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [x] roadmap R1.6b 行 → done；index P1-2.2-006/007 → fixed + MR 表 R1.6b 行
- [x] owner doc 裁定：别名纪律为编译器内部行为（eql-and-database-compatibility.md 无对应契约面）→ 预期 No owner-doc update required，执行时核实
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] roadmap 与 index 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 簇 B：makeAlias 查重+回登记落地（用户别名遮蔽探针回归锁定）
- [x] 簇 C：行 155 覆盖删除落地（D1/M1/M2 形态回归锁定）
- [x] focused verification：nop-orm-eql 全量绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 裁定记录（预期 No owner-doc update required）
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：stash 判别真实 FAIL→PASS（非恒真断言）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### transformCollectionScope owner 改写校验（审计建议的防御纵深）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 行 155 删除消除了悬空 owner 的唯一已知生产者；簇 B 别名查重消除了遮蔽放大器；重造缺陷需未知新 bug
- Successor Required: `no`

## Non-Blocking Follow-ups

- no remaining plan-owned work

## Closure

Status Note: 簇 B/C 双 P1 修复落地：makeAlias 查重+回登记+makeTableAlias 回登记（别名纪律统一，闭合残余碰撞面）；提升下沉 canMergeSomeScopes 路径1/canMergeAllScopes 分支+删 ：154 无条件覆盖（审查勘正承载性后的正确修法）。**判别证据可独立复现且失败输出与审计探针逐字吻合**（unknown-alias[t1]/t1.SID=t1.USER_ID 自比/SILENT1 悬空引用）；既有 32 transformer 测试零期望 SQL 改动全绿（平铺行为保持）。nop-orm-eql 197 tests 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_cf890090-3b87-4669-b867-e9ff4060f01e
- Evidence:
  - **Anti-Hollow stash 判别独立重做**：PASS——auditor 独立执行三文件 stash：修复态 6/6 绿 → stash 后 6 用例中恰好 3 判别用例 FAIL（D1=unknown-alias[t1] ERROR/dim10b-03 自比/SILENT1 篡改+自比，输出与审计探针逐字吻合；3 个形状对照用例修复前即绿=判别力非恒真）→ pop 后 6/6 绿+全模块 197/197 绿。
  - **簇 B**：PASS——ctor 双参+makeAlias do-while 查重回登记（Transformer:59-65/:499-511）+visitor :191 接线+makeTableAlias :1197-1203 回登记。
  - **簇 C**：PASS——提升下沉 :242（路径1）/:273（ALL）+路径2 commonAnd :251 保留+原 :154 位置留删除注释；提升值与原 ：154 在 parent1==parent2 路径逐点等价；既有 32 测试 diff 零期望 SQL 改动。
  - **契约核验**：PASS——assignedAliases 消费方全为生成去重循环，回登记方向安全；用户别名经 collectNames 预收路径不受影响；197 全量含 join/子查询/CTE 实证无合法场景破坏。
  - **状态回填四处一致**：PASS——roadmap R1.6b(:136)/index :97-98/MR :261/daily log。
  - **Deferred 分类**：PASS——owner 改写校验=防御纵深建议（审计原文），非 live defect 修复要求；生产者（:154）已删除+四形态回归钉死。
  - Advisory 处置：A1 baseline 计数 21 系审计期时点值（执行时点 32，三处回填一致——留档）；A2 owner 改写校验登记位置为 Deferred But Adjudicated（措辞统一留档）；A3 冗余断言 unknown 留档（判别力来自编译步骤）；A4 genColumnAlias 不回登记为修复前既有行为（列别名 c 前缀无碰撞面，超范围）。

Follow-up:

- transformCollectionScope owner 改写校验（Deferred But Adjudicated，optimization candidate）
- genColumnAlias 回登记对称性（A4，超范围 watch）
