# nop-code 不变式闭环 I6 — Cycle 1 循环收口报告

> Status: active
> Last Reviewed: 2026-08-13
> Source: I6 计划 `ai-dev/plans/2026-08-13-1059-7-nop-code-invariant-i6-cycle-closure.md`
> 输入: I4 计划（Phase 1-2 completed / Phase 3-9 planned）+ I5 `i5-full-green-record.md`（I4 已执行面全量验证全绿）+ I3 `i3-adjudication-matrix.md`（§A/§B/§D/§E 裁决）+ `invariant-catalog.md`（INV-01..05）+ `ar-status-matrix.md`
> 后继: 本报告驱动 roadmap Cycle 1 状态裁定（稳态判定 + 复触发条件 + Cycle 2 后继派生）
> 方法论: `ai-dev/skills/invariant-loop-audit-prompt.md`

## 目的

对 Cycle 1（I0→I6）做循环级收口：汇总修复统计、执行稳态判定（可复现程序）、登记复触发条件、派生 Cycle 2 后继路径。

> **关键前提诚实声明**：本报告执行时，**I4 尚未全部完成**——Phase 1（OOM 族 WP-1/WP-2）+ Phase 2（幂等性 WP-3）已 `completed`，但 Phase 3-9（WP-7 搜索同步 / WP-4 删除路径 / WP-5 缓存不可变 / WP-6 截断可观测 / WP-8 数据一致性 / WP-9 error-handling / WP-10 安全/权限）仍 `planned`。I5 `i5-full-green-record.md` 已显式声明其验证范围限于「I4 已落地范围（Phase 1-2）」并标注「Cycle 1 未达稳态」。因此本报告的统计、棘轮前进量均锚定 **I4 已执行范围**；稳态判定按 Dependency Graph 条件诚实裁定（见 §3）。

---

## §1. Cycle 1 修复统计（I4 已执行范围）

### 1.1 I4 工作包落地数（WP 级，以 I4 closure evidence / I5 record 实算为准）

> 计数原则：以 I4 plan checklist 实际勾选状态为准。I4 Phase 1-2 已 `[x]`，Phase 3-9 全部 `[ ]`。I3 §E.2 的 35 条 Phase 2 P1 为 §B 枚举毛值（含跨族交叉引用），统计时取去重净值——**实际已落地 = Phase 1 的 38 条 red-list 真违规中的 WP-1/WP-2/WP-3 覆盖部分**。

| I4 Phase | 工作包 | 不变式 | 状态 | 已落地修复项（实算） |
|----------|--------|--------|------|---------------------|
| Phase 1 | WP-1 OOM-查询上限 | INV-04 | completed | query-limit 19 真违规全部修复（P0×13 / P1×6）+ 14「已接受有界」防御性 `setLimit(1)` → 全族 33→0 |
| Phase 1 | WP-2 OOM-字段最小化 | INV-01 | completed | entity-field-min 12 真违规全部投影修复（P0×6 / P1×6）+ AR-64 缓存路径投影 → 24→12（剩 12 为已接受全实体 DTO 映射） |
| Phase 2 | WP-3 幂等性 | INV-03 | completed | idempotency 2 red-list 锁（indexDirectory/indexFile）迁移到 IDEMPOTENCE_TABLE（query-first upsert）→ 2→0 |
| Phase 3 | WP-7 搜索引擎同步 | INV-03 | **planned** | 0（未执行） |
| Phase 4 | WP-4 删除路径完整性 | INV-03 | **planned** | 0（未执行；含 ORM plan-first AR-149/150） |
| Phase 5 | WP-5 缓存不可变性 | INV-05 | **planned** | 0（未执行） |
| Phase 6 | WP-6 截断可观测性 | INV-04 子项 | **planned** | 0（未执行） |
| Phase 7 | WP-8 数据一致性语义 | — | **planned** | 0（未执行） |
| Phase 8 | WP-9 error-handling | — | **planned** | 0（未执行） |
| Phase 9 | WP-10 安全/权限 | — | **planned** | 0（未执行；ask-first） |

**已落地小计**：3 个工作包（WP-1/WP-2/WP-3）= 38 条 red-list 真违规中**已收敛 33 条**（query-limit 19 + entity-field-min 12 + idempotency 2）。

