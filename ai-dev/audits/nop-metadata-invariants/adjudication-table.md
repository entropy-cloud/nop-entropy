# nop-metadata 不变式审计 — 裁决表（Adjudication Table）

> 产出方：plan `2026-08-13-1930-3`（Cycle 1 / I3 — 发现裁决与工作项拟制）
> 实测日期：2026-08-13
> 裁决输入：`formal-red-list.md`（81 项命中）+ `adversarial-probing-notes.md`（对抗探查，0 新增）
> 下游消费者：I4（P0/P1 修复执行）、I5（零命中验证）、Cycle 2/I1（新族派发，本轮无）
> 方法论：`ai-dev/skills/invariant-loop-audit-prompt.md`（类别清扫强制 + Loop Rule 预授权派生）；裁决纪律见 plan `Anti-Slacking Rule` + `Non-Degradable Items`

## 裁决纪律（本案适用）

- **已确认 live defect / contract drift 一律 P0/P1，不得降级 deferred/follow-up**（Anti-Slacking + Non-Degradable Items）。
- **裁决不得用"暂不处理/待定"** —— 每条命中必须落到 `P0` / `P1` / `新族→Cycle2` / `deferred(non-blocking 理由)` 之一（plan Phase 3 Exit Criteria）。
- **门禁命中 ≠ 自动 defect**：INV-SILENT-SWALLOW 的不变式陈述（clause b）允许"构造带 ErrorCode 的诊断信息 + 有意裁定的 per-edge 隔离"。故 80 命中里，哪些是真缺陷（应 rethrow/补 ErrorCode）、哪些是合规的有意隔离（应 formalize 为 ErrorCode-bearing 诊断 + per-edge accepted 注释），由 **I4 逐实例裁定**。本裁决表的职责是：**把整族派给 I4 并附类别清扫指令**，不越权预判每条。
- **本计划不改代码 / 不改不变式**（Non-Goals）—— 裁决只产出归属与 I4 工作项描述。

---

## §1 裁决汇总（零悬挂自检）

| 族（不变式） | red list 命中 | 裁决 | 归属 | 悬挂数 |
|--------------|---------------|------|------|--------|
| silent-swallow（INV-SILENT-SWALLOW） | 80 | **P1** | I4（类别清扫） | 0 |
| unique-key（INV-UK） | 0 | 无命中（防回退门禁绿） | — | 0 |
| sensitive-literal（INV-SENSITIVE） | 0 | 无命中（防回退门禁绿） | — | 0 |
| limit 负值（INV-LIMIT） | 1（L1 queryTableData） | **P1**（契约冲突，需人工确认） | I4 + 人工确认 | 0 |
| 新族（对抗探查） | 0 | 无新族 | — | 0 |
| **合计** | **81** | — | — | **0（零悬挂）** |

> **零悬挂自检**：每条 red-list 条目（80 silent-swallow + 1 limit = 81）均有非"待定"归属。UK/SENSITIVE 零命中无裁决条目。对抗探查 0 新族、不触发 Cycle 2/I1。**裁决表零悬挂达成。**

---

## §2 族级裁决与 I4 工作项描述

### 族 A — silent-swallow（INV-SILENT-SWALLOW，80 命中）

**裁决**：**P1 → 派 I4（类别清扫）**。

**裁决理由（可核）**：
1. 门禁确定性命中 80 处"catch 既不 rethrow 也不在花括号跨度内出现 ErrorCode 传播信号"，分布横跨 26 个文件 / 5 个语义子类（S1 类型探测回退 / S2 批量失败隔离 / S3 优雅降级 / S4 良性 best-effort / S5 通用 catch+LOG），**符合"修实例不修类别"的复发温床特征**（先例链 P2-06/07/09 + P2-01/02/04 + AR-21 已 7 次同族复发）。
2. INV-SILENT-SWALLOW 不变式（clause b）允许"有意裁定的 per-edge 隔离"，但**前提是 catch 内构造带 ErrorCode 的诊断信息**。80 命中均未在跨度内出现 ErrorCode token —— 意味着即便部分是"有意隔离"，其诊断信息也未达到"ErrorCode 可观测到边界"的不变式要求。**故整族为 in-scope 待收敛项，不得 deferred**（Non-Degradable：已确认的 invariant 失败项）。
3. 不升级 P0：80 命中均非急性数据丢失 / 安全漏洞 / 数据完整性破坏（安全族已在 R6.2/R8.2 修；UK 族 0 命中；batch 失败隔离 S2 已把错误捕获进结构化 errors/errorCount/result 行）。属**可诊断性 / 契约合规性**问题，P1 优先级正确。

