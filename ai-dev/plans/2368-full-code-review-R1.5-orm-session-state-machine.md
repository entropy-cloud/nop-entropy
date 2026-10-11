# 2368 full-code-review R1.5 — nop-orm 会话状态机修复（P1-2.1-001/002）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.5 行；finding `P1-2.1-001`/`P1-2.1-002`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.1-orm-ormmodel-deep-audit.md`，会话状态机通道）

## Purpose

修复 nop-orm 会话状态机两个 P1：①`detach()` 不脱离一级缓存（pre-detach 脏仍 flush、post-detach 修改反常落库、contains/get 仍命中）且与 `evict` javadoc 契约冲突；②DELETING/DELETED 实体被查询装配（`internalAssemble`）无条件重置 MANAGED——同会话 delete 后查询静默取消 DELETE（行残留）或删除意图反转为 UPDATE。

## Current Baseline

- **P1-2.1-001**：`OrmSessionImpl.detach(entity, selection)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1368-1394`）无 `checkValid()`、无 `cache.remove`；`evict`（:304-308）= `checkContext+checkValid+cache.remove`；`IOrmSession.evict` javadoc（`IOrmSession.java:86`）明示「从 session 移除、不再参与 flush」而 `detach`（:209）无 javadoc。`orm_detach()`（`support/OrmEntity.java:403-407`）只清 enhancer/locked，状态保持 MANAGED+oldValues。
- **P1-2.1-002**：`internalAssemble`（`OrmSessionImpl.java:365-412`）的 `!isManaged()` 分支对 PROXY 初始化为设计意图，但 DELETING/DELETED 同样命中 → 全量 `orm_internalSet` 后 ：398-407 无条件 `entity.orm_state(MANAGED)`（revision 表按 nopRevType 置 DELETED）→ DELETE 唯一入队在 internalFlush→flushDelete，重置后永不入队。delete 流转：`delete()` 置 DELETING（:991）→ flush 发 DELETE → `persisterPostDelete` 置 DELETED（:1160-1162）。到达路径：①EQL select→JdbcQueryExecutor.transformRow→`internalMakeEntity`（:1459-1465，缓存命中直接返回不校验状态）→internalAssemble；②findByExample→OrmAssembly.readEntity。
- **测试基建**：`AbstractOrmTestCase`（真实 H2 + `orm().runInSession` / `sessionFactory.openSession` / `jdbc().findFirst(new SQL(...))` 行数断言先例齐备——`TestOrmSessionState` 的 `testTenantPkStatelessSave`/`countRows` 模式）；实体 `test.entity.TestTenantPkTable`（复合主键）与 `SimsCollege` 在用。
- nop-orm 测试基线：210 tests（skip 6）全绿（2.1 审计 2026-10-04 实测锚点）。

## Goals

- `detach` 对齐 evict 契约：checkValid + cache.remove——detach 后 contains/get 不命中、不参与 flush（pre/post-detach 修改均不再落库）；javadoc 显式化。
- 装配路径不再重置终态：DELETING/DELETED 实体被查询装配时保持状态与内存值（flush 仍发 DELETE）。
- 会话级回归：delete→EQL select→flush 断言行数（修复前该测试暴露 UPDATE 复活/行残留）。
- owner doc detach 契约文档（2.1 审计移交注记「detach/attach 契约文档（P1-2.1-001 修复伴随）」）。

## Non-Goals

- 不修 P2-2.1-001..005（attach 顶替/删除后重存/CascadeFlusher 重入/to-one 级联删除跳过/useRevision）——P2 台账通道。
- 不改 `orm_detach()` 的实体层行为（enhancer 清除语义保留；状态对齐由 session 侧 cache.remove 达成，与 evict 完全同构）。
- 不动 revision 表装配语义（:398-407 的 nopRevType→DELETED 是历史版本实体语义，非本条缺陷）。
- MISSING 状态装配行为不变（审计未点名，不扩scope）。