**未落地（I4 范围内，仍 planned）**：7 个工作包（WP-7/4/5/6/8/9/10），对应 I3 §A.4 对抗探查 5 族中的 3 族（WP-7 搜索同步 / WP-4 跨文件孤儿 / WP-5 缓存不可变）+ §B.4 data-consistency 10 + §B.5 auth 3 + §B.6 error-handling 子串/吞原子集 + §B.9 AR-51。

### 1.2 门禁棘轮前进量（修复前 → 修复后，I5 record §2 权威）

| Family | I1 baseline（修复前） | I4 Phase 1-2 后（修复后） | real-violation | 说明 |
|--------|---------------------|--------------------------|----------------|------|
| query-limit (INV-04) | 33 | **0** | 0 | **超额**：19 真违规 + 14 已接受有界防御性 setLimit → 全族归零（优于原计划 ≤14） |
| entity-field-min (INV-01) | 24 | **12** | 0 | 12 真违规投影修复；剩 12 == red list §2「已接受全实体」（DTO 映射/CLOB 必要） |
| delete-contract (INV-02) | 0 | **0** | 0 | 退化契约保持（物理删除；live 无 useLogicalDelete） |
| idempotency (INV-03) | 2 red-list 锁 | **0** | 0 | indexDirectory/indexFile 迁入 IDEMPOTENCE_TABLE；KNOWN_NON_IDEMPOTENT 空 |

**棘轮前进净量**：query-limit −33（33→0）/ entity-field-min −12（24→12）/ idempotency red-list 锁 −2（2→0）/ delete-contract ±0（退化）。**四族 real-violation 归零**（I5 record §2 权威，I4 已执行范围）。

### 1.3 门禁覆盖率提升（I0 → I1 → I4-已执行）

| 阶段 | 门禁覆盖 | real-violation 命中 |
|------|---------|---------------------|
| I0 基线 | **零门禁**（`ar-status-matrix.md` 148 条发现无任何可执行门禁拦截） | N/A（无门禁） |
| I1 沉淀 | 四族门禁（INV-01 entity-field-min `.mjs` / INV-02 delete-contract `.mjs` / INV-03 idempotency JUnit / INV-04 query-limit `.mjs`） | query-limit 33 / entity-field-min 24 / delete-contract 0 / idempotency 2 锁 |
| I4 已执行（Phase 1-2） | 四族门禁（同 I1，棘轮 baseline 前进） | **real-violation 全部归零**（query-limit 0 / entity-field-min 0 真违规 / delete-contract 0 / idempotency 0 锁） |

> 门禁从「零」到「四族 + real-violation 归零」是 Cycle 1 的核心结构性收益。门禁已抓到真实 live defect（幂等门禁发现 indexDirectory/indexFile duplicate-key 23505），证明门禁非空壳。

### 1.4 ar-status-matrix 改判

I3 裁定的 5 条 stale 已在 I4 Phase 1 改判（`ar-status-matrix.md`）：AR-04 / AR-092 / AR-180 / AR-179 / AR-153(r10) → `fixed` / `stale-premise`（附 live 证据，见 I3 §B.3/§B.6/§B.9）。

---

## §2. I3 裁决处置矩阵终态分布

> 数据源 = `i3-adjudication-matrix.md` §E（权威总账）。本节汇总 38 条 red-list 真违规 + 95 条 open 悬空发现的终态分布。

### 2.1 §E.1 red-list 真违规裁决总账（38 条）

| Family | 真违规数 | P0 I4 | P1 I4 | 其他 |
|--------|---------|-------|-------|------|
| query-limit（INV-04） | 19 | 13 | 6 | 0 |
| entity-field-min（INV-01） | 12 | 6 | 6 | 0 |
| idempotency（INV-03） | 2 | 2 | 0 | 0 |
| 对抗探查新增 | 5 | 3 | 2 | 0 |
| **合计** | **38** | **24** | **14** | **0** |

> 38 条真违规**全部 `I4 修复`**，零降级、零悬挂。其中已落地 33 条（WP-1/WP-2/WP-3），剩余 5 条（对抗探查 WP-7×1 / WP-4×2 / WP-5 P0×1 + WP-6 P1 子项）属 I4 Phase 3-6 planned 待续。

### 2.2 §E.2 open 悬空发现裁决总账（95 条，按 AR-ID 去重）

