# 不变式门禁（Invariant Guards）

> 受众：修改 nop-metadata（或后续扩展到其他模块）service/processor/bizmodel/ORM 模型层的开发者和 AI。

## 目的

Nop 平台用**可执行不变式门禁**取代"修实例不修类别"的反应式审计：每个反复复发的失败模式族沉淀为一条 CI 门禁，命中即红、非零即阻断。本文档列出 nop-metadata 已沉淀的门禁、运行方式、棘轮规则。

## 门禁清单（nop-metadata，5 条）

| 门禁 | 不变式 | 形式 | 扫描/穷举面 | 命中即红的语义 |
|------|--------|------|-------------|----------------|
| `check-silent-swallow.mjs` | INV-SILENT-SWALLOW | Node 静态扫描（注释剥离） | 125 catch 块 / 45 文件（2026-08-15 live） | catch 块未 rethrow、未构造带 ErrorCode 的诊断信息（空 catch / 仅 getMessage / 静默降级返回默认值且无日志） |
| `check-orm-unique-key-constraint.mjs` | INV-UK | XML-aware 静态扫描 | 37 `<unique-key>` / 39 entity | `<unique-key>` 缺 `constraint=` 或 `columns=`（DDL 静默跳过唯一约束） |
| `check-sensitive-literal-leak.mjs` | INV-SENSITIVE | Node 静态扫描 | `.param(...)` / `LOG.*(...)` 字面量入参 | error/log message 含 raw JDBC URL（`jdbc:` + `@`）/ 内嵌 SQL 原文 / 已知凭据哨兵 |
| `TestLimitNegativeValueInvariant` | INV-LIMIT | JUnit 5 `@ParameterizedTest` + `@MethodSource` | 4 limit-taking public 入口方法 | 接受 `limit` 入参的 public 方法未显式拒绝负值（`limit = -1` 必须抛带 ErrorCode 的异常） |
| `check-silent-wrong-result.mjs` | INV-LOCALE / INV-NARROW / INV-CONTAINS-CLASSIFY / INV-DELIM-KEY / INV-BIGDEC（Cycle 2，silent-wrong-result 族） | Node 静态扫描（1 扫描器 × 5 规则，注释剥离） | service src/main 全量（2026-08-15 live：40 locale / 0 narrow / 19 contains / 6 delim / 2 bigdec） | **模式 b（baseline 快照对账）**：任何 baseline 外新命中键、或同键命中数超过 baseline 计数即红。子族：默认 locale case-mapping（机器比较语义）；`(long|int|short)` 截断先于算术；String 子串匹配用作分类；分隔符拼接复合键；Number→BigDecimal 经 `doubleValue()` 丢精度（无 `longValue()` 整数路由） |

> 每条不变式的完整定义（陈述 / 覆盖失败族 / 历史 audit-finding-ID 证据 / 检测方法）记录在不变式目录（invariant-catalog，位于仓库审计目录）。silent-wrong-result 族的初始 red list 人读层与机器可读 baseline 快照同样位于仓库审计目录（nop-metadata-invariants 下的 initial-red-list-cycle2 与 baseline-cycle2/silent-wrong-result.json，非链接引用——以仓库实际路径为准）。

## 运行方式

### 一键聚合（推荐）

```bash
# 本地（用 ./mvnw）
./ai-dev/tools/run-nop-metadata-invariants.sh

# CI（用 mvn，由 invariant-gate job 注入 MVN=mvn）
MVN=mvn ./ai-dev/tools/run-nop-metadata-invariants.sh
```

聚合入口语义：`set -e`，任一门禁非零退出 ⇒ 整体非零退出。先决条件：门禁工具的 `node_modules` 已安装（在工具目录下 `pnpm install`）。

### 单条门禁

```bash
# 1-3 为 Node 扫描器（exit 0 = 零命中，exit 1 = 有命中）
node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata
node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata
node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata

# 4 为 JUnit（默认 surefire 即运行；显式 -Dtest= 仅作单条调试 / 聚合入口复用，0 failures = 零命中）
./mvnw test -pl nop-metadata/nop-metadata-service \
  -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false

# 5 为 silent-wrong-result 五规则扫描器：
# 无 baseline：命中即红（exit 1）；模式 b（CI/聚合入口形态）：对账通过即绿
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata \
  --baseline ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json

# 单子族调试 / 快照再生成 / 自验证 fixture
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --emit-baseline
node ai-dev/tools/check-silent-wrong-result.mjs --fixture
```

### 模式 b（baseline 快照对账）语义

silent-wrong-result 门禁初始 red list 非空（2026-08-15 快照 67 命中 / 61 键），采用**快照对账**接入而非立即阻断：

