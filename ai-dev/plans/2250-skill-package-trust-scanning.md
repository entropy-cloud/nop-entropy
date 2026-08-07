# 2250 Skill 包信任扫描

> Plan Status: deferred
> Last Reviewed: 2026-08-03
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §14.2 ⑥ / §14.5；`ai-dev/analysis/agent-survey/2026-08-03-arbiteros-governance-kernel-deep-analysis.md` §3.10（Skill Trust）
> Related: `ai-dev/design/nop-ai-agent/skill-system-design.md`；`ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md`

## Deferral Note（2026-08-03）

本计划在 Round 1 对抗性审查中发现**根本性架构错位**，无法在当前 nop skill 加载模型下落地。标记 `deferred`，等待前置条件满足后重新评估。

### 根本性问题（Round 1 审查 Blocker）

1. **威胁模型错位**：ArbiterOS 的 skill trust 扫描针对 `.opencode/skills/<name>/SKILL.md`（自由文本，可含注入指令）。但 nop-ai-agent Java 运行时加载的是 `*.skill.yaml`（结构化 12 行 YAML：name/goal/dependencies/tags/resourceScope）——`FileSystemSkillProvider` 只读 `.skill.yaml`，**不读** `.opencode/skills/SKILL.md`。结构化 YAML 字段的注入威胁面极低（`goal` 是描述性短文本，不是可执行 prompt）。
2. **`SkillPackageSource` 未定义**：SPI 签名 `scan(SkillModel, SkillPackageSource)` 的核心输入参数 `SkillPackageSource` 没有定义——provider 在 parse YAML 后丢弃原始文件内容，scanner 无法拿到"skill 包的原始文件"。
3. **scanner 归属矛盾**：provider 持有（构造器注入）vs engine 透传（setter 链）两条路径互斥，且 provider 的懒缓存时序使 engine 晚注入的 scanner 永不执行。

### 重新激活的前置条件

以下任一条件满足时，重新评估本计划：

- **(a) SKILL.md → .skill.yaml 转换工具落地**：`skill-system-design.md:175-177` 描述的"AI 编写 SKILL.md → 工具转换为 .skill.yaml"的转换工具实现后，scanner 可在**转换前**扫描 SKILL.md 自由文本（威胁面高），而非扫描转换后的结构化 YAML。
- **(b) nop 引入自由文本 skill 加载**：若 `FileSystemSkillProvider` 扩展为直接加载 `.opencode/skills/SKILL.md`（不经 .skill.yaml 中间格式），scanner 有真实威胁面。
- **(c) 重新定义威胁模型**：若确认 `.skill.yaml` 的结构化字段（如 `dependencies: [bash_exec]`、`resourceScope: [CREDENTIALS]`）有独立威胁面（非注入，而是能力声明滥用），则需设计**不同的扫描策略**（基于字段值规则，非正则文本匹配），这本质是不同的设计。

### 设计文档同步

`nop-ai-agent-security-and-permissions.md` §14.2 ⑥ 当前标注 skill 扫描为"未来小增强"——该判定**仍然成立**，但本计划的审查发现"小增强"的前提（`.skill.yaml` 有可扫描的威胁面）未满足。§14.5 的 skill scanning 行应补充注记："待 SKILL.md→.skill.yaml 转换工具落地后重新评估"。

---

## Purpose（原始草稿，保留供重新激活时参考）

吸收 ArbiterOS 的 skill 包信任扫描概念，为 nop 的 `.opencode/skills/` 生态补上加载期安全扫描能力。

## Current Baseline（原始草稿）

- **已有**：`ISkillProvider` + `FileSystemSkillProvider`（加载 `*.skill.yaml`，**非 SKILL.md**）+ `ISkillCurator`（评估 skill 质量，非安全）。
- **缺失（重新评估后修正）**：skill 包加载时无安全扫描——但"skill 包"的物理形态是结构化 `.skill.yaml`（低威胁面），而非自由文本 SKILL.md（高威胁面）。原始草稿把两者混淆。