| Terminal | 条数 | 占比 |
|----------|------|------|
| P0 I4 修复（Phase 1 交叉） | 6 | 6% |
| P1 I4 修复（本节裁决 + Phase1 交叉 P1） | 29 | 31% |
| P2/P3 后继修复计划（successor ownership） | 27 | 28% |
| 接受为残余风险（optimization candidate / justified） | 28 | 29% |
| stale（建议矩阵改判） | 5 | 5% |
| 移出范围 out-of-scope | 0 | 0% |
| **合计** | **95** | **100%** |

> 零悬挂（每条 in-scope 发现有且仅有一个终态）。P0/P1 已确认 live defect 未降级；P2/P3 已确认 defect 均附 Why Not Blocking + Successor Path（无静默丢弃）。

### 2.3 终态汇总（38 + 95 = 133 条 in-scope 发现）

| 终态 | 条数 | 说明 |
|------|------|------|
| I4 修复（P0+P1） | 38 真违规 + 35 悬空（6 P0 交叉 + 29 P1）= **去重后 P0/P1 I4 队列** | I3 §C 定稿 10 WP；I4 已执行 WP-1/2/3（33 真违规），WP-4..10 待续 |
| P2/P3 后继修复计划 | **27** | graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-45 / AR-182（§5 派生 successor path） |
| 接受为残余风险 | **28** | optimization candidate / justified（performance 4 / dead-code 7 / 硬编码子集 / AR-75 / AR-157 / orm-schema 聚合 2） |
| stale | **5** | AR-04/092/180/179/153(r10)（已改判 ar-status-matrix） |
| out-of-scope | **0** | 本 cycle 无 |

> **诚实披露**：I3 §E.2 的 29 P1 + 6 P0 交叉 = 35「Phase 2 P1 I4 修复项」（I4 plan Current Baseline 引用的「35 条」），与 §E.1 的 38 真违规存在跨族交叉引用去重关系（AR-168/177/30/66/166/155=158 跨两表）。**I4 实际已执行的是 WP-1/WP-2/WP-3 = 38 真违规中的 33 条**；35 条悬空 P1 中仅 AR-64（WP-2 缓存路径投影）已随 Phase 1 落地，其余 34 条属 WP-4..10 待续。

---

## §3. 稳态判定（可复现程序）

### 3.1 判定程序（三步）

#### (a) 数据源

- I4 执行日志：`ai-dev/logs/2026/08-13.md` I4 Phase 1-2 条目（completed）。
- I4 plan Deferred/Follow-up：I4 Phase 3-9（WP-7/4/5/6/8/9/10）全部 `planned`，无 `[x]`；I4 plan `Deferred But Adjudicated` 仅含 AR-149/150 ORM cascadeDelete（若 plan-first 阻塞）。
- I5 `i5-full-green-record.md` §6「I4 未完成范围」：逐项列出 7 个 planned WP；§7 明确「Cycle 1 未达稳态」。
- I3 §A.4 对抗探查 5 族：3 P0（SymbolTable 可变共享引用 / 搜索去同步 / 跨文件孤儿）+ 2 P1（CallGraph 读缺 synchronized / 截断可观测）——其中 WP-5/WP-7/WP-6/WP-4 对应项未执行。

#### (b) 比较基线

- 已沉淀不变式（`invariant-catalog.md`）：INV-01 实体加载字段最小化 / INV-02 删除路径物理删除契约 / INV-03 增量索引幂等性 / INV-04 查询结果上限 / INV-05 缓存对象不可变性（5 条，其中 INV-05 仅有 catalog 陈述，**无门禁**）。
- I3 §D Cycle 2/I1 候选门禁（4 条）：INV-05 缓存不可变性门禁 / 截断可观测性门禁（INV-04 子项）/ error-handling 专项门禁 / @Auth 系统性门禁。

#### (c) 逐条比对（I4 执行中发现的缺陷是否落在已知族/候选之内）

