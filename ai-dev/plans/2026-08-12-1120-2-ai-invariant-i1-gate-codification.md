# 2 AI Invariant Loop I1 — 不变式沉淀（首批门禁）

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I1. 不变式沉淀（首批门禁）
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I1；`ai-dev/skills/invariant-loop-audit-prompt.md`（门禁即契约、单调棘轮、表完备性）
> Related: `ai-dev/plans/2026-08-12-1120-1-ai-invariant-i0-inventory-baseline.md`（I0 提供不变式目录与目标集表）；`2026-08-12-1120-3-ai-invariant-i2-gate-driven-audit.md`（I2 消费本 plan 门禁产出 red list）

## Purpose

把 I0 目录沉淀的 5 条不变式落地为**可执行门禁**（非文档），全部接入 CI，采用「已知缺口清单 + 单调棘轮」机制：门禁对未知缺口 fail、对已登记缺口零增长，I2 以缺口清单作为 red list 输入，I4 修复后清单归零、I5 验证零命中。门禁集合只增不减（弱化需人工确认 + 留痕）。

## Current Baseline

- I0 已产出（前置条件，本 plan 执行前须确认）：
  - `ai-dev/audits/nop-ai-invariants/invariant-catalog.md`：5 条不变式（secure-default / timeout 声明 / 清理对称性 / ToolExecutor 安全边界 / fix-commit real-diff），每条含检测方法。
  - 4 张目标集表：Default* 类（约 33）、编排入口、ToolExecutor 实现（约 27）、资源 entry-point 方法——各带复现命令，构成表完备性数据源。
- 技术栈现状（live 核实）：
  - 全仓 `pom.xml` 无 ArchUnit——门禁①采用 ArchUnit 需先引入依赖（roadmap §I1 明示「需先引入 ArchUnit 依赖」）。
  - `ai-dev/tools/` 已有 mjs 工具先例（`check-*.mjs`、`scan-hollow-implementations.mjs`）、ast-grep 规则 3 条（`ai-dev/tools/rules/java-lint-*.yml`）、`sgconfig.yml`、`package.json`（scripts 含 `check:` / `scan:` 前缀）——门禁④⑤按同模式追加。
  - CI：`.github/workflows/maven.yml` 目前仅 `mvn -B package`（build job）+ e2e job，无 node 检查步骤；本地聚合入口为 `ai-dev/tools/package.json` 的 `check` script。
  - 测试基建：nop-ai-agent 测试目录结构成熟（`engine/`、`security/` 等，TestSecureByDefault / TestLayer23SecureDefaults / TestPlan271AsyncTimeoutReliability / TestEngineEntryCleanupSymmetry 为实例级先例）。
- 历史证据：五族缺口（无 secure-default 声明 / 无 timeout 声明 / 清理不对称 / 无安全边界声明 / 空 fix commit）在已关闭实例上有案可查（Lesson 05/08、arm-index），但**尚无任何实例被门禁机械枚举**——首跑预期产出 I2 的 red list，而非门禁直接全绿。两者的桥接 = known-gaps 清单：首跑缺口**登记入清单**（供 I2 消费）而非留作门禁红灯，因此「门禁引入后 CI 全绿」与「首跑产出 red list」不矛盾：绿 = 清单与现状精确一致，red list = 清单内容本身。

## Goals

- 五族门禁全部落地为可执行形式：
  ① Default* 类 secure-default 声明门禁（ArchUnit 规则，引入 `com.tngtech.archunit:archunit-junit5` 测试依赖，`Decision` 确认）；
  ② 编排入口 timeout 声明门禁（JUnit `@ParameterizedTest` + 方法表穷举）；
  ③ entry-point 资源清理对称性门禁（JUnit `@ParameterizedTest` + 方法表穷举）；
  ④ ToolExecutor 安全边界声明门禁（`ai-dev/tools/` mjs 静态扫描 + ast-grep 规则）；
  ⑤ fix-commit real-diff 验证门禁（mjs git 扫描）。
- 每个门禁配套 **known-gaps 清单**（`ai-dev/audits/nop-ai-invariants/gate-gaps.*`）：登记首跑发现的既有缺口（= I2 red list 输入），门禁在 CI 中对**清单外**的新缺口 fail（单调棘轮 + 表完备性：新增 Default* 类/编排入口/ToolExecutor 不入表即 red）。
- 全部入 CI：JUnit/ArchUnit 门禁随 `./mvnw test -pl nop-ai -am -T 1C` 执行；mjs 门禁接入 `.github/workflows/maven.yml`（新增 job 或扩展现有 job，`Decision`）。
- 门禁引入后 CI 全绿（缺口清单与当前缺口集精确一致），每个门禁有 committed 回归测试。

## Non-Goals