## Goals / Non-Goals / Scope / Execution Plan

（原始草稿内容保留在 git 历史中。重新激活时应基于上述 Deferral Note 的前置条件重写，而非沿用原始 Phase 设计——原始 Phase 建立在错误的"扫描 .skill.yaml 的 goal 字段"前提上。）

## Current Baseline

- **已有**：`io.nop.ai.agent.skill.ISkillProvider`（`getSkills() → Collection<SkillModel>`）+ `FileSystemSkillProvider`（shipped，filesystem 加载）+ `NoOpSkillProvider`（pass-through）。`SkillModel` 含 name/goal/intentSignature/topPattern/dependencies/tags/resourceScope——**无 trust/safety 字段**。
- **已有**：`ISkillCurator` + `LLMCurator`（LLM 评估 skill **质量**：是否 well-written/useful）+ `NoOpSkillCurator`。**质量 ≠ 安全**——curator 不检测注入/恶意。
- **缺失**：skill 包加载时无安全扫描。一个含 `"<instruction>ignore previous rules</instruction>"` 的 SKILL.md 会被无标记加载。
- **ArbiterOS 参考**：ArbiterOS 项目的 `skill_trust.py`（490 行，外部仓库，不在本仓库）调 `cisco-ai-skill-scanner` CLI → `max_severity → trustworthiness`（CRITICAL/HIGH→LOW, MEDIUM→UNKNOWN, LOW/INFO/SAFE→HIGH），按 SKILL.md SHA-256 缓存。**nop 是 Java，不能直接用 Python CLI**——需 Java-native 启发式扫描。

## Goals

- nop 在 skill 加载时能扫描 skill 包内容（SKILL.md + 关联文件）的安全威胁，标记信任级别。
- 信任级别可被 `ISkillProvider` 消费方（如 `SkillResolver`、dispatch 链）查询，用于决定是否加载/限制该 skill。
- Java-native 启发式默认实现（无外部 Python 依赖），SPI 可插拔（未来可接入更强的扫描器）。

## Non-Goals

- **不做 LLM-based skill 安全评估**——启发式（正则/模式匹配）先行，LLM 评估留 successor（与 `ISkillCurator` 的 LLM 评估是不同维度）。
- **不改 dispatch 链消费信任级别的逻辑**——本计划只**生产**信任级别并附加到 SkillModel；是否根据信任级别拒绝/限制 skill 是独立决策（留 successor 或由集成商裁定）。
- **不移植 ArbiterOS 的 `cisco-ai-skill-scanner`**——那是 Python CLI，nop 用 Java-native 启发式。
- **不改 `ISkillCurator`**——curator 评质量，scanner 评安全，两者并存。

## Scope

### In Scope

- `SkillTrustLevel` 枚举（LOW / UNKNOWN / HIGH，与 `ContentOrigin` 的信任语义对齐）
- `ISkillScanner` SPI（输入 skill 包路径/内容 → 输出 `SkillTrustLevel` + 命中威胁列表）
- `HeuristicSkillScanner` 默认实现（正则/模式匹配：注入指令、危险命令、权限提升引导、exfiltration 模式）
- `SkillModel` 新增 `trustLevel` 字段（additive，默认 UNKNOWN，向后兼容）
- `FileSystemSkillProvider` 集成（加载时调 scanner → 设 trustLevel）

### Out Of Scope

- dispatch 链消费 trustLevel 的策略（拒绝 LOW-trust skill 等）——独立 successor
- LLM-based scanner——successor
- skill 包的 sandbox 执行——非本计划
- 缓存机制（ArbiterOS 按 SHA-256 缓存）——首版不缓存，每次加载扫描（性能可接受，skill 加载非热路径）

## Execution Plan

### Phase 1 - SPI + 信任级别模型设计

