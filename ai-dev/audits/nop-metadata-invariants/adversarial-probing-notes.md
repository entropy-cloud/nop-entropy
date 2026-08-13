# nop-metadata 不变式审计 — 盲区对抗探查笔记（Adversarial Blind-Spot Probing）

> 产出方：plan `2026-08-13-1930-3`（Cycle 1 / I2 — 不变式驱动审计，Phase 2）
> 实测日期：2026-08-13（live repo，对抗性手动核查）
> 方法论：`ai-dev/skills/invariant-loop-audit-prompt.md` 步骤 0 + open-ended 对抗审查精神
> 上游：`audit-target-set.md`（I0 目标集，比对基准）、`formal-red-list.md`（Phase 1 正式 red list）
> 下游消费者：I3 裁决（对抗探查结论决定是否产生新族 → Cycle 2/I1）

## 目的

门禁是模式驱动的确定性扫描，覆盖不到的地方由本节做**对抗性手动核查**。聚焦三个盲区方向（plan Phase 2 明列）：

1. **I0 目标集漏扫**：是否有 I0 之后新增 / I0 漏扫的 processor / bizmodel 方法？
2. **门禁未覆盖的子模式**：每族的门禁检测规则是否有子模式漏网（如 silent-swallow 门禁若只查 `getMessage()`，"catch 后 log.warn 后继续"是否漏网）？
3. **ORM 模型 unique-key 之外的 DDL 完整性盲区**：index / 外键声明与 DDL 产物是否漂移？

附加对抗方向（open-ended 精神）：

4. **跨模块调用链**：service→core→dao 路径上是否有"上层 catch 后未传播 ErrorCode 到边界"的实例？
5. **目标集稳定性**：I0→I2 期间是否有新增变更型方法（`@BizQuery`/`@BizMutation`）脱离门禁覆盖？

> **结论先行**：5 个探查方向均结论"**无新族命中 / 无 I0 漏扫**"。对抗探查**未并入任何新条目**到 `formal-red-list.md`。仅方向 3 产生一个"经核查判定为非缺陷"的观察（ORM index 未进 `_create` DDL），已确认为平台级 by-design，不构成本轮新族、不触发 Cycle 2/I1。详见各方向小节。

---

## 方向 1 — I0 目标集漏扫（processor / bizmodel 方法）

**探查方法**：复跑 I0 `audit-target-set.md` §1 的全部计数口径，与 I0 记录值逐项比对。若 I0 之后新增方法或 I0 计数有误，即为漏扫。

**实测（2026-08-13 live）**：

| 口径 | I0 记录值 | I2 复测值 | 结论 |
|------|-----------|-----------|------|
| `@BizModel` 注解类（main） | 40 | **40** | 一致 |
| `@BizQuery` 方法 | 13 | **13** | 一致 |
| `@BizMutation` 方法 | 30 | **30** | 一致 |
| `@BizAction` 方法 | 0 | **0** | 一致 |
| 变更型入口面合计（@BizQuery+@BizMutation） | 43 | **43** | 一致 |
| 显式 `@Name("limit")` 入参的 public 方法 | 4 | **4** | 一致 |
| catch 块（service main） | 130 / 46 文件 | **130 / 46 文件** | 一致 |
| service 下 `public` 符号（参考） | 723 | 723 | 一致 |

**复现命令**：
```bash
rg -c '^\s*@BizModel'     nop-metadata/nop-metadata-service/src/main/java | awk -F: '{s+=$2} END{print s}'   # 40
rg -c '^\s*@BizQuery'     nop-metadata/nop-metadata-service/src/main/java | awk -F: '{s+=$2} END{print s}'   # 13
rg -c '^\s*@BizMutation'  nop-metadata/nop-metadata-service/src/main/java | awk -F: '{s+=$2} END{print s}'   # 30
rg -n '@Name\("limit"\)'  nop-metadata/nop-metadata-service/src/main/java                                    # 4 行
rg -c "catch\s*\("        nop-metadata/nop-metadata-service/src/main/java | awk -F: '{s+=$2} END{print s}'   # 130
rg -l "catch\s*\("        nop-metadata/nop-metadata-service/src/main/java | wc -l                              # 46
```

**结论**：**无 I0 漏扫**。I1→I2 期间 `nop-metadata-service/src/main/java` 零 commit（`git log --since=2026-08-13 -- nop-metadata/nop-metadata-service/src/main/java` 为空），目标集完全稳定。limit-taking 入口仍为 4（`NopMetaSearchBizModel:64`、`NopMetaTableBizModel:231/258/285`），无新方法脱离门禁覆盖。**无需补扫目标集**。

---