**I4 工作项描述（实例清单 + 类别清扫 + test-first + 验收）**：

- **实例清单**：`formal-red-list.md` §1 全 80 条（含 `文件:行` + 语义子类标注）。按子类分批处理可降低裁定成本：
  - **S1 类型探测回退**（≈17，如 `MetaTableProfiler`、`MetaQualityRuleExecutor` 的 `catch (SQLException ignore)`）：裁定"用 `ResultSetMetaData` 预判列类型替代异常流控制" vs "保留回退但补 ErrorCode 诊断"。
  - **S2 批量失败隔离**（≈21，如 `MetaQualityCheckpointExecutor`、`MetaQualityCheckpointScheduler`）：确保 `errors`/`errorCount`/result 行捕获的内容含 ErrorCode 上下文（如 `buildExecutionErrorEntry` 内映射到 `NopMetadataErrors.*`）。
  - **S3 优雅降级**（≈14，如 `NopMetaIndexBuilder` 11 处、`NopMetaLineageEdgeQueryAction`）：确保降级路径把 ErrorCode 传播到返回结果（如 `IndexResult.failed`/`errors` 列表含 ErrorCode）。
  - **S4 良性 best-effort**（≈8，如 `MetaDataSourceConnectionProcessor:99` SecurityException、`HostSecurityUtil` NumberFormatException）：逐条裁定——若确属"可选调优 / 环境拒绝"且无业务影响，formalize 为 ErrorCode-bearing 诊断 + `// per-edge accepted: <reason>` 注释（使门禁识别为合规 clause-b）；否则补 ErrorCode。
  - **S5 通用 catch+LOG**（≈20，如 `NopMetaDataSourceBizModel`、`NopMetaModuleBizModel`）：最可能含真缺陷，逐条核查"是否应 rethrow 或补 ErrorCode"。

- **类别清扫强制（具体可执行 grep）** —— 修任一实例必 grep 全类兄弟，**门禁本身即权威清扫器**：
  ```bash
  # 权威类别清扫扫描（返回全部命中 + 任意新增兄弟，退出码 1 = 有命中）
  node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata

  # 全 catch 面（130 块的原始分布，用于核查"是否漏扫某文件"）
  rg -n "catch\s*\(" nop-metadata/nop-metadata-service/src/main/java | sort

  # 类型探测回退子模式（ignore/ignored 命名 catch）—— S1 子类的快速定位
  rg -n "catch\s*\(\s*\w+\s+(ignore|ignored)\b" nop-metadata/nop-metadata-service/src/main/java

  # bizmodel 层 sweep（@BizModel 类内的 catch，对应 S5 子类高发区）
  for f in $(rg -l '@BizModel' nop-metadata/nop-metadata-service/src/main/java); do
    echo "=== $f ==="; rg -n "catch\s*\(" "$f"
  done
  ```
  > **"门禁即权威清扫器"原则**：I4 修复后重跑 `check-silent-swallow.mjs`，命中数应**单调递减至 0**（或对合规 clause-b 实例 formalize 后被门禁识别为放行）。不允许"修了报到的实例、漏了同类兄弟"（这正是历史先例链的病根）。

- **test-first 要求**：
  - 每个被改为 rethrow/补 ErrorCode 的 catch：新增 focused test 触发该 catch 路径并断言抛出指定 `ErrorCode`（非空壳断言）。
  - 每个 formalize 为 clause-b 的 catch：新增 test 验证"降级返回值携带 ErrorCode 上下文"（如 `IndexResult.failed(...).errorCode == ...`）或"ErrorCode 出现在 catch 花括号跨度内"（使门禁放行）。
  - **禁止 `@Disabled` 跳过**；未收敛项以门禁 FAIL 暴露。

- **门禁复跑零命中验收点**：
  ```bash
  node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata   # 收口期望退出码 0（零命中）
  ```
  若 I4 决定保留某些 clause-b 合规实例，门禁需同步升级为"识别 ErrorCode token 即放行"（当前规则已含 `ErrorCode.` / `.errorCode(` / `NopMetadataException(` 等信号），formalize 后自然放行 —— 无需弱化门禁。

---

### 族 B — unique-key constraint（INV-UK，0 命中）