Status: planned
Targets: `ai-dev/design/nop-ai-agent/skill-system-design.md`（新增 skill 安全扫描章节）

- Item Types: `Decision`

- [ ] 设计 `SkillTrustLevel` 枚举：`LOW`（检测到高危威胁）/ `UNKNOWN`（未检测到但不确定）/ `HIGH`（显式标记为可信或扫描通过且无风险特征）
- [ ] 设计 `ISkillScanner` SPI：`SkillScanResult scan(SkillModel skill, SkillPackageSource source)` → `SkillScanResult`（trustLevel + `List<SkillThreatFinding>` 命中威胁 + scanner 标识）
- [ ] 设计威胁分类（参考 ArbiterOS + nop 既有 `PromptInjectionGuardrail` 的 4 类）：`prompt_injection` / `dangerous_command` / `privilege_escalation` / `exfiltration_guidance`
- [ ] 在 `skill-system-design.md` 新增"Skill 安全扫描"章节，固化 SPI 契约 + 信任级别语义 + 与 `ISkillCurator`（质量）的边界

Exit Criteria:

- [ ] `skill-system-design.md` 新增章节，含 `ISkillScanner` I/O 契约表 + `SkillTrustLevel` 语义表 + 威胁分类表
- [ ] 章节明确区分：scanner（安全）vs curator（质量）——两者并存，不互相替代
- [ ] No owner-doc update required（`skill-system-design.md` 即 owner doc）

### Phase 2 - 启发式 Scanner 默认实现 + SkillModel 扩展

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/`

- Item Types: `Proof`

- [ ] 实现 `SkillTrustLevel` 枚举
- [ ] 实现 `SkillThreatFinding` 值类型（threatClass + matchedPattern + filePath + snippet）
- [ ] 实现 `SkillScanResult` 值类型（trustLevel + findings + scannerName）
- [ ] 实现 `ISkillScanner` 接口 + `NoOpSkillScanner`（pass-through，返回 UNKNOWN——向后兼容默认）
- [ ] 实现 `HeuristicSkillScanner`：扫描 skill 包内文本文件（SKILL.md + .md + .yaml），正则匹配 4 类威胁模式（复用 `PromptInjectionGuardrail` 的正则知识 + 补 dangerous_command/privilege_escalation 模式）；命中高危 → LOW，无命中 → UNKNOWN（保守不升 HIGH）
- [ ] `SkillModel` 新增 `trustLevel` 字段（类型 `SkillTrustLevel`，默认 `UNKNOWN`，向后兼容）

Exit Criteria:

- [ ] `SkillTrustLevel`、`SkillThreatFinding`、`SkillScanResult`、`ISkillScanner`、`NoOpSkillScanner`、`HeuristicSkillScanner` 六个类存在于 main source
- [ ] `SkillModel` 含 `trustLevel` 字段 + getter/setter（默认 UNKNOWN）
- [ ] **无静默跳过**：`HeuristicSkillScanner` 扫描异常时显式返回 `SkillScanResult.error(...)`（非吞异常返回 HIGH）
- [ ] 新增功能测试：`TestHeuristicSkillScanner`（≥ 4 case：注入命中→LOW、危险命令命中→LOW、良性→UNKNOWN、扫描异常→error result）
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过

### Phase 3 - FileSystemSkillProvider 集成

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/FileSystemSkillProvider.java`

- Item Types: `Fix`

- [ ] `FileSystemSkillProvider` 持有 `ISkillScanner` 字段（默认 `NoOpSkillScanner`，经构造器/setter 注入 `HeuristicSkillScanner`）
- [ ] 加载 skill 时调 `scanner.scan(skill, source)` → 将结果 `trustLevel` 设到 `SkillModel.trustLevel`
- [ ] setter 注入时若 scanner 为 null，兜底 `NoOpSkillScanner`（不 NPE）
- [ ] `DefaultAgentEngine` / `ReActAgentExecutor.Builder` 透传 scanner（与既有 `setSkillProvider` 模式一致，additive setter）