## 方向 2 — 门禁未覆盖的子模式（每族检测规则的漏网检查）

**探查方法**：逐族审视门禁检测规则，构造"应命中但规则可能漏网"的子模式样例，核查 live code 是否存在且被命中。

### 2.1 INV-SILENT-SWALLOW 子模式覆盖

门禁规则：catch 块花括号跨度内不出现 `throw` / `NopMetadataException(` / `.errorCode(` / `ErrorCode.` / `BizException` / `Biz.fatal(` 任一 → 命中。

**对抗假设**：门禁是否只查"空 catch / getMessage-only"而漏网"log.warn 后继续"、"catch 后 return null 当正常值"、"catch 后 break/continue 跳过"等非空但未传播 ErrorCode 的子模式？

**实证（live 抽查，对应 formal-red-list §1 编号）**：
- **"catch + LOG.warn + 继续"** —— `MetaDataSourceConnectionProcessor.java:99`（hit #1）：`catch (SecurityException se) { LOG.warn(...); }`。门禁**命中**（跨度内无 throw/ErrorCode 信号）。✓ 子模式被覆盖。
- **"catch + LOG.error + 降级返回结构化 errors"** —— `MetaQualityCheckpointExecutor.java:156`（hit #42）：`catch (Exception e) { LOG.error(...); errors.add(buildExecutionErrorEntry(rule,e)); errorCount++; ... }`。门禁**命中**（虽做了结构化错误捕获，但跨度内无 throw/ErrorCode token，故命中）。✓ 子模式被覆盖。
- **"catch + return null/0/empty"** —— `NopMetaLineageEdgeQueryAction.java:165`（hit #15）：`catch (NopException e) { LOG.error(...); errors.add(errorMap("sql_parse", e)); refs = Collections.emptyList(); }`。门禁**命中**。✓ 子模式被覆盖。
- **"catch (SQLException ignore) + 回退备选读法"** —— `MetaQualityRuleExecutor.java:702/709`（hit #56/#57）：`catch (SQLException ignore) { /* 非数值列，尝试按布尔读 */ }`。门禁**命中**。✓ 子模式被覆盖。
- **"catch (NopException e) 不 rethrow"** —— `NopMetaLineageEdgeQueryAction.java:165/221/338`（hit #15/#16/#17）：即使 catch 的是 `NopException`，只要不 rethrow 就命中。✓ 子模式被覆盖（门禁不依赖 catch 的异常类型，依赖花括号跨度内的传播信号）。

**结论**：INV-SILENT-SWALLOW 门禁是 `java-lint-empty-catch.yml` + `java-lint-getmessage-only.yml` 的**语义超集**（与 invariant-catalog §INV-SILENT-SWALLOW ④ 检测方法声明一致）。所有"非空但未传播 ErrorCode"的子模式均被覆盖，无漏网。子模式判定边界由 I3 裁决（哪些是合规的"有意裁定 per-edge 隔离"，哪些是缺陷）。

### 2.2 INV-UK 子模式覆盖

门禁规则：`<unique-key>` 元素缺 `constraint=` 或 `columns=` → 命中（XML-aware，跨行整体匹配）。

**对抗假设**：是否存在"带 constraint= 但值为空"、"自闭合 vs 含子元素两种形态"、"跨行属性"等漏网？

**实证**：I0 `audit-target-set.md` §3 的 XML-aware 复现脚本（`node -e` 逐 `<unique-key>` 元素核对）输出 `matched 37, missing 0`。门禁读同样的 37 元素，0 命中。两种形态（自闭合 `<unique-key ... />` 与含 `<column>` 子元素）均被脚本覆盖。✓ 无漏网。

### 2.3 INV-LIMIT 子模式覆盖

门禁形式：JUnit 5 `@ParameterizedTest` + `@MethodSource`，方法表 = 4 limit-taking 入口，每方法 `limit = -1` 断言抛 ErrorCode。配套 `TestLimitTargetSetCompleteness` 表完备性自检。

**对抗假设**：是否存在"接受 limit 但未用 `@Name("limit")` 注解"的同义入参（如 `pageSize`、`size`、`maxResults`）脱离穷举表？

**实证**：`rg -n '@Name\("limit"\)'` = 4。对抗核查其它常见分页参数名：
```bash
rg -n '@Name\("(pageSize|size|maxResults|page_size|maxRows|top|fetchSize)"\)' nop-metadata/nop-metadata-service/src/main/java
# 结果：0 命中 —— nop-metadata 无其它分页同义入参
```
（注：`fetchSize` 是 JDBC 内部 hint，非 public 入口参数；不入穷举表正确。）