| I4 待续 WP | 覆盖缺陷 | 落在已知族/候选？ | 判定 |
|-----------|---------|------------------|------|
| WP-7 搜索同步（AR-166） | 增量索引搜索/DB 去同步 | INV-03（已沉淀） | 同族扩展，非新族 |
| WP-4 删除路径（AR-30/66/60/149/150） | 跨文件孤儿 + cascadeDelete | INV-03 + INV-02（已沉淀） | 同族扩展，非新族 |
| WP-5 缓存不可变（AR-155/158/145/148/42/62/147） | 缓存返回可变引用 | INV-05 catalog + §D 候选门禁 | 已登记候选，非新族 |
| WP-6 截断可观测（AR-136/76/61） | 静默截断无 WARN | INV-04 子项 + §D 候选门禁 | 已登记候选，非新族 |
| WP-8 数据一致性（AR-01/10/40/41/59/63/93/132/51） | 语义正确性 | 无门禁覆盖（I3 §B.4 裁决 P1 I4） | 已知族（data-consistency），非新族 |
| WP-9 error-handling（子串/吞异常） | 子串误匹配 + 吞异常 | §D 候选门禁 | 已登记候选，非新族 |
| WP-10 安全/权限（AR-146/155/170） | @Auth 契约漂移 | §D 候选门禁 | 已登记候选，非新族 |

**比对结论**：I4 已执行范围（Phase 1-2）与待续范围（Phase 3-9）的全部缺陷，**均落在 INV-01..05 已沉淀族或 I3 §D 已登记候选之内**，无超出已知覆盖面的新缺陷模式被发现。即「零新族」成立。

### 3.2 分支裁定

> Dependency Graph 条件：I6 -- 有新族 --> Cycle 2/I1；I6 -- 零新族**且零 red** --> 稳态暂停。

- **零新族**：✅ 成立（§3.1(c) 比对，全部缺陷落在已知族/候选内）。
- **零 red**：⚠️ **部分成立但有保留**。I5 full-green 证明**四族门禁 real-violation 归零**（I4 已执行范围）。但「零 red」的完整语义依赖 I4 全部完成——I4 Phase 3-9（7 个 WP）仍 `planned`，意味着 **I3 裁决的 P0/P1 I4 修复队列中尚有未落地项**（对抗探查 3 族 P0 + data-consistency 10 + auth 3 + error-handling 子集 + 缓存不可变 + 截断可观测）。这些不是「门禁 red」（门禁已归零），而是「**未完成的修复工作**」。

**裁定：Cycle 1 未达稳态——既非干净分支 A（稳态暂停），亦非干净分支 B（派生 Cycle 2）。**

理由：
1. I6 计划 Current Baseline 明确声明「本计划 **blocked until I4 + I5 fully closed**」。I4 未 fully closed（Phase 3-9 planned），故 I6 稳态判定的**前置条件未满足**——Dependency Graph 的两分支假设 I4 已完成，当前并不成立。
2. 「零新族」虽成立，但「稳态暂停」要求 Cycle 1 修复工作收口完成。I4 Phase 3-9（7 WP）未执行，Cycle 1 仍有大量 in-scope P0/P1 修复工作待落地，此时宣布「稳态暂停」会**静默遗漏已知未修缺陷**，违反 Anti-Slacking。
3. I5 record §7 已明确「Cycle 1 未达稳态：I4 Phase 3-9 未执行」——本判定与 I5 权威结论一致。

**最终裁定**：**Cycle 1 维持 `active`——本次为「过渡性循环收口（interim closure）」，确定性稳态判定 DEFERRED（推迟）**。待 I4 Phase 3-9 全部完成 → 触发 I5 re-verification（以现 I5 plan 为模板）全绿后 → 由后续 I6-revisit 做确定性稳态判定（届时按 Dependency Graph 二选一：零新族且零 red → 稳态暂停；或发现新族 → 派生 Cycle 2/I1）。

> **为何不强行二选一**：Dependency Graph 的两个分支是为「I4 已完成」设计。在 I4 未完成时强行选分支 A 会掩盖未修缺陷；强行选分支 B（派生 Cycle 2）则无依据（零新族）。诚实做法是显式声明前置条件未满足 + 推迟判定，并锁定后续触发路径（§4 复触发条件 + §5 后继派生）。

---

## §4. 复触发条件登记（roadmap Loop Rule）

> roadmap `Loop Rule` 原为引用 nop-stream 的一行存根。本次登记 nop-code 专属复触发条件（≥3 条）。

### 4.1 Cycle 1 继续执行触发（首要——I4 未完成，立即生效）