## Scope

### In Scope

- `OrmSessionImpl.detach`：head 加 `checkValid()`；`entity.orm_detach()` 后加 `cache.remove(entity)`。
- `IOrmSession.detach`：javadoc 契约显式化（对齐 evict 措辞：从 session 移除、不再参与 flush、级联 selection 语义说明）。
- `OrmSessionImpl.internalAssemble`：head 加 DELETING/DELETED 守卫（skip 装配——保留状态与内存值；注释说明「同事务查询仍返回该行，但 delete 意图不被静默取消」）。
- 回归测试（session 级）：detach 三断言（contains=false / post-detach 修改不落库 / closed session 响亮失败）；delete→EQL select→flush 行数为 0 + 状态保持 DELETING。
- owner doc：`docs-for-ai/02-core-guides/model-first-development.md` 补 detach 契约小节（含与 evict 的关系）。

### Out Of Scope

- `analyze(InputStream)` 等其他变体无此问题面；attach 对称修复（P2-2.1-001）；`orm_detach()` 实体层重构。

## Execution Plan

### Phase 1 - 两修复 + 回归测试

Status: completed
Targets: `OrmSessionImpl.java`、`IOrmSession.java`、`nop-persistence/nop-orm/src/test/java/io/nop/orm/session/`（新测试类）

- Item Types: `Fix`