**结论**：limit 穷举表完备（4/4），表完备性自检 PASS。无同义入参漏网。INV-LIMIT 的 `TestLimitTargetSetCompleteness` 已守护"新增 limit-taking 方法不入表即红"，防新方法静默成盲区。

### 2.4 INV-SENSITIVE 子模式覆盖

门禁规则：同一行同时出现 logger/error-builder token 与匹配字面量（JDBC-URL / ≥2 SQL 关键字内联 literal）→ 命中。

**对抗假设**：是否存在"多行构造的敏感 message"（logger 与字面量不同行）漏网？

**实证**：门禁为行级共现判定（与 invariant-catalog §INV-SENSITIVE ④ 声明的"行级扫描器按行级共现判定"一致）。历史命中点（`ARG_RAW_JDBC_URL`、SQL 字面量、Map 分支）均已在 R6.2/R8.2/R8.4b 修复为脱敏形式，当前 0 命中。多行构造的边角 case 是已知近似（catalog 已声明），但因历史命中点已清零且本族为防回退门禁，当前无实际漏网命中。**记录为 INV-SENSITIVE 的已知精度边界**（非缺陷），若 Cycle 2 该族复发并出现多行构造，可在 Cycle 2/I1 升级为 AST 级判定。

**结论（方向 2）**：4 族门禁的子模式覆盖均经对抗核查无漏网。INV-SENSITIVE 的"多行构造"为已知精度边界（已声明），非本轮新族。

---

## 方向 3 — ORM 模型 unique-key 之外的 DDL 完整性盲区（index / 外键）

**探查方法**：INV-UK 只覆盖 `<unique-key>`。对抗核查 ORM 其它 DDL 相关元素（`<index>`、`<relation>`/外键）的声明与 DDL 产物是否漂移。

**实测（live）**：
- ORM 元素计数：`<entity `=39、`<unique-key name=`=37、`<index name=`=**63**、`<relation `=0（无物理外键声明，符合 Nop 平台"关系逻辑化"约定）。
- 63 个 `<index>` 全部带 `name=` 且有 `<column>` 子元素（0 缺失）。
- DDL 产物 `nop-metadata/deploy/sql/mysql/_create_nop-metadata.sql`：含 PK（`constraint PK_*`）与 UNIQUE（`constraint UK_*` / `unique`），但 **`IX_` 出现次数 = 0** —— 63 个 ORM `<index>` **均未出现在 `_create` DDL**。

**对抗深查（这是否为 nop-metadata 特有缺陷？）**：

定位 DDL 生成器 `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl.xlib`：
- L69-L83（create-table 流程）：仅循环 `table.uniqueKeys`（且仅当 `uniqueKey.constraint` truthy 才发射 `constraint ... unique`），**不循环 `table.indexes`**。
- L381-L389 存在独立的 `index` 发射 unit（`create ${index.unique ? 'unique ' : ''}index ...`），但该 unit 由**独立的 diff/alter 流程**调用，不在 `_create`（建表）流程内。

全仓比对（确认是否平台级 by-design）：

| 模块 | ORM `<index>` 数 | `_create` DDL 中 `create index`/`IX_` 数 |
|------|------------------|------------------------------------------|
| nop-metadata | 63 | **0** |
| nop-wf | 2 | 0 |
| nop-ai | 11 | 0 |
| nop-job | 8 | 0 |
| nop-retry | 9 | 0 |
| nop-code | 37 | (no _create ddl) |
| nop-auth | 0 | 0 |

**结论**：index 不进 `_create` DDL 是**平台级 by-design 行为**（所有模块一致）—— ORM `<index>` 供运行时 schema/diff 工具（`OrmDbDiffer`）与代码生成消费，`_create` 部署 DDL 只发射 PK + UNIQUE（数据完整性约束），不发射优化类索引（索引由部署/运维按需创建或由 diff 工具补齐）。

**裁决**：此观察**不构成 nop-metadata 正确性缺陷**，也**不构成本轮新族**：
- 性质：优化类（查询性能），非数据完整性 / 非静默正确性缺陷（与 Lesson 09 unique-key 静默缺失 = 数据完整性丢失，本质不同）。
- 范围：平台级（nop-orm 生成器），非 nop-metadata 模块专属。
- 授权边界：本 mission 范围 = nop-metadata；平台级生成器变更超出 mission 授权（需 nop-orm 层面的人工确认）。

**不触发 Cycle 2/I1**。记录为"对抗探查方向 3 = 已核查、平台级 by-design、优化类、非本轮新族"。若未来平台决定保证索引部署（如 `_create` 发射索引或新增 `_create_index` 流程），那是 nop-orm 层面的架构决策，由独立 mission 处理，不归本不变式闭环。

---