Exit Criteria:

- [ ] `FileSystemSkillProvider` 加载 skill 后，`SkillModel.getTrustLevel()` 反映 scanner 结果（非默认 UNKNOWN，当注入了 `HeuristicSkillScanner` 时）
- [ ] **接线验证**：测试断言 `FileSystemSkillProvider.getSkills()` 返回的 `SkillModel` 的 `trustLevel` 确实来自 scanner 调用（用含注入的测试 skill 包 → trustLevel=LOW）
- [ ] **Anti-Hollow**：端到端测试从 skill 文件 → provider 加载 → scanner 调用 → trustLevel 设置 → 消费方可读，全链路连通
- [ ] 向后兼容：不注入 scanner 时，trustLevel 全部为 UNKNOWN（与未加字段前行为等价）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

### Phase 4 - 验收 + 文档同步

Status: planned
Targets: `ai-dev/design/nop-ai-agent/skill-system-design.md`；`ai-dev/design/nop-ai-agent/guardrail-contract.md`；`ai-dev/logs/`

- Item Types: `Proof` | `Follow-up`

- [ ] 端到端验收测试：构造一个含注入指令的测试 skill 包 → `FileSystemSkillProvider`（注入 `HeuristicSkillScanner`）加载 → 断言 `trustLevel=LOW` + findings 非空；构造一个良性 skill 包 → `trustLevel=UNKNOWN`
- [ ] `skill-system-design.md` 补"实现状态"段（shipped 默认 NoOp，`HeuristicSkillScanner` opt-in）
- [ ] `guardrail-contract.md` SPI 分类表补 `ISkillScanner`（partial-with-implementation）
- [ ] `ai-dev/logs/` 更新

Exit Criteria:

- [ ] 端到端验收测试通过（注入 skill → LOW，良性 skill → UNKNOWN）
- [ ] `skill-system-design.md` + `guardrail-contract.md` 已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `ISkillScanner` SPI + `HeuristicSkillScanner` + `SkillModel.trustLevel` + `FileSystemSkillProvider` 集成全部 landing
- [ ] 端到端验证：含注入的 skill 包被标记 LOW-trust
- [ ] 向后兼容：不注入 scanner 时行为不变
- [ ] 不存在被静默降级的 in-scope 项
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow：skill 文件 → provider → scanner → trustLevel 全链路连通
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] checkstyle 通过

## Deferred But Adjudicated

### dispatch 链消费 trustLevel 的策略

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划只生产 trustLevel，消费策略（拒绝 LOW-trust skill / 限制资源 / 要求审批）是独立决策，需设计如何接入 `SecurityCheckpointChain`——属 §14.2 ③ Layer 1 observe-mode 的后续，留 successor。
- Successor Required: yes（待 Layer 1 observe-mode 或独立 skill-gating plan）

### LLM-based scanner

- Classification: `optimization candidate`
- Why Not Blocking Closure: 启发式先行覆盖已知模式；LLM 评估（类似 `LLMCurator`）可捕获启发式漏掉的语义威胁，但成本/延迟高，留 successor。
- Successor Required: no

### 扫描结果缓存

- Classification: `optimization candidate`
- Why Not Blocking Closure: skill 加载非热路径（启动时加载，非每次 tool call），首版不缓存可接受。ArbiterOS 按 SKILL.md SHA-256 缓存的优化留 successor。
- Successor Required: no

## Non-Blocking Follow-ups

- scanner 正则模式库的持续维护（随实战发现新威胁模式补充）
- `--use-llm` 等价物（Java 侧的 LLM 辅助扫描）

## Closure

Status Note: (待 closure 时填写)
Completed: (待 closure)

Closure Audit Evidence:
- (待独立子 agent closure-audit 后填写)