- **T0（首要，立即）**：I4 Phase 3-9 未完成 → 直接恢复 I4 执行（WP-7/4/5/6/8/9/10），**无需等待复触发**——这是 Cycle 1 的剩余 in-scope 工作，不是「复触发」场景。I4 完成后触发 I5 re-verification，再由 I6-revisit 做确定性稳态判定。

### 4.2 Cycle 2 / 稳态打破触发（稳态建立后生效）

1. **CI 门禁变红**：`check-nop-code-invariants.mjs` strict 模式（或 `--baseline` ratchet 模式）报告 `[NEW]` 违规，或 `TestNopCodeIndexIdempotencyInvariant` 的 red-list 锁变红（KNOWN_NON_IDEMPOTENT 新增条目）。→ 启动新 Cycle（或退回 I4 修复）。
2. **审计目标集结构变更**：新增 / 重命名 SearchService / IndexManager / CodeClassLoader / 删除路径方法（`audit-target-set.md` 的 6 服务类方法清单 + ORM 11 实体表发生增删改）。→ 重新激活 I0 盘点（审计目标集 refresh）→ 走 I1..I6。
3. **周期复探**：季度（或 nop-code 大版本发布后）主动重跑四族门禁 + 对抗探查（I2 三面：并发 / OOM / 跨文件孤儿+搜索去同步），即使门禁绿也探查是否暴露已知族外的新失败模式。→ 若发现新族，派生 Cycle 2/I1。

---

## §5. Cycle 2 后继派生（successor path）

> 每条后继项附 `Why Not Blocking Cycle 1 Closure`（I3 已裁定，此处汇总引用）。本节派生的后继路径在 Cycle 1 维持 active 期间作为「I4 待续 / Cycle 2 候选」登记，**不替代 I4 Phase 3-9 的执行**。

### 5.1 §D Cycle 2 / I1 候选门禁 successor path（4 条）

| 候选门禁 | 覆盖失败族 | 依据 | Successor Path | Why Not Blocking Cycle 1 Closure |
|---------|-----------|------|----------------|----------------------------------|
| **INV-05 缓存不可变性门禁** | concurrency-lock 11 + WP-5 全部 | 11 条 open 全无门禁覆盖（覆盖率 0%）；AST/regex 无法覆盖「缓存返回是否不可变快照」「读方法是否同同步保护」 | Cycle 2 / I1：`.mjs` 静态扫描（缓存管理类返回内部集合引用）+ JUnit 单线程确定性探针（addToCache 后快照不变）+ 可选并发 canary | 门禁实现属 Cycle 2/I1（Loop Rule 预授权）；Cycle 1 通过 WP-5 手动修复（I4 Phase 5 planned）收敛 live defect，门禁是长期治理工具 |
| **截断可观测性门禁（INV-04 子项）** | error-handling 截断子集 + WP-6 | 6+ 处 setLimit(MAX_QUERY_RESULTS) 后无 size 检查/WARN | Cycle 2 / I1：`.mjs` 扫描（setLimit 后须跟 size==limit 检查或 WARN） | 同上；Cycle 1 通过 WP-6（I4 Phase 6 planned）手动修复 |
| **error-handling 专项门禁** | error-handling 子串/吞原子集 + WP-9 | 子串误匹配(contains/startsWith)与 catch 吞异常无门禁 | Cycle 2 / I1：`.mjs` 扫描（可疑 contains/startsWith + 空 catch 块） | 同上；Cycle 1 通过 WP-9（I4 Phase 8 planned）手动修复 |
| **@Auth 系统性门禁** | auth-security 3 + WP-10 | @Auth 缺失/契约漂移无门禁 | Cycle 2 / I1：ORM/API 交叉检查（BizModel action 与 action-auth.xml 权限声明匹配） | 同上；Cycle 1 通过 WP-10（I4 Phase 9 planned，ask-first）手动修复 |

### 5.2 §B P2/P3 后继修复 successor path（27 条，以 §E.2 权威总账为准）

> **26/27 差异对账**：I3 §E.2 标注 27 条；§E.3 族核对合计 26，差 1 源自 AR-45 聚合余量折算（incremental-desync 族在 §1 aggregate=3 时 AR-45 归入聚合余量未单计，§E.2 逐行列 4）。**以 §E.2 权威总账 27 为准**，逐族对账如下：