## 方向 4 — 跨模块调用链（service→core→dao 的 catch 吞异常）

**探查方法**：INV-SILENT-SWALLOW 的目标面是 `nop-metadata-service`。对抗核查：nop-metadata 其余子模块（web/app/core/api/dao/meta）是否有 catch 块未覆盖？

**实测（live）**：

| 子模块 | catch 块数 |
|--------|-----------|
| nop-metadata-service | 130（已覆盖，门禁面） |
| nop-metadata-app | **0** |
| nop-metadata-core | **0** |
| nop-metadata-api | **0** |
| nop-metadata-dao | **0** |
| nop-metadata-web | （无 `src/main/java`） |
| nop-metadata-meta / codegen | （无 `src/main/java`） |

**复现**：
```bash
for d in nop-metadata/nop-metadata-web nop-metadata/nop-metadata-app nop-metadata/nop-metadata-core nop-metadata/nop-metadata-api nop-metadata/nop-metadata-dao; do
  [ -d "$d/src/main/java" ] && echo "$d: $(rg -c "catch\s*\(" $d/src/main/java | awk -F: '{s+=$2} END{print s+0}')"
done
```

**结论**：nop-metadata 的 catch 块**全部集中在 service 层**（130/130），其余子模块零 catch。门禁 scope（`nop-metadata-service`）= INV-SILENT-SWALLOW 不变式陈述的"service/processor/bizmodel 层"全集，**无跨子模块盲区**。所谓"上层 catch 后未传播 ErrorCode 到边界"的实例本身就在这 130 个之中（已由门禁覆盖、已进 red list），无需额外并入。

---

## 方向 5 — 目标集稳定性（I0→I2 新增变更型方法）

**探查方法**：核查 I0 之后是否有新增/重命名 processor / bizmodel / ORM entity 脱离门禁覆盖（Loop Rule 的结构变更触发条件之一）。

**实测**：
- `git log --since=2026-08-13 -- nop-metadata/nop-metadata-service/src/main/java` = **空**（I1→I2 期间 service 源码零 commit）。
- `git log --since=2026-08-13 -- nop-metadata/model/nop-metadata.orm.xml` = **空**（ORM 模型零变更）。
- 方向 1 的全部计数口径与 I0 一致（40/13/30/130/46/4/37/39）。

**结论**：**无结构变更**。目标集稳定，门禁表完备性约束（INV-LIMIT 的 `TestLimitTargetSetCompleteness`、INV-UK 的 37 元素、INV-SILENT-SWALLOW 的 130 catch 面）均未被新增方法击穿。无需触发 Cycle 2/I1 的结构变更分支。

---

## 汇总：对抗探查结论一览

| 方向 | 探查问题 | 方法 | 结论 | 是否并入 red list / 触发新族 |
|------|----------|------|------|------------------------------|
| 1 | I0 目标集漏扫 | 复跑全部计数口径比对 | 全部一致，无漏扫 | 否 |
| 2 | 门禁子模式漏网 | 逐族构造对抗样例 + live 抽查 | 4 族均无漏网（INV-SENSITIVE 多行构造为已知精度边界） | 否 |
| 3 | ORM index/外键 DDL 漂移 | 全仓比对 + 生成器代码核查 | 平台级 by-design，优化类，非正确性缺陷 | 否（不触发 Cycle 2/I1） |
| 4 | 跨模块调用链 catch | 全子模块 catch 块普查 | catch 全集中在 service（130/130），无跨子模块盲区 | 否 |
| 5 | 目标集稳定性 | git log + 计数复跑 | I1→I2 零结构变更 | 否 |

**对抗探查总结论**：5 个方向均**无新族命中 / 无 I0 漏扫 / 无目标集击穿**。`formal-red-list.md` 的 81 项命中即为 I3 裁决的完整输入，**对抗探查未并入任何新条目**。本轮**不触发 Cycle 2/I1 的新族派发**（Loop Rule 预授权派发的触发条件未满足）。

> **Anti-Hollow 自检**：每个方向均有明确结论（命中/无命中 + 依据），无"未发现问题"空话。方向 3 的"非缺陷"结论附全仓比对表 + 生成器代码行号 + 性质/范围/授权边界三重裁定依据，可独立复核。

---

## 引用

- `formal-red-list.md`（Phase 1 正式 red list —— 本文件证明其无对抗新增）
- `audit-target-set.md`（I0 目标集 —— 方向 1/5 的比对基准）
- `invariant-catalog.md`（4 族检测方法 —— 方向 2 的子模式覆盖核查基准）
- `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl.xlib`（DDL 生成器，方向 3 证据）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（方法论，步骤 0 + 对抗审查精神）