**裁决**：**无命中**（防回退门禁绿）。live 实测 37/37 unique-key 全带 `constraint=` + `columns=`，DDL 三方言已物化 UNIQUE（Lesson 09 已由 R3.19 修复）。

**I4 工作项**：无（本族 red list 分量 = 0）。门禁继续作为防回退守护：若 I4 期间 ORM 模型新增 unique-key，`check-orm-unique-key-constraint.mjs` 自动覆盖（无需额外动作）。

---

### 族 C — sensitive-literal（INV-SENSITIVE，0 命中）

**裁决**：**无命中**（防回退门禁绿）。历史命中点（`ARG_RAW_JDBC_URL`、SQL 字面量、Map 分支敏感列）已由 R6.2/R8.2/R8.4b 修复为脱敏形式。

**I4 工作项**：无（本族 red list 分量 = 0）。门禁继续作为防回退守护。

---

### 族 D — limit 负值（INV-LIMIT，1 命中 L1）

**裁决**：**P1 → 派 I4 + 人工确认**（契约冲突点，需 ask-first）。

**裁决理由（可核）**：
1. `NopMetaTableBizModel#queryTableData` 对 `limit = -1` 执行 `normalizeQueryLimit` → null/≤0 取缺省值（**静默封顶**），门禁 FAIL。
2. **契约冲突**：INV-LIMIT 不变式（I0 定稿）陈述"负值必须显式失败"；但 MA7.4-03 此前裁定此方法为"静默封顶语义"（intentional）。两者直接冲突。
3. **不能 deferred**：这是已确认的 invariant 失败项（Non-Degradable），且是 public 入口方法的契约漂移。但**也不能 AI 单方面改**——两条解决路径都涉及行为/契约变更，触发 AGENTS.md 的 ask-first（public API 行为变更）。
4. 不升级 P0：`limit = -1` 静默封顶为缺省值（非负值直通下游 SQL），不导致"负值绑给 DB 的不可诊断错误"（INV-LIMIT 禁止的真正危害形式）。属**契约一致性**问题，P1 正确。

**I4 工作项描述（契约冲突解决 + test-first + 验收）**：

- **解决路径（二选一，均需人工确认）**：
  - **路径 ①（尊不变式）**：改 `queryTableData` 的 `normalizeQueryLimit`，使 `limit < 0` 抛 `ERR_PAGINATION_LIMIT_INVALID`（与 `queryJoinData`/`queryAggregation` 的 `normalizeJoinQueryLimit` 对齐，沿 AR-09 先例）。
    - 影响：public `@BizQuery` 行为变更（原本 `limit=-1` 返回缺省行数，改为抛异常）—— **ask-first**，需核查是否有调用方依赖 `-1` 语义。
  - **路径 ②（尊 MA7.4-03 + 弱化不变式）**：保留 `queryTableData` 静默封顶，**正式修订 INV-LIMIT 陈述**：增加"例外：`queryTableData` 的 `limit ≤ 0` 取缺省值为 MA7.4-03 裁定的有意静默封顶"。
    - 影响：**棘轮弱化**（关键机制 #2）—— 需人工确认 + 留痕 + 同步更新 `invariant-catalog.md` + 在 `TestLimitNegativeValueInvariant` 方法表中为该方法标注预期非抛、并在 `TestLimitTargetSetCompleteness` 反映该例外。
  - **AI 不得擅自选路径**：I4 启动时由人工裁定路径 ① vs ②，再执行。

- **类别清扫强制（具体可执行 grep）** —— 修此入口必核对全部 limit-taking 入口 + 同义参数：
  ```bash
  # 全部 limit-taking public 入口（应 = 4，I0 §1.4）
  rg -n '@Name\("limit"\)' nop-metadata/nop-metadata-service/src/main/java

  # limit 同义参数对抗 sweep（应 = 0，证明无漏网同义入参）
  rg -n '@Name\("(pageSize|size|maxResults|page_size|maxRows|top)"\)' nop-metadata/nop-metadata-service/src/main/java

  # normalize*Limit helper 全集（limit 归一化逻辑集中点）
  rg -n "normalize\w*Limit" nop-metadata/nop-metadata-service/src/main/java
  ```