- **对账键** = 文件路径 + 子族标签 + 命中行文本归一化（去首尾空白）；**不用行号**（无关编辑导致的行号漂移不误伤对账）。
- **对账语义 = 计数版子集（⊆）**：对每个键，当前出现次数 ≤ baseline 记录次数 → 绿；任何键超计数或出现 baseline 外新键 → 红。命中数**少于** baseline 为绿（渐进修复期间 CI 保持绿，无需每笔修复同步收缩 baseline）。
- **baseline 收缩**：只能随裁决终态或修复落地而收缩（每笔收缩须可追溯到裁决表或修复 commit）；禁止无依据扩张（扩张 = 新命中被"合法化"，属棘轮倒退）。
- **放行注释**：行内 `// invariant-ok: <裁决引用>` 把该行命中移出违规集与对账输入（终态 = 放行注释的条目不驻留 baseline）；放行条目在扫描器输出中显式列入 Allowed 节，不产生静默盲区。
- **终态**：全部条目修复或裁定后，baseline 重写为"已批准豁免清单"（FP 驻留 + 优化候选维持）或清空；baseline 清空且无放行注释 → 该门禁升级为模式 a（零命中阻断式）。

### CI

`.github/workflows/maven.yml` 的 `invariant-gate` job（`needs: build`）在 `build` 成功后运行聚合入口。4 条 Node 扫描器依赖 `pnpm` + Node 20（工具目录的 pnpm-lock.yaml）；limit 测试依赖 JDK 21。**非零即红**——不得用 `continue-on-error` 或 `|| true` 静默放行。门禁 5 由聚合入口以 `--baseline` 形式调用（模式 b）。

> INV-LIMIT 的 `TestLimitNegativeValueInvariant` 采用**双重运行（defense-in-depth）**：① 默认 surefire（本地 `./mvnw test -pl nop-metadata -am` 即覆盖，底层 limit 缺陷已修复，该测试全绿）；② CI `invariant-gate` job 经聚合入口 `run-nop-metadata-invariants.sh` step 4 显式 fail-fast 复跑（第二层，任一重新引入 `limit<0?0:limit` / `Math.abs(limit)` 的改动在本地默认测试与 CI 两处均变红）。表完备性由 `TestLimitTargetSetCompleteness`（默认 surefire，常驻 PASS）守护——新增 limit-taking 入口方法不入方法表即该测试变红。

## 棘轮规则（Ratchet）

> **已沉淀的不变式只增不减。** 弱化 / 删除 / 豁免需人工确认 + 留痕 + committed 回归测试同步。

- 门禁集合单调递增：Cycle 1 沉淀 4 条；Cycle 2 增补 silent-wrong-result 单门禁（内含 5 子族规则）；后续 Cycle（对抗探查发现新族）只增不减。
- 弱化一条不变式（如增加例外、缩小扫描面）必须在不变式目录留痕 + 人工确认 + 同步回归测试。
- 新增 / 重命名 `processor` / `bizmodel` / ORM entity 必须被对应门禁覆盖：
  - 新增 catch 块 → INV-SILENT-SWALLOW 自动覆盖（扫描全部 catch）。
  - 新增 `<unique-key>` → INV-UK 自动覆盖（扫描全部 orm.xml）。
  - 新增 limit-taking public 入口 → 必须同时更新 `TestLimitNegativeValueInvariant` 方法表 + `TestLimitTargetSetCompleteness` 完备性断言，否则后者变红。
  - 新增默认 locale case-mapping / 截断强转 / String 子串分类 / 分隔符复合键 / 无路由 double→BigDecimal → INV-SILENT-WRONG-RESULT 自动覆盖（baseline 外新键即红；防"修实例不修类别"回潮）。

## 表完备性说明

INV-SILENT-SWALLOW / INV-UK / INV-SENSITIVE / INV-SILENT-WRONG-RESULT 为**全量扫描**（扫描全部目标，不绑方法表），天然覆盖新增目标。INV-LIMIT 为**方法表驱动穷举**，完备性由独立的 `TestLimitTargetSetCompleteness` 守护（反查 `@Name("limit")` 全部入口，与方法表比对）。

## 复触发条件（下一轮 Cycle 启动）

下列任一条件触发时，启动下一轮不变式审计 Cycle：

- CI `invariant-gate` 变红（门禁命中；模式 b 门禁 = baseline 外新命中）。
- 新增或重命名 `processor` / `bizmodel` / ORM entity（结构变更）。
- 周期复探（定期重跑对抗探查，捕捉盲区漂移）。