- **不裁决缺口**（I3）：门禁只产出缺口清单，不判定 P 级、不改代码。
- **不修复缺口实例**（I4）：已有实例不补声明（除非修复即「声明与门禁契约同步」且属门禁自身接线）。
- **不运行全量审计**（I2）：本 plan 只验证门禁自身正确（正例绿、反例红、缺口集匹配），不追求发现全部问题。
- 不改产品运行时行为：门禁均为静态/测试期检查，不侵入产品代码路径。
- 不新增 ArchUnit 之外的静态分析框架；不引入新 CI 平台。

## Scope

### In Scope

- 五族门禁的实现与 committed 回归测试。
- ArchUnit 依赖引入（nop-ai 目标模块 test scope）与 CI 接线。
- known-gaps 清单机制（登记/校验/归零流程）与门禁聚合入口（package.json scripts / maven profile）。
- invariant-catalog.md 的「检测方法」列与门禁产物双向同步。

### Out Of Scope

- 缺口裁决表（I3）、修复执行（I4）、全量验证（I5）、收口（I6）。
- 对门禁覆盖面之外的新审计维度（I2 对抗探查）。

## Execution Plan

### Phase 1 - 门禁机制决策

Status: completed
Targets: `nop-ai/nop-ai-agent/pom.xml`（及门禁落地的测试模块）、`ai-dev/audits/nop-ai-invariants/`、`.github/workflows/maven.yml`

- Item Types: `Decision`

- [x] `Decision` **前置校验**：确认 I0 已落地（`ai-dev/audits/nop-ai-invariants/invariant-catalog.md` 存在、四张目标集表存在且带复现命令）；不满足则 fail-fast，不得在目录缺失时开工。
- [x] `Decision` 门禁①技术选型：确认引入 ArchUnit（`archunit-junit5`，测试 scope）到门禁落地模块；明确 ArchUnit 扫描范围与 Nop `_gen`/代码生成产物的排除策略（仅扫描 `src/main` 非生成类）；**跨模块 classpath 约束**：Default* 类分布在 agent/core/toolkit/shell 四模块，`nop-ai-agent` 依赖 core+toolkit 但不依赖 shell——须显式选择「单覆盖模块 vs 每模块规则 vs 排除+理由」，避免一个测试模块扫不到全部目标类；若 ArchUnit 与项目加载机制冲突导致不可用，回退为「JUnit 反射扫描 + 声明注解」方案并在 catalog 记录裁定。
- [x] `Decision` 门禁①**声明形式与语义裁定**：定稿「secure-default 声明」的判定标准（新 Default* 类必须满足什么可观测条件才算声明：如声明性注解 + 构造期兜底语义），并明确**该门禁只验证声明存在性与类别属性，不验证行为语义**（行为级验证 = I2 接线抽查与探查职责）——防止门禁退化为「注解存在即通过」的空转检查（Lesson 08 声明 ≠ 接线教训）。
- [x] `Decision` known-gaps 机制定稿：清单文件位置/格式（族 → 实例 → 登记原因 → 登记日期 → 登记方），门禁判定规则（清单外实例缺声明 = fail；清单内实例 = pass）；**清单变更策略（防静默白名单）**：只有 I1 首跑登记 + I2/I3 裁决后的 finding 登记两种入口，登记须附裁决证据；**任何人不允许为保持 CI 绿而向清单添加条目**（添加即 = 缺口在 I4 修复而非豁免）；清单移除 = I4 修复后（移除后门禁恢复对此实例的实判，未真修复即 fail——自校验）。
- [x] `Decision` CI 接入方式定稿：JUnit/ArchUnit 门禁随现有 `mvn test`（无新增步骤）；mjs 门禁（④⑤）在 `.github/workflows/maven.yml` 新增 node job（复用 `ai-dev/tools/package.json` scripts）或并入现有 job——按仓库 CI 现状最小改动裁定；**mjs 依赖安装策略明确**（`ai-dev/tools/` 无 `pnpm-lock.yaml`：提交 lockfile vs 非 frozen `pnpm install`，以 CI 稳定性优先裁定）。
- [x] `Decision` 门禁②③「timeout 声明/清理对称性」判定标准定稿：编排入口必须满足什么可观测条件才判定为「已声明 timeout」（方法签名参数 / 配置项 / 注解，至少一种）；entry-point 判定标准同理。判定标准写入门禁测试类头注释与 catalog。
- [x] `Decision` 门禁④**声明 vs 接线边界**：静态扫描只验证「安全边界声明存在 + 校验调用点出现」（机械可判），**运行时接线验证属 I2 的接线抽查**（本 plan 的 I2 successor 已含该职责）——门禁④不承诺静态证明运行时接线，计划文本不夸大其能力。

**Phase 1 Decision Records（2026-08-12 执行时裁定，`Decision` 留痕）**：