| 族 | 条数（§E.2） | Terminal | Successor Path | Why Not Blocking Cycle 1 Closure |
|----|-------------|----------|----------------|----------------------------------|
| graph-algorithm | 12 | P2 后继 | Cycle 2 / 图算法专项后继计划 | 辅助分析特性（社区检测/介数/凝聚），非核心索引/查询正确性；触发需主动调用图分析 API，低频；无门禁覆盖 |
| language-adapter | 10 | P2 后继 | Cycle 2 / 语言适配器专项后继计划 | 非 Java 主语言解析边缘构造（Python/TS QN/import/嵌套/相对导入）精度问题；核心 Java 符号索引工作；非核心正确性硬失败 |
| config-contract | 2 | P2 后继 | 先确认 native-image 支持范围 → Cycle 2 / 配置契约后继 | GraalVM reflect-config 缺口；若 native-image 不在 supported baseline，不影响 JVM 模式运行 |
| incremental-desync（AR-45） | 1 | P3 后继 | Cycle 2 / 专注后继计划 | loadFingerprints 双重 pathMapper 窄面数据语义缺陷；触发需特定 pathMapper 配置；非核心索引/查询正确性 |
| concurrency（AR-182） | 2（1 条单列 + 聚合余量折算） | P2 后继 | Cycle 2 / 状态持久化后继计划 | incrementalStatusMap LRU 无持久化，重启触发全量重索引（正确但慢的降级），无数据损坏 |
| **合计** | **27** | — | — | 逐族附 Why Not Blocking + Successor Path，无静默丢弃 |

> **对账结论**：graph-algorithm 12 + language-adapter 10 + config-contract 2 + AR-45 ×1 + AR-182 ×1 + 聚合余量折算 ×1 = **27**（§E.2 权威）。§E.3 族核对 26 的差异（AR-45 折算口径）已在 incremental-desync 行显式说明，两账在终态语义上一致（零悬挂）。**无遗漏、无新增降级**。

### 5.3 接受为残余风险（28 条，watch-only / optimization candidate，非后继修复）

> 这些条目已裁定为非 live-defect 的优化项，附 Why Not Blocking，**不派生 successor plan**（watch-only）：

- performance 4（N+1 / O(N²)，optimization candidate）
- dead-code 7（死字段/死权重，无行为影响）
- error-handling 硬编码子集 H（当前值工作正确，仅不可配置）
- orm-schema 聚合 2（完整性硬化，当前数据未违反）
- AR-75（resolveQualifiedNamesToIds 全实体必要，mutation 需全字段）
- AR-157（evictOverflow 无序驱逐，影响缓存命中率非正确性）

---

## §6. 结论

1. **Cycle 1 修复统计（I4 已执行范围）**：3 个工作包（WP-1/WP-2/WP-3）落地，38 条 red-list 真违规中 33 条已收敛；门禁棘轮前进（query-limit 33→0 / entity-field-min 24→12 / idempotency 2→0 / delete-contract 0→0），四族 real-violation 归零（I5 record 权威）。
2. **稳态判定**：**Cycle 1 未达稳态——维持 `active`，确定性稳态判定 DEFERRED**。「零新族」成立（全部缺陷落在 INV-01..05 + §D 候选内），但 I4 Phase 3-9（7 WP）未完成，Dependency Graph 的前置条件（I4 fully closed）未满足。强行宣布稳态会静默遗漏已知未修缺陷（违反 Anti-Slacking）。
3. **复触发条件**：已登记 ≥3 条（CI 门禁变红 / 审计目标集结构变更 / 周期复探）+ T0 首要条件（I4 Phase 3-9 立即恢复执行）。
4. **Cycle 2 后继派生**：§D 4 候选门禁 + §B 27 条 P2/P3 后继修复均已登记 successor path + Why Not Blocking（以 §E.2 总账为准，26/27 差异已逐族对账消除）。
5. **I4 Phase 3-9 是 Cycle 1 达稳态的唯一阻塞项**——完成后触发 I5 re-verification 全绿，再由 I6-revisit 做确定性稳态判定。

> **本报告为过渡性循环收口（interim closure）**：I6 的交付物（统计 / 稳态判定程序 / 复触发条件 / 后继派生）已完成，但 Cycle 1 本身未关闭。下一行动 = 恢复 I4 Phase 3-9 执行。