- [x] `detach`：head `checkValid()`；`orm_detach()` 后 `cache.remove(entity)`（与 evict 完全同构）
- [x] `IOrmSession.detach` javadoc：契约显式化（移除出 session/不再 flush/**级联 detach 已加载关联实体**〔未初始化 proxy 保持 PROXY 留 session——既有语义〕；与 evict 关系一句话）
- [x] `internalAssemble` head DELETING/DELETED 守卫（skip + 注释；不触碰 PROXY 初始化与 revision 表语义）
- [x] 回归测试 A（P1-2.1-002）：save→flush→delete（断言 state=DELETING）→ EQL select 同 id → 断言 state 仍 DELETING → flush → `jdbc` 行数断言 0（修复前：UPDATE 复活或行残留）
- [x] 回归测试 B（P1-2.1-001）：save 两个同类型实体→flush→get 其一→detach→**修改被 detach 实体属性 + 修改另一实体保持 session dirty**（判别布景：修复前 forEachDirty 扫全类型缓存会把被 detach 实体的 pending oldValues 当 UPDATE 落库）→flush→被 detach 实体行值断言为修改前值、另一实体行值为修改后值→`contains`=false；closed session 上 detach 抛 ERR_ORM_SESSION_CLOSED。**修复前必须失败的断言集合=被 detach 实体的行值断言**

Exit Criteria:

- [x] 回归测试 A/B 全绿，且修复前判别证据：**优先 `git stash` 两处修复跑测试一次留输出**（测试 B 的被 detach 实体行值断言 + 测试 A 的行数断言修复前 FAIL）；若以实跑探针为准，探针代码+输出记录进 daily log（纯推理 trace 不接受）
- [x] `./mvnw test -pl nop-persistence/nop-orm -am` 全绿（210 基线 + 新增，skip 6 保持；本仓 Maven 4 下 -pl 需路径形式——审查实测）
- [x] `./mvnw test -pl nop-persistence/nop-orm-model -am` 全绿（相邻模块回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 与收口

Status: completed
Targets: `docs-for-ai/02-core-guides/model-first-development.md`、roadmap、轮次 index、本 plan

- Item Types: `Decision`（契约文档）、`Proof`

- [x] model-first-development.md 补 detach 契约小节（对齐 evict 语义、级联 selection、不再 flush）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0
- [x] roadmap R1.5 行 → done；index P1-2.1-001/002 → fixed + MR 表 R1.5 行
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] owner doc 与 index/roadmap 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-2.1-001 修复落地：detach = checkValid + cache.remove，契约 javadoc 化，回归锁定
- [x] P1-2.1-002 修复落地：DELETING/DELETED 装配守卫，delete→select→flush 行数回归锁定
- [x] focused verification：nop-orm + nop-orm-model 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（P2 族走台账）
- [x] owner docs 已同步（model-first-development.md detach 契约）
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：守卫/移除真实改变行为（回归测试在修复面断言，非恒真断言）
- [x] `./mvnw test -pl nop-persistence/nop-orm -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### P2-2.1-001..005 会话状态机 P2 族

- Classification: `out-of-scope improvement`（P2 台账通道承载）
- Why Not Blocking Closure: 审计裁定其为 P2；R1.5 只承载两个 P1（roadmap 行原文）
- Successor Required: `no`

## Non-Blocking Follow-ups

- no remaining plan-owned work

## Closure

Status Note: P1-2.1-001/002 双 P1 修复落地：detach=checkContext+checkValid+级联先行重排+cache.remove（与 evict 完全同构，附带消除预存 enhancer 先清 NPE 面）+IOrmSession javadoc 契约化+owner doc 对照表；internalAssemble head DELETING/DELETED 跳过装配守卫（裁定语义注释在位，PROXY 初始化路径不受影响）。**判别证据可独立复现**：stash 修复跑测试 3/3 FAIL（DELETING→MANAGED/contains 命中/closed 不抛）→恢复后 3/3 绿。nop-orm 216/0/skip6 + orm-model 34/0 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_1febb901-e554-41f7-9ba1-00630e0cec30
- Evidence:
  - **P1-2.1-001 修复**：PASS——detach head checkContext+checkValid（:1378-1380）、级联先行（detachProp 递归在 orm_detach :1407 之前）、cache.remove（:1410）与 evict（:304-308）完全同构；IOrmSession detach javadoc 六点契约与实现逐条一致。
  - **P1-2.1-002 修复**：PASS——internalAssemble head 守卫（:365-378）仅命中 isDeleting()/isDeleted()，PROXY 独立枚举值走原 !isManaged() 初始化分支不受影响（全量套件绿佐证懒加载无回归）。
  - **Anti-Hollow**：PASS——(a) 判别布景在测试 :106-107（第二实体保持 dirty）；(b) **auditor 独立重做 stash 判别**：stash→3/3 FAIL（失败模式与声明逐一吻合：DELETING→MANAGED/contains true/closed 不抛）→pop→3/3 PASS；(c) 守卫仅 DELETING/DELETED 早退。
  - **全量复跑**：PASS——auditor 亲测 `./mvnw test -pl nop-persistence/nop-orm -am` exit 0（nop-orm 216/0/0/skip6 + orm-model 34/0）；doc-links --strict 0；hollow scan 0。
  - **契约核验**：PASS——javadoc↔实现一致；owner doc 对照表与代码一致；orm_detach() 实体层未改动（Non-Goal 兑现）。
  - **状态回填四处一致**：PASS——roadmap R1.5(:134)/index P1-2.1-001/002(:90-91)/index MR R1.5(:258)/daily log。
  - **Deferred 分类**：PASS——P2-2.1 族台账在案；执行期新发现的双向一对一 detach 级联递归预存缺陷已登记 index P2 裁决台账（P2-R1.5-001）。
  - Advisory 处置：A1 预存缺陷登记（已补 index P2 台账行）；A2 随本 closure 提交（已做）；A3 plan「修复前必须失败断言集合」措辞与实测不符（实测首个失败为 contains 断言，fail-fast 使行值断言不可达——判别力成立，留档本段）；A4 测试基线锚点漂移（210 系 2.1 审计实测后陈旧值，实测 216=基线+新增+其他增量，全绿/skip6 保持实质满足）。

Follow-up:

- P2-R1.5-001 双向一对一关联 detach(null selection) 级联递归 StackOverflow（预存缺陷，已登记 index P2 裁决台账）
- P2-2.1-001..005 族（P2 台账通道）