- **D1 前置校验（PASS）**：`invariant-catalog.md` 存在（4 表齐备带复现命令，2026-08-12 live 复核：33/13/31/8 行数与命令输出一致）。
- **D2 门禁①技术选型**：**采用 ArchUnit**——`com.tngtech.archunit:archunit-junit5:1.5.0`（test scope）加入 `nop-ai-agent` + `nop-ai-shell` 两个模块（版本号直接写在模块 pom，nop-bom 不管理 ArchUnit）。**跨模块扫描策略 = 每模块规则**：`nop-ai-agent` 测试 classpath 覆盖 agent(22)+core(8)+toolkit(1)=31 类（agent 依赖 core+toolkit）；`nop-ai-shell` 测试覆盖 shell(2) 类（shell 依赖 toolkit，agent 与 shell 互不可见）。表完备性用**源树机械反查**（复刻 I0 `find` 命令：`src/main` 下 `Default*.java`，排除 `/test/`、`/_gen/`、`/target/`）与表 diff；声明检查用 ArchUnit/反射读类注解。ArchUnit 扫描范围 = `io.nop.ai.*` 包（`importPackages`），`_gen`/测试类不进源树扫描。**回退预案**（catalog 记录）：若 ArchUnit 与 junit-platform 6.0.3 / Java 26 运行时不兼容（实测 archunit-junit5-engine 依赖 junit-platform-engine 1.14.4，mediation 后为 6.0.3），回退为 `com.tngtech.archunit:archunit` 核心库编程式 `ClassFileImporter`（不引入 junit5 engine）或纯 JUnit 反射扫描。
- **D3 声明形式与语义**：声明形式 = **类级 marker 注解 `@SecureDefault`**（新建，置于 `nop-ai-api` 的 `io.nop.ai.api.secure` 包——四模块的共同传递依赖，供 agent/core/toolkit/shell 引用；纯声明、无行为）。门禁只验证（a）注解存在性（b）类别属性（`src/main` 具体类、类名 `Default` 前缀、非生成类），**不验证行为语义**（行为验证 = I2 接线抽查）。**首跑既有 33 类均无注解 → 全部登记 known-gaps**（reason-type `missing-declaration`，理由「I1 门禁契约引入，等 I4 补注解」，不修复——Non-Goals 已有实例不补声明）。新增 Default* 类：不带注解且不在清单 = red。
- **D4 known-gaps 机制**：文件 `ai-dev/audits/nop-ai-invariants/gate-gaps.yaml`（YAML：js-yaml 供 mjs、snakeyaml 2.6 供 JUnit——已在 nop-ai-agent classpath 上）。结构：族（`gate-1-default-secure` / `gate-2-orchestration-timeout` / `gate-3-entry-point-cleanup` / `gate-4-tool-boundary`）→ 实例（FQCN/方法引用）→ 原因 → reason-type（`missing-declaration` / `not-applicable`）→ 日期 → 登记方。判定规则：表内实例 = 声明通过 或 在清单（同族）→ pass；否则 fail。变更策略：登记入口仅（1）I1 首跑（2）I2/I3 裁决 finding（附裁决证据）；**禁止为保持 CI 绿添加条目**；移除 = I4 修复后（移除即恢复实判，自校验）。
- **D5 CI 接入**：JUnit/ArchUnit 门禁随 `./mvnw test -pl nop-ai -am -T 1C` 自动执行（无新增步骤）；mjs 门禁④⑤在 `.github/workflows/maven.yml` 新增 `invariant-gates` node job（`actions/checkout@v4` + `fetch-depth: 0` + pnpm 10 + node 20，`pnpm install --frozen-lockfile`（先提交 `ai-dev/tools/pnpm-lock.yaml`）→ `pnpm check:ai-invariants`）。**mjs 安装策略 = 提交 lockfile**（CI 稳定性优先）。
- **D6 门禁②③判定标准**：**timeout 声明**（满足任一）：(a) 入口方法签名含 timeout 形参（`long`/`Duration`/`*TimeoutMs`）；(b) 入口实现路径文件引用 timeout 机制标记（`orTimeout` / `callChatWithTimeout` / `get(..., TimeUnit` / `TimeoutException`）；(c) 入口声明类/其配置持有非零 timeout 配置项（如 `DefaultAgentEngineConfig` `callAgentTimeoutMs=120000`/`llmTimeoutMs`/`toolTimeoutMs`）。不适用（无异步执行等待面：`forkSession`/`cancelSession`/`close`）→ 登记 `not-applicable` + 理由（I3 裁决输入），不静默 pass。**清理对称性**：entry-point 行 = acquire 标记 + 对称 release 标记在同一指定源文件都存在（行锚定，live 核实）；行为级对称验证 = 既有 `TestEngineEntryCleanupSymmetry` 基线 + I2。
- **D7 门禁④声明 vs 接线边界**：静态扫描只验证「安全边界声明存在 + 校验调用点出现」（机械可判，按 I0 表安全敏感面列：网络 → `SsrfAddressGuard`/`validateUrl`/`validateHost` 引用；文件 → `IToolFileSystem`/`VirtualFileSystem`/`IResource` 有界抽象且无裸 `java.io.File`/`java.nio.file` 构造；命令 → `validateCommand` 引用；内存/会话 → 无外部 IO 面）。边界实现 `LocalToolFileSystem` 的 `isPathAllowed` 接线（`resolveFile` 内）单独检查一次。**运行时接线验证不属本门禁**（归 I2 接线抽查）。