- **test-first 要求**：
  - 路径 ①：`TestLimitNegativeValueInvariant` 对 `queryTableData` 的 FAIL 转 PASS（断言抛 `ERR_PAGINATION_LIMIT_INVALID`）；新增正向 test 覆盖 `limit=0`/`null` 仍取缺省值（保留合法封顶语义）。
  - 路径 ②：`TestLimitNegativeValueInvariant` 方法表为 `queryTableData` 标注预期非抛（静默封顶），`TestLimitTargetSetCompleteness` 仍 PASS（表 = 4）；`invariant-catalog.md` INV-LIMIT 陈述更新，含 MA7.4-03 例外引用。

- **门禁复跑零命中验收点**：
  ```bash
  ./mvnw test -pl nop-metadata/nop-metadata-service -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false
  # 收口期望：4 tests, 0 failures（路径 ①），或 4 tests 0 failures 且方法表标注例外（路径 ②）
  ```

---

## §3 新族登记（对抗探查发现 → Cycle 2 / I1 派发）

**本轮对抗探查（`adversarial-probing-notes.md`）发现 0 个新族**，不触发 Loop Rule 预授权派发。逐方向结论：

| 对抗方向 | 是否新族 | 处置 |
|----------|----------|------|
| 方向 1（I0 目标集漏扫） | 否 | 全部计数口径与 I0 一致，无漏扫 |
| 方向 2（门禁子模式漏网） | 否 | 4 族子模式覆盖经核查无漏网（INV-SENSITIVE 多行构造为已知精度边界，非新族） |
| 方向 3（ORM index/DDL 漂移） | 否 | 平台级 by-design（nop-orm 生成器），优化类，非 nop-metadata 正确性缺陷；超 mission 授权范围 |
| 方向 4（跨模块 catch） | 否 | catch 全集中在 service（130/130），无跨子模块盲区 |
| 方向 5（目标集稳定性） | 否 | I1→I2 零结构变更 |

> **不派发 Cycle 2/I1**（Loop Rule 预授权派发的触发条件 = 发现 I0 目录外新失败族；本轮未发现）。方向 3 的 ORM index 观察留作平台级架构备注（非本闭环范围），若未来 nop-orm 决定保证索引部署，由独立 mission 处理。

---

## §4 端到端链条完整性自检（Anti-Hollow）

> 从"门禁命中"→"裁决归属"→"I4 工作项可执行描述"链条完整性核查：

| 链条环节 | 完整性证据 |
|----------|------------|
| 门禁命中 → red list | `formal-red-list.md` 81 项，每项含 `文件:行` + 不变式编号 + 可复跑命令 |
| red list → 裁决归属 | 本表 §1/§2，81 项均有非"待定"归属（80→P1/I4，1→P1/I4+人工确认） |
| 裁决 → I4 可执行描述 | 本表 §2 族 A/D，每族含实例清单 + 具体可执行 grep + test-first + 门禁零命中验收 |
| 对抗盲区 → 新族派发 | 本表 §3，5 方向均结论无新族（不派发 Cycle 2/I1，留痕完整） |
| 已知 live defect 降级检查 | §1 零 deferred；§2 族 A/D 均 P1（非 deferred）；UK/SENSITIVE 零命中无降级空间 |

**链条无断点**。裁决表满足 plan Phase 3 Exit Criteria "端到端验证"。

---

## §5 deferred 项（本案）

**本裁决表无 deferred 项**。所有 81 命中均归 P1/I4（含 1 项需人工确认的契约冲突），UK/SENSITIVE 零命中。对抗探查 0 新族。无任何条目以"暂不处理/optimize-later/if-time"等模糊词挂起（Anti-Slacking 合规）。

> 唯一"非阻塞观察"= 对抗方向 3 的 ORM index 平台级 by-design 观察，已明确裁定为**非缺陷、非本轮新族、超 mission 范围**，记录在 `adversarial-probing-notes.md` 供未来平台级决策参考。它不是 deferred 工作项（本闭环不拥有它）。

---

## 引用

- `formal-red-list.md`（裁决输入：81 项命中）
- `adversarial-probing-notes.md`（对抗探查：5 方向 0 新族，证明无遗漏）
- `invariant-catalog.md`（INV-SILENT-SWALLOW clause b / INV-LIMIT 陈述，裁决依据）
- `initial-red-list.md`（I1 快照，棘轮零点）
- `ai-dev/audits/arm-index-nop-metadata.md`（MA7.4-03 / AR-09 / AR-23④ 等先例链可定位）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（类别清扫强制 + Loop Rule 预授权派生）