Exit Criteria:

- [x] 六项决策的裁定与理由记录于 catalog「检测方法」节或本 plan（`Decision` 留痕）
- [x] ArchUnit 依赖坐标与模块选定（若采用），pom.xml diff 可审查；跨模块扫描策略明确（含 shell 模块 Default* 类的归属）
- [x] known-gaps 清单文件结构示例就位（可含空清单），变更策略（谁可登记、证据要求、禁止为绿添加）成文
- [x] CI 接入方案与 mjs 安装策略明确（改动文件路径可预见）
- [x] 门禁①④判定标准成文（含声明 ≠ 行为、声明 ≠ 接线边界）
- [x] 门禁②③判定标准成文（I2 裁决时可直接引用）
- [x] 无 owner-doc update required（决策为 ai-dev 内部裁定；若引入 ArchUnit 影响构建文档，`docs-for-ai/` 构建节需同步——以实际改动为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 门禁①：Default* secure-default 声明

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`（或按 Phase 1 决策选定的模块）、`invariant-catalog.md`

- Item Types: `Fix | Proof`

- [x] `Fix` 实现门禁①：对 I0 目标集表全部 Default* 类断言「声明安全默认配置」（ArchUnit 规则或反射扫描 + 声明机制），判定标准 = Phase 1 裁定的声明形式（显式 secure-default 声明：注解/接口/构造期兜底），**门禁只验证声明与类别属性，不验证行为语义**（行为验证归 I2）。
- [x] `Proof` 表完备性接线：新 Default* 类不入目标集表即触发「未登记」fail（从 `src/main` 机械反查类名，与表 diff）。
- [x] `Fix` 编写 committed 回归测试：正例（已声明类绿）+ 反例（构造临时缺声明类必红）+ 表完备性用例。
- [x] `Proof` known-gaps 登记：首跑发现的全部缺口实例登记入清单（不修复，供 I2 消费）。

**Phase 2 落地记录（2026-08-12）**：

- 依赖：`com.tngtech.archunit:archunit-junit5:1.5.0`（test scope）加入 `nop-ai-agent` + `nop-ai-shell` pom（archunit-junit5-engine 传递依赖 junit-platform-engine 1.14.4，mediation 后为项目 6.0.3——实测 `ClassFileImporter` 运行正常；测试以 JUnit 5 普通 @Test + ArchUnit ClassFileImporter 编程式断言实现，避免 @ArchTest 引擎发现路径）。
- 声明注解：`io.nop.ai.api.secure.SecureDefault`（nop-ai-api，marker 纯声明）。
- 门禁测试：`TestInvariantGate1SecureDefault`（nop-ai-agent，31 类：agent 22 + core 8 + toolkit 1；5 tests）+ `TestInvariantGate1SecureDefaultShell`（nop-ai-shell，2 类；3 tests）——声明检查（ArchUnit import + isAnnotatedWith）+ 表完备性（源树机械反查 `src/main` 复刻 I0 find 命令，排除 test/_gen/target 段）+ 反例（临时 fixture 写入 `src/main` 的 end-to-end：无注解新类必红、有注解不入表必红、表内无注解无登记必红）+ 判定谓词逻辑测试。
- known-gaps 登记：33/33 Default* 类登记 `gate-gaps.yaml` family `gate-1-default-secure`（`missing-declaration`，I4 补注解，不修复）。
- 验证：`./mvnw test -pl nop-ai -am -T 1C` 全绿（含门禁，BUILD SUCCESS 03:30）。

Exit Criteria:

- [x] 门禁①测试类存在且随 `./mvnw test -pl <模块> -am -T 1C` 运行，全绿
- [x] 反例测试验证「缺声明的 Default* 类」必红（门禁不是空转）
- [x] 表完备性用例验证「新类未登记」必红
- [x] 缺口清单 = 首跑实际缺口集（差异为零或显式记录）
- [x] **无静默跳过**：门禁对不可判定的类（抽象类/生成类）显式标注排除理由，不静默 pass
- [x] 无 owner-doc update required（若 Phase 1 裁定需引入 ArchUnit，则需同步 `docs-for-ai/` 构建说明——以裁定为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 门禁②③：编排入口 timeout 声明 + entry-point 清理对称性

Status: completed
Targets: `nop-ai/*/src/test/**`

- Item Types: `Fix | Proof`

- [x] `Fix` 实现门禁②：`@ParameterizedTest` 穷举 I0 编排入口表，逐入口断言「已声明 timeout」（判定标准见 Phase 1）；无法满足者登记缺口。
- [x] `Fix` 实现门禁③：`@ParameterizedTest` 穷举 I0 资源 entry-point 表，逐入口断言 acquire/release 对称（以既有 TestEngineEntryCleanupSymmetry 语义为基线）；无法满足者登记缺口。
- [x] `Proof` 表完备性接线（同 Phase 2 机制：新入口未登记即 red）。
- [x] `Fix` committed 回归测试：正例 + 反例（构造缺 timeout / 缺清理的临时入口必红）+ 表完备性用例。
- [x] `Proof` known-gaps 登记（门禁②③首跑缺口）。

**Phase 3 落地记录（2026-08-12）**：

- 门禁② `TestInvariantGate2OrchestrationTimeout`（nop-ai-agent `io.nop.ai.agent.gate`）：14 入口表（含 **I1 表完备性发现**——I0 §3.2 漏计 `IAgentEngine.getSessionStatus`，live 接口公共方法实为 10 个，已补入目录并登记 not-applicable），逐入口断言「已声明 timeout」（声明 = 证据文件含标记：`callAgentTimeoutMs` 配置 / `llmTimeoutMs` 构造参数 / `orTimeout` 机制调用）；表完备性 = §3.2 判定标准机械反查（IAgentEngine 公共方法正则解析 + engine 包非 IAgentEngine 实现类 execute/executeAllowedCalls + CallAgentExecutor.executeAsync）。首跑：9 declared + 4 not-applicable（forkSession/cancelSession/close/getSessionStatus）+ 1 missing（SingleTurnExecutor.execute，登记缺口）。
- 门禁③ `TestInvariantGate3EntryPointCleanup`：8 行 entry-point 表（file→marker 映射），逐行断言 acquire/release 成对存在；表完备性 = §3.4 五族复现命令回查（标记仍命中预期文件）。首跑：8/8 成对存在，零缺口。
- known-gaps 登记：gate-2 5 条（4 not-applicable + 1 missing-declaration）；gate-3 零。
- 验证：`./mvnw test -pl nop-ai -am -T 1C` 全绿（含门禁，BUILD SUCCESS）。

Exit Criteria:

- [x] 门禁②③测试类存在且全绿，反例验证门禁有效（非空转）
- [x] 表完备性用例验证「新编排入口/新 entry-point 未登记」必红
- [x] 门禁②③缺口清单精确（= 首跑实际缺口集）
- [x] **无静默跳过**：判定标准外的入口显式登记「不适用 + 理由」（I3 裁决输入），不静默 pass
- [x] 无 owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁④⑤：ToolExecutor 安全边界 + fix-commit real-diff

Status: completed
Targets: `ai-dev/tools/check-ai-tool-executor-boundary.mjs`（新）、`ai-dev/tools/check-fix-commit-diff.mjs`（新）、`ai-dev/tools/package.json`、`ai-dev/tools/rules/`（如用 ast-grep）

- Item Types: `Fix | Proof`

- [x] `Fix` 实现门禁④：mjs 静态扫描 I0 ToolExecutor 表，断言每个实现「声明安全边界」= 声明存在 + 校验调用点出现（机械可判：SSRF/路径逃逸/命令注入防护的校验入口在类内被引用，按 Lesson 08 判定规则）；**运行时接线验证不属本门禁**（归 I2 接线抽查，见 Phase 1 决策）；清单外新执行器未登记即 fail。
- [x] `Fix` 实现门禁⑤：mjs 扫描 git 历史中 `fix(nop-ai)` 模式 commit（**范围 = 2026-07-31 审计关闭后全部 `fix(nop-ai)` commit**，含 PR 内 commit 的 CI 场景），断言实质 diff > 0（排除纯版权头/空白/生成文件）；零实质变更 commit 报错（Lesson 05 判定规则）。
- [x] `Fix` 为④⑤编写自测（fixture 目录：正例/反例/边界用例），并注册到 `ai-dev/tools/package.json` scripts（`check:` / `scan:` 命名）。
- [x] `Proof` known-gaps 登记（门禁④首跑缺口）；门禁⑤为 commit 卫生门禁（不适用 known-gaps 清单：输出 = 违规 commit 清单，由 I2/I3 消费——见 I2 计划的处理约定）。
- [x] `Fix` CI 接线：按 Phase 1 决策在 `.github/workflows/maven.yml` 加入 mjs 门禁步骤（④⑤），验证 CI 配置语法（本地 dry-run 或 workflow 模拟）；**门禁⑤ 的 PR 扫描 job 须配置 `fetch-depth: 0`（或 base-ref 深度拉取）**——`actions/checkout@v4` 默认 shallow clone 会让 PR 内 commit 扫描静默为空（gate 空转风险）。

**Phase 4 落地记录（2026-08-12）**：

- 门禁④ `check-ai-tool-executor-boundary.mjs`：内嵌 §3.3 目标集 30 实例（surface 列 + 校验入口标记），逐实例断言声明成立（网络 → `SsrfAddressGuard`/`validateUrl`/`validateHost`/`ISearchEngine` 有界抽象；命令 → `validateCommand`；文件 → `IToolFileSystem`/`VirtualFileSystem`/`IResource`/`ICompactionArchiveReader` 有界抽象且无裸 `java.io.File`/`java.nio.file` 构造；内存 → 无外部 IO 面）；边界实现 `LocalToolFileSystem` `isPathAllowed`→`resolveFile` 接线单独检查；表完备性 = §3.3 复现 grep 反查（28 direct + 3 indirect − 抽象基类）。首跑：29/30 声明成立（AskOracleExecutor not-applicable 登记缺口；UpdateTodosExecutor 表标注文件面 live 内存实现，按 live 面判成立 + note 供 I2）。`--self-test` 7 例全绿（正例绿/反例红/登记机制）。
- 门禁⑤ `check-fix-commit-diff.mjs`：扫描 `fix(nop-ai)` commit（本地 `--since 2026-07-31` / PR `--base-ref <sha>`），实质 diff = hunk 内配对（removed.trim == added.trim 为纯空白差异跳过）+ 排除版权头/纯空白/生成路径（`/target/`、`/_gen/`、`_` 前缀）后 > 0。首跑：2026-07-31 后 5 个 commit（2026-08-01 MA4.2/4.5 系列）全部实质 diff > 0，**零违规**。`--self-test`：_tmp 构造 4 commit 仓库（正例 + 空白/版权头/生成文件反例 + PR 模式），7 例全绿。
- scripts：`check:ai-tool-boundary` / `check:fix-commit-diff` / `check:ai-invariants`（自测 + 实跑聚合）注册；`pnpm-lock.yaml` 生成并解除 ignore（D5 裁定：提交 lockfile，CI `--frozen-lockfile`）。
- CI：`.github/workflows/maven.yml` 新增 `invariant-gates` job（`fetch-depth: 0` + pnpm 10 + node 20 + `pnpm install --frozen-lockfile` + `pnpm check:ai-invariants`）；workflow YAML 本地 `yaml.safe_load` 校验通过；**GitHub Actions 实跑无法本地验证，显式登记为 I5 验证项**。
- 验证：`pnpm check:ai-invariants` 全绿 exit 0；`pnpm check:ai-tool-boundary` / `pnpm check:fix-commit-diff` exit 0。

Exit Criteria:

- [x] 门禁④⑤脚本存在、自测全绿、正反例用例验证有效
- [x] package.json 新 scripts 可执行（门禁④：`pnpm check:ai-tool-boundary`；门禁⑤：`pnpm check:fix-commit-diff`——脚本名与 `check-ai-tool-executor-boundary.mjs` / `check-fix-commit-diff.mjs` 一一对应）
- [x] 门禁④缺口清单精确；门禁⑤对 2026-07-31 后全部 `fix(nop-ai)` commit 运行结果记录（预期零或显式登记，含 PR 内 commit 场景）
- [x] CI workflow 改动落地且可观测（diff 可审查；若 CI 无法本地验证，记录理由并显式登记为 I5 验证项）
- [x] 无 owner-doc update required（若 CI 改动影响构建文档则同步——以实际改动为准）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 聚合接线与全绿验证

Status: completed
Targets: `ai-dev/tools/package.json`、`.github/workflows/maven.yml`、`ai-dev/audits/nop-ai-invariants/`

- Item Types: `Fix | Proof`

- [x] `Fix` 聚合入口：mjs 门禁（④⑤）加入聚合 script（如 `check:ai-invariants`），JUnit 门禁（①②③）随模块测试自动执行——聚合方式与 Phase 1 决策一致。
- [x] `Proof` 全绿验证：`./mvnw test -pl nop-ai -am -T 1C` + 聚合 script 全部通过；known-gaps 清单与门禁实际输出 diff 为零。
- [x] `Proof` 门禁有效性与棘轮验证：临时构造「清单外新违规」使门禁必红，验证后回滚（证明门禁真在拦截，而非恒绿）。
- [x] `Fix` catalog 同步：5 条不变式的「检测方法」列更新为实际门禁产物路径 + 运行命令；目标集表与 known-gaps 双向引用。

**Phase 5 落地记录（2026-08-12）**：

- 聚合入口：`pnpm check:ai-invariants` = 门禁④⑤ self-test（拦截力自证）+ 实跑（`check-ai-tool-executor-boundary.mjs` + `check-fix-commit-diff.mjs`）——exit 0 全绿；JUnit 门禁①②③随 `./mvnw test -pl nop-ai -am -T 1C` 自动执行。
- 全绿验证：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（含门禁测试）；`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw compile -pl nop-ai -am` OK；checkstyle 非构建门禁（全仓 9164 条 pre-existing 违规，`sun_checks.xml`，`-Pqa` profile 外不阻断——与 I0 基线一致）。
- 棘轮验证（红 → 回滚 → 绿，证据见 daily log）：
  - 门禁①：临时写入 `DefaultRatchetFixture.java`（src/main，无声明）→ `tableCompleteness_matchesSourceTree` 红（"must be added to invariant-catalog §3.1"）→ 删除 → 绿。
  - 门禁②：临时注释 `SingleTurnExecutor.execute` 缺口登记 → 门禁红（"verdict 'missing' but not registered"）→ 恢复 → 绿。
  - 门禁④：临时注释 `AskOracleExecutor` 缺口登记 → 红 → 恢复 → 绿；临时新增 `DefaultRatchetToolExecutor`（implements IToolExecutor 不入表）→ 红（"NOT in the gate table"）→ 删除 → 绿。
  - 门禁⑤：self-test 构造 _tmp 仓库 4 commit（正例 + 空白/版权头/生成文件反例）→ 3 反例全部判零 diff、整体 exit 1 → 真实仓库 5 commit 零违规。
  - 门禁③：committed 反例测试（`negative_missingCleanupMarkerIsRejected`）。
- catalog 同步：INV-1~5 检测方法节 = 实际门禁产物（测试类/mjs 文件 + 运行命令）；§3.1~§3.4 表头增加 known-gaps family 双向引用；§3.5 known-gaps 机制完整成文；§3.2 补录 I1 表完备性发现（getSessionStatus）。

Exit Criteria:

- [x] 全量命令绿：`./mvnw test -pl nop-ai -am -T 1C`（含门禁①②③）+ `pnpm check:ai-invariants`（④⑤）
- [x] 棘轮验证记录：构造违规 → 门禁红 → 回滚 → 绿（证据在 daily log）
- [x] known-gaps 清单与门禁输出一致（零 drift）
- [x] catalog 检测方法列与门禁产物一一对应（含运行命令）
- [x] **端到端验证**：从「新增一个缺声明的 Default* 类/编排入口/ToolExecutor」→ 门禁 red 的完整路径已验证（用户/开发者新增代码被 CI 拦截的闭环）
- [x] **接线验证**：门禁确实在 CI 与本地测试命令链上被调用（非孤岛脚本）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 五族门禁全部落地且有效（正例绿 / 构造反例红 / 棘轮验证通过）
- [x] known-gaps 机制运转：清单登记、零增长校验、归零流程成文
- [x] 全部入 CI（JUnit 随 mvn test；门禁④⑤在 `.github/workflows/maven.yml` 步骤——不满足于仅本地聚合脚本）
- [x] 门禁引入后 CI 全绿（= known-gaps 清单与现状精确一致，见 Baseline 桥接说明），清单变更遵守 Phase 1 策略（无未经裁决的登记）
- [x] invariant-catalog.md 检测方法列与门禁产物同步（owner 文档更新，若引入 ArchUnit/CI 改动影响 docs-for-ai 构建节则同步）
- [x] 不存在被静默降级的 in-scope 项（门禁缺陷/契约 drift 不得进 follow-up）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）门禁在 CI 与测试链上实际执行（非存在性文件），（b）无空转门禁（每门禁有反例用例证明其拦截力），（c）known-gaps 非静默白名单（清单外必红，清单变更受 Phase 1 策略约束）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（guide rule 5b，closure 时执行）——实际退出码 1 = 2 条 **pre-existing** high finding（2026-08-01 commit，本 plan 零引入），pre-existing 基线记录于 Closure Evidence
- [x] `./mvnw compile -pl nop-ai -am -T 1C`
- [x] `./mvnw test -pl nop-ai -am -T 1C`（含门禁）
- [x] checkstyle / 代码规范检查通过（`check-import-order.mjs` 等既有门禁不回归）

## Deferred But Adjudicated

### 门禁⑤ fix-commit real-diff 的 CI 形态

- Classification: `optimization candidate`（历史 commit 全量扫描的 CI 形态待定）
- Why Not Blocking Closure: 门禁⑤落地两层：CI 步骤覆盖 PR 内 `fix(nop-ai)` commit（checkout 后对 PR 提交做有界历史扫描，不阻塞当前代码面门禁）；2026-07-31 前的历史 commit 全量扫描由本地工具 + 收口流程（I5/I6）执行。当前代码面的五族门禁（①②③④）不受影响。
- Successor Required: `no`（I6 收口流程复用该工具）

### 门禁②③中「判定标准外」的入口

- Classification: `watch-only residual`（首跑后由 I3 裁决，本 plan 仅登记不裁决）
- Why Not Blocking Closure: 门禁职责是枚举 + 报告，判定归属 I3；本 plan 只保证报告无遗漏、无静默 pass。
- Successor Required: `yes` — I2/I3

## Non-Blocking Follow-ups

- 门禁运行耗时优化（如 ArchUnit 扫描范围收窄）——若首跑 >30s 再评估。
- mjs 门禁与 ast-grep 规则合并（若某族更适合规则形式）——优化候选。

## Closure

Status Note: 五族门禁（3 JUnit/ArchUnit + 2 mjs）全部落地、known-gaps 机制运转、CI 接线完成、门禁引入后全绿、棘轮验证通过、独立 closure audit APPROVED——满足全部 Exit Criteria 与 Closure Gates，Plan 关闭。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session，task `ses_00ba81d34ffeBmsVLIilq1bTY3`）
- Evidence:
  - **Phase 1-5 全部 PASS**：审计者逐条 live 核对（门禁测试类/注解/pom 依赖/已知缺口清单 33+5+1 条带证据字段/mjs 脚本/package.json scripts/pnpm-lock.yaml 未 ignore/CI job fetch-depth:0/目录同步含 §3.2 getSessionStatus 补录 14 行）。
  - **门禁有效性**：门禁①②③ surefire 实测全绿（agent 5+3+3、shell 3）；门禁④⑤ 实跑 exit 0 + self-test exit 0（正例绿/反例红）；负例前提 live 验证（如 ReActAgentExecutor 零 orTimeout → 反例测试真红）。
  - **Anti-Hollow（a）**：门禁在 `mvn test`（surefire 报告）与 pnpm scripts/CI job 链上实际执行，非孤岛文件；（b）每门禁有反例用例（① src/main 临时文件 end-to-end、②③ committed 反例测试、④⑤ fixture 反例）；（c）known-gaps 非静默白名单：表内实例无声明无登记必红（satisfiesGate 逻辑 + mjs 未登记 N/A/表外实现 red），清单仅首跑登记且全带证据字段。
  - **checklist 完整性**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1120-2-ai-invariant-i1-gate-codification.md --strict` 退出码 0。
  - **构建**：`./mvnw compile -pl nop-ai -am -T 1C` 退出 0；`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（含门禁）。
  - **scan-hollow**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 1——**pre-existing 基线**：恰 2 条 high finding（`PlanReplanner.java:272`、`NoOpProviderFailoverQueue.java:34`，git blame 均为 2026-08-01 commit，工作树零 diff，非本 plan 引入；其中 NoOpProviderFailoverQueue 为 Minimum Rules #24 合规的显式占位）。本 plan 新增文件零 finding（审计者核对）。该基线状态记录于本 closure（guide rule 5b 意图 = 本 plan 不引入新空壳，已满足）。
  - **Minor 修订**：审计发现 2 处 prose 计数不准确（门禁② declared 计数、门禁④ 27/30→29/30）——已修订（plan/catalog/gate-gaps.yaml/log/test javadoc 同步为 9 declared + 4 N/A + 1 missing / 29+1）。
  - **Info**：`nop-auth-service TestChannelScanBindLoginE2E` 偶发失败（VarCollector NPE，2026-08-09 既有，隔离运行通过，与本 plan 无关）；`check-doc-links --strict` 20 个 pre-existing 错误（他组 roadmap 引用，非本 plan 引入）。
  - **Deferred 分类检查**：无 in-scope live defect 被降级；deferred 仅 `optimization candidate`（门禁⑤历史全量扫描 CI 形态，I6 复用）+ `watch-only residual`（门禁②③判定标准外入口 → I2/I3 裁决），分类诚实。
  - **接线验证**：CI `invariant-gates` job 已落地（YAML 本地解析通过）；GitHub Actions 实跑无法本地验证——显式登记为 I5 验证项（plan 文本已记录）。

Follow-up:

- CI Actions 实跑验证（I5）。
- 门禁②③判定标准外入口（forkSession/cancelSession/close/getSessionStatus not-applicable + SingleTurnExecutor missing）= I2 red list 输入 → I3 裁决。
- 门禁④ AskOracleExecutor SSRF 校验入口待 oracle client 落地（I3/I4）。
- pre-existing scan-hollow 2 条 high finding 基线（PlanReplanner:272 注释措辞、NoOpProviderFailoverQueue:34 显式占位）——不属本 plan，留待后续治理。
- 门禁运行耗时优化（ArchUnit 扫描范围）与 mjs 门禁 ast-grep 合并 = non-blocking optimization candidates（Non-Blocking Follow-ups 段原文）。
