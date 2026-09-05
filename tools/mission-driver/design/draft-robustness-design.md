# Draft Robustness Design

**Date**: 2026-07-20
**Scope**: `tools/mission-driver/src/main.js` (`cmdDraftMission`), `tools/mission-driver/prompts/mission-brief.md`, `tools/mission-driver/prompts/mission-draft.md`, draft-state schema
**Status**: implemented — §4.1/§4.2/§4.3/§4.4/§4.5 landed via WI1–WI5; see `docs/plans/mission-driver-draft-robustness/`
**Related**: `mission-design.md`, `flow-engine-design.md`
**Origin**: 一次 `draft "d"` 调用产生了 3 个无用文件（`d-brief.md` / `d-roadmap.md` / `tools/mission-driver/missions/d.json`），暴露 draft 管线的 3 个缺陷。

---

## 0. 事件复盘

7 月 14 日 17:35，一次以单字符 `"d"` 为描述的 `draft` 调用产生了：

- `docs/backlog/d-brief.md` — Stage 1 brief 产物
- `docs/backlog/d-roadmap.md` — Stage 2 draft 产物
- `tools/mission-driver/missions/d.json` — Stage 2 draft 产物（mission.json）

brief agent 正确识别出 `"d"` 信息不足，写了一份 gate brief（明确"不得进入实现"）。但 Stage 2 仍无条件执行，照常生成了 roadmap + mission.json；且 mission.json 落在了 `tools/mission-driver/missions/` 而非仓库根 `missions/`。三个产物都没有实际意义，需要人工清理。

这不是孤立的"用户输错"，而是 draft 管线在**输入校验、gate 强制、路径解析**三处都缺位共同造成的。本文档分析根因并给出方案。

> **缺陷 4（分析过程中另行发现）**：`mission-check.mjs:106` 的独立 CLI 入口判断 `import.meta.url === \`file://${process.argv[1]}\`` 在 **Windows 上永久失效**——左边是 `file:///C:/...`、右边 `process.argv[1]` 是 `C:\...` 或相对路径，永不相等 → 独立 CLI 块从不执行 → `node mission-check.mjs <file> <root>` 静默 exit 0 什么都没校验。这本身不是 `d` 事故的成因，但让事后用 `mission-check` 复查时得到**假阳性**（误以为校验通过），掩盖了配置问题。与三个 draft 缺陷同属 mission-driver 工具健壮性，一并修（见 §2.6 / §4.5 / WI5）。

> **缺陷 5（分析 aborted run 时另行发现）**：`engine.js` 的 `_executeSubflowStep` 把 `subflowRuns` 攒在**局部变量**里（`:964`），只在 forEach 全部结束时才通过 return 交给调用方写入主 run-state。父进程中途被杀 → 主 run-state 的 `subflowRuns` 永远是 `_wfOpen`（`:345`）给的初始 `[]`，尽管磁盘上子流程 run-state 文件完好。这叠加 monitor.js:267 旧版的 `status === "running"` gate，造成 dashboard"步骤 1 秒后消失"（monitor 侧已在 commit 06749fa 修固；engine 侧仍需补，见 §2.6 / §4.5 / WI5）。

---

## 1. 现状梳理（关键代码位置）

### 1.1 draft 两段式管线

`cmdDraftMission` 函数体（`src/main.js:317-509`）：

```
Stage 1 (brief)   ──mission-brief.md──▶  docs/backlog/<slug>-brief.md
                     main.js:397-438 (Stage 1 brief block)
                                            (extractBriefPath at :160-164 取出路径)

Stage 2 (draft)   ──mission-draft.md──▶  docs/backlog/<slug>-roadmap.md
                     main.js:458-509 (Stage 2 draft block)
                                            + {{missionsDir}}/<slug>.json
                                            (parseDraftArtifact at :236-307 解析 mission 身份)
```

两阶段之间**没有任何条件分支**——Stage 1 结束直接进 Stage 2（Stage 2 draft block 入口 `main.js:458` 注释 `// ── Stage 2: draft`，无 if 判断）。

### 1.2 路径来源

| 路径 | 来源 | 解析方式 |
|------|------|---------|
| `docs/backlog/<slug>-brief.md` | `mission-brief.md:13,27,30` 字面量 | agent 按 cwd / AGENTS.md 所在地解析（相对） |
| `docs/backlog/<slug>-roadmap.md` | `mission-draft.md:13` 字面量 | 同上（相对） |
| `{{missionsDir}}/<slug>.json` | `mission-draft.md:19` 模板变量 | `cmdDraftMission` Stage 2 draft block 的 `resolveTemplateVars` 调用（`main.js:462`）注入绝对路径（`resolveMissionsDir`，`:80-84`） |

`resolveMissionsDir(opts, projectRoot) = resolve(projectRoot, "missions")`；`projectRoot = opts.dir || process.env.PROJECT_ROOT || process.cwd()`（`resolveProjectRoot`，`:76-78`）。

### 1.3 brief 产物解析

`extractBriefPath`（`main.js:160-164`）用正则 `/<BRIEF_FILE>\s*([^\s<]+)\s*<\/BRIEF_FILE>/i` 从 brief agent 输出里取文件路径。**只取路径，不取任何"是否放行"的语义**。

### 1.4 draft-state

`cmdDraftMission` 在 `--draft-job-dir` 模式下写 `draft-state.json`，由 `writeDraftState(patch)` 闭包（在 `cmdDraftMission` 函数体 `main.js:355-365` 定义；mdr-remediate-3 A1 把定义位置从 reject 分支之后提到之前以避免 TDZ）按 patch 合并多次写入。非 job 模式不写。

`draft-state.json` schema（由 `cmdDraftMission` 多个 `writeDraftState` patch 点合写：`main.js:378-385,397-404,440-445,459,475,483,505-510,520-528`；消费方 `src/monitor.js`）：

| Field | Type | Written by patch point | Notes |
|-------|------|------------------------|-------|
| `status` | `"running" \| "completed" \| "blocked" \| "failed"` | start (`:397-404`) / completion (`:520-528`) / gate blocked (`:475`) / failure (`:440-445`, `:505-510`) / WI1 reject (`:378-385`, mdr-remediate-3 A1) | draft job lifecycle state |
| `phase` | `"brief" \| "brief_done" \| "draft" \| "completed" \| "rejected"` | start (`:401`) / brief done (`:459`) / Stage 2 entry (`:483`) / completion (`:523`) / WI1 input rejection (`:381`, mdr-remediate-3 A1) | coarse progress marker; `"rejected"` is a terminal phase distinct from runtime-failure `"brief"` / `"draft"` (WI1 rejection is pre-Stage-1) |
| `startedAt` | ISO string | start (`:399`) | this process's start time (re-affirms stale state from prior attempt) |
| `endedAt` | ISO string | WI1 reject (`:382`, mdr-remediate-3 A1) / gate blocked (`:475`) / failure (`:442`, `:507`) / completion (`:522`) | terminal timestamp |
| `desc` | string | start (`:400`); preserved by merge across WI1 reject patch (mdr-remediate-3 A1) | the raw draft description argument (for auditability) |
| `flowHint` | string \| null | start (`:402`) | mdo-4 P2 user/wizard-chosen flow name |
| `targetFile` | string \| null | start (`:403`) | mdo-4 P2 brief input — project-relative target file path |
| `briefPath` | string \| null | brief done (`:459`) / completion (`:524`) | resolved brief file path (`extractBriefPath` output, `:160-164`) |
| `briefGate` | `"pass" \| "blocked" \| null` | brief done (`:459`) | WI2 — `extractBriefGate` output (`:184-189`); `null` distinguishes "Stage 1 ran but AI emitted no marker" from "Stage 1 skipped" |
| `briefGateReason` | string \| null | brief done (`:459`) | WI2 — paired with `briefGate`; the `s` (dotall) regex flag at `:187` enables multi-line reasons |
| `missionName` | string \| null | completion (`:525`) | `parseDraftArtifact` output (`:236-307`) |
| `roadmapPath` | string \| null | completion (`:526`) | `parseDraftArtifact` output |
| `missionFile` | string \| null | completion (`:527`) | `parseDraftArtifact` output — the resolved mission.json path |
| `error` | string | WI1 reject (`:383`, mdr-remediate-3 A1) / failure (`:443`, `:508`) | rejection reason (WI1) / agent error message (brief / draft failure) |

`CONTEXT.md` 不重复此 schema（已交叉引用本设计文档）。

---

## 2. 根因分析

### 2.1 缺陷 1：draft 描述无校验

`cmdDraftMission(desc, opts)`（函数体入口 `main.js:317`）对 `desc` 参数**零校验**：

- 无最小长度检查（`"d"` 通过）
- 无空白/占位检查（`" "`、`"test"`、`"asdf"` 通过）
- 无"是否含可执行语义"检查

slug 直接由 AI 从 desc 派生（`mission-brief.md:13`），输入 `"d"` → slug `"d"` → 一整套 `d-*` 文件。命令行层（`program.command("draft").argument("<description>")` Commander 注册，`main.js:844-855`）只保证参数**存在**，不保证**有意义**。

**影响**：任何误输入、测试输入、空白输入都会生成一套文件，污染 `docs/backlog/` 和 `missions/`，需要人工清理。monitor draft-job UI 同样会触发（同一代码路径）。

### 2.2 缺陷 2：brief gate 只在 prompt 层、不在引擎层

`mission-draft.md:7` 明确写：

> The brief is the authoritative scope gate — do not contradict its 非目标.

但这是**给 AI 的文字指令**，引擎完全不感知。`main.js:334+` 无条件进 Stage 2：

```js
// Stage 1 结束（无论 brief 说了什么）
if (opts.draftJobDir) { writeDraftState({ phase: "brief_done", briefPath }); }
// ── Stage 2: draft (roadmap + mission.json) ──
if (opts.draftJobDir) { writeDraftState({ phase: "draft" }); }
const promptFile = resolve(__dirname, "..", "prompts", "mission-draft.md");
// ...直接跑 draft agent...
```

brief agent 在 `d` 事件里其实**判断对了**（写了 gate，说"信息不足、不得进入实现"），但引擎没有读取这个判断，Stage 2 照跑。

> 补充：draft agent 在**内容层**部分尊重了 gate（`d-roadmap.md` 只建了一个"先澄清范围"的 WI，没编造实现）。所以 gate 并非完全失效，只是"是否生成文件"这一层没有引擎级强制。

**影响**：brief 的"gate"语义强弱完全取决于 AI 是否听话，不可预测、不可测试。用户无法依赖 brief 拦住垃圾输入。

### 2.3 缺陷 3：路径双轨制（split-brain）

draft 管线里路径有两种来源，解析基准不同：

| 类型 | 例子 | 解析基准 | 谁定 |
|------|------|---------|------|
| 模板变量 | `{{missionsDir}}` | `projectRoot`（绝对） | `main.js` 注入 |
| 字面量 | `docs/backlog/...` | agent cwd / AGENTS.md 所在地（相对） | agent 自己 |

当 `projectRoot ≠ 仓库根` 时（如本次 `cwd = tools/mission-driver/`），两者发散：

- `{{missionsDir}}` = `<repo>/tools/mission-driver/missions`（绝对，按 projectRoot）→ `d.json` 落这里
- `docs/backlog/` → agent 按 AGENTS.md 所在地（仓库根）解析 → brief/roadmap 落仓库根

brief agent 自己也观察到了这个分歧（`d-brief.md:31`："Project root 指向 `tools/mission-driver`"），但引擎没有据此对齐。

**影响**：
- mission.json 与 brief/roadmap 落在不同根下，monitor 的 `GET /api/configs` 扫 `{projectRoot}/missions/*.json` 会漏掉放错位置的 mission.json（本次 `d.json` 不在仓库根 `missions/`，`list` 命令也看不到它）。
- 后续 `run` 该 mission 时，`resolveConfig` 按 mission 文件路径找，但 brief/roadmap 路径是相对的，可能找不到。
- 用户难以预期文件去向。

### 2.4 三缺陷的耦合

三个缺陷在 `d` 事件里**叠加**才造成"3 个无用文件 + 位置错乱"：

1. 缺陷 1 放行了 `"d"`（本应在校验层拦下）。
2. 缺陷 2 让 brief 的 gate 判断没能阻止 Stage 2（本应在 brief 层拦下）。
3. 缺陷 3 让产物散落到错误位置（本应在路径层一致）。

任何一层到位都能避免或减轻本次事故。三者相互独立，应分别修。

### 2.5 缺陷 4：mission-check.mjs 独立 CLI 在 Windows 静默失效

`mission-check.mjs:106` 用如下判断决定是否运行独立 CLI 主体：

```js
if (import.meta.url === `file://${process.argv[1]}`) { ... }
```

- 左侧 `import.meta.url`：Node 规范化为 `file:///C:/Work/.../mission-check.mjs`（file URL，正斜杠，三斜杠前缀 + drive letter）。
- 右侧 `` `file://${process.argv[1]}` ``：`process.argv[1]` 在 Windows 是 `C:\Work\...\mission-check.mjs`（反斜杠）或调用时传入的相对路径（如 `tools/mission-driver/src/mission-check.mjs`）。拼成 `file://C:\Work\...` 或 `file://tools/...`，既不是合法 file URL，也与左侧的规范化形式不等。

两者在 Windows **永远** `!==` → CLI 主体（`:106-118` 的 `if` 块）从不执行 → 脚本不报错、不输出、走完模块顶层、exit 0。`&&` 链无法发现这个 no-op，下游照常执行。

**影响**：

- `node mission-check.mjs <file> <root>` 在 Windows 是一个**假阳性机器**——任何 mission（哪怕 `plansDir` 不存在）都"通过"。
- macOS / Linux 上 `process.argv[1]` 是 `/abs/path/mission-check.mjs`，拼成 `file:///abs/path/...` 与 `import.meta.url` 相等，CLI 正常。所以这是一个**平台相关缺陷**，在非 Windows 开发者机器上不会被察觉，CI 若跑在 Linux 上也测不出来。
- `run` / `list` 等子命令**不受影响**——它们走 `config.js:499` 的 `loadMission(file, projectRoot)`（import 进来当函数调用），不走 `import.meta.url` 入口判断。所以 `run` 能正常报"plansDir does not exist"，而紧挨着的 `mission-check.mjs` 独立 CLI 却说没问题——两者口径不一致，更易误导。

### 2.6 缺陷 5：subflowRuns 不增量落盘（aborted run 子流程历史丢失）

`_executeSubflowStep`（`engine.js:941`）的 forEach 路径里，`subflowRuns` 是一个**局部变量**（`:964` `const subflowRuns = []`）。每个子流程完成后 `push` 一条（`:983` / `:1013`），但这个数组只在 forEach 全部结束、函数 return 时（`:1058`）才随结果对象交给调用方，再由 `_wfClose` 写进主 run-state.json 的 step 记录。

如果父进程在 forEach **中途**被杀（崩溃 / SIGKILL / 机器睡眠后未醒），主 run-state.json 里该 subflow step 的 `subflowRuns` 永远是 `_wfOpen`（`:345`）给的初始值 `[]`——尽管每个已完成的子流程都已把自己的 run-state 写到磁盘（`run-state-<stepName>-<visit>-<i>.json`，由子引擎自己的 `_writeWorkflow` 落盘）。

**影响**：

- 主 run-state.json 的 `subflowRuns: []` 不能反映真实进度。直读 run-state 的消费方（`--analyze` 复盘、`git show` 后人工看 run-state、任何不经过 monitor merge 的工具）看不到子流程历史。
- monitor 的 `mergeSubflowChildren` 旧版有 `step.status === "running"` gate（已在 commit 06749fa 移除），叠加空 `subflowRuns` 造成 dashboard"1 秒后消失"。monitor 侧已修，但 run-state.json 本身仍不完整。
- 与缺陷 1-4 不同，这是**执行引擎**层的问题（不是 draft 管线），但同属"工具健壮性"，且是在分析本次 aborted run 时发现的，一并记录。

**注意**：monitor 侧的渲染修复（commit 06749fa）已让 dashboard 对历史 aborted run 恢复显示子流程步骤。本缺陷（§4.5 / WI5）的剩余价值是让 run-state.json **自包含**——不依赖 monitor 的 fallback 扫描就能反映子流程进度。

---

## 3. 设计目标与约束

### 3.1 目标

1. **G1 — 输入校验**：`draft` 命令在 CLI 层拒绝明显无意义的描述（空、过短、纯占位），给出清晰报错。
2. **G2 — gate 引擎化**：brief 的"是否放行"判定从 prompt 文字升级为结构化 marker，引擎据 marker 决定是否进 Stage 2。
3. **G3 — 路径一致**：draft/brief 管线里所有文件路径统一走模板变量（绝对、projectRoot 锚定），消除字面量相对路径。
4. **G4 — 可观测**：draft-state 记录 gate 决策（pass/blocked + reason），无论是否 job 模式。
5. **G5 — 向后兼容**：旧调用方式（`--skip-brief` 单段式）不受影响；无 draft-state 的旧 run 不受影响。
6. **G6 — 校验工具跨平台可用**：`mission-check.mjs` 独立 CLI 在 Windows / macOS / Linux 上都能真正执行校验（消除假阳性）。
7. **G7 — 子流程进度自包含**：forEach 子流程每完成一项就增量写入主 run-state.json 的 `subflowRuns`，父进程中途崩溃时 run-state 仍反映已完成项（不依赖 monitor fallback）。

### 3.2 约束 / 不变性

- 引擎核心保持**零 npm 依赖**。
- 不改变 draft 两段式的外部调用接口（`draft <desc>` CLI 不变）。
- 不引入新的强制人工步骤（gate blocked 时只是"不进 Stage 2 + 报错"，不阻塞其它工作）。
- 与正在做的 `mission-driver-step-audit` 优化**独立**（不依赖 audit 计数、不依赖 `--from-step`），可并行实施。

### 3.3 非目标

- 不重构 `cmdDraftMission` 的整体结构。
- 不改 `run` / `draft` 之外的子命令。
- 不做"AI 判断描述是否有意义"的语义校验（那不可测；只用确定性的长度/空白规则 + brief gate）。

---

## 4. 解决方案

### 4.1 方案 A（缺陷 1）：CLI 层描述校验

在 `cmdDraftMission` 入口（`main.js:244` 之后、Stage 1 之前）加一段确定性校验：

```js
function validateDraftDesc(desc) {
  const trimmed = String(desc ?? "").trim();
  if (trimmed.length === 0) {
    return { ok: false, reason: "description is empty" };
  }
  if (trimmed.length < 4) {
    return { ok: false, reason: `description too short (${trimmed.length} chars); need at least a phrase describing the mission goal` };
  }
  // 纯占位检测：test / asdf / todo / xxx / 单字符重复
  if (/^(test|asdf|foo|bar|todo|xxx|none|null|n\/a)$/i.test(trimmed)) {
    return { ok: false, reason: `description looks like a placeholder ("${trimmed}")` };
  }
  return { ok: true };
}
```

> **Deviation note (A6, implementation vs design)**：`src/main.js:207-220` 的实际 `validateDraftDesc` 实现采用 `empty → placeholder → length` 顺序，与本节代码块的 `empty → length → placeholder` 不同。原因记录在 `main.js:199-204` 的 JSDoc：design 的顺序让 3 字符黑名单条目（`xxx` / `foo` / `bar` / `n/a`）永远不可达——它们总是先被 `length < 4` 拦下。改为 placeholder 优先让黑名单实际生效（`"xxx"` → "looks like a placeholder" 是比 `"too short"` 更可操作的拒绝原因）。语义等价（三规则都是 reject）；测试覆盖（`draft-desc-validate.test.js`）锁住实现顺序。

阈值 `4` 是经验值：有意义的目标至少是一个词组（"add X"、"fix Y"）。可配置（base.json 加 `draft.minDescLength`，默认 4），但不强求。

校验失败时：打印 reason + hint（"draft 需要一句描述目标的话；示例：draft '为 mission-driver 增加 audit 计数'"），`process.exitCode = 1`，不进 Stage 1。

> **Reject-branch state write (mdr-remediate-3 A1, implementation vs prior WI1 closure)**：When invoked with `--draft-job-dir`, the WI1 reject branch writes `{status: "failed", phase: "rejected", endedAt, error: <reason>}` to `draft-state.json` BEFORE exit; the initial `desc` written by `startDraftJob` is preserved by `writeDraftState`'s merge semantics. `phase: "rejected"` is a new terminal phase, distinct from existing `phase: "brief"` / `"draft"` runtime-failure phases, because WI1 input rejection is pre-Stage-1 (no brief / draft agent has run). This closes the A1 stuck-running failure mode: without this write, `startDraftJob`'s initial `status: "running"` would persist forever because the child's stderr is `stdio: "ignore"`-discarded by the parent and `run-reconcile` does not cover `draft-state.json`. The direct CLI path (no `--draft-job-dir`) still exits 1 without writing any state file — the WI1 contract for direct CLI invocation is unchanged. See plan `docs/plans/mission-driver-draft-robustness/2026-07-21-1005-3-stuck-running-draft-state-remediation.md`.

**为何不做语义校验**：让 AI 判断"这段描述是否有意义"不可测试、不可复现。确定性规则（长度 + 黑名单）能拦住本次 `"d"` 这类明显垃圾，又不会误伤正常输入。剩下的"语义是否充分"交给 brief gate（方案 B）。

### 4.2 方案 B（缺陷 2）：brief gate marker 契约 + 引擎强制

#### 4.2.1 扩展 brief marker 契约

`mission-brief.md` 当前只要求输出 `<BRIEF_FILE>...</BRIEF_FILE>`（`:30`）。新增一个 gate marker：

```
<BRIEF_FILE>docs/backlog/<slug>-brief.md</BRIEF_FILE>
<BRIEF_GATE>pass|blocked</BRIEF_GATE>
<BRIEF_GATE_REASON>一句话说明（blocked 时必填）</BRIEF_GATE_REASON>
```

gate 判定规则写进 prompt：
- `pass`：描述足以推导出目标 / 范围 / 产物（哪怕粗粒度）。
- `blocked`：描述信息不足（如裸关键词、纯占位、无目标模块、无验收标准），无法安全生成 roadmap + mission.json。

#### 4.2.2 引擎解析与强制

在 `main.js` 加一个 `extractBriefGate`（镜像 `extractBriefPath`，`:160-164`）：

```js
function extractBriefGate(resultText) {
  if (typeof resultText !== "string") return { gate: null, reason: null };
  const m = resultText.match(/<BRIEF_GATE>\s*(pass|blocked)\s*<\/BRIEF_GATE>/i);
  const r = resultText.match(/<BRIEF_GATE_REASON>\s*(.+?)\s*<\/BRIEF_GATE_REASON>/is);
  return { gate: m ? m[1].toLowerCase() : null, reason: r ? r[1] : null };
}
```

> **Regex flag (F3)**：`BRIEF_GATE_REASON` 正则用 `/is` 标志——`i` 大小写不敏感、`s` (dotall) 让 `.` 匹配换行符，使 reason 可以跨多行（AI 经常写出"原因 + 影响范围 + 建议"三段式）。多行分支的测试覆盖在 `2026-07-21-1005-2-verification-and-contract-hardening.md` A5。

Stage 1 之后（`main.js:332` 附近），加分支：

```js
const { gate, reason } = extractBriefGate(briefResult.text);
writeDraftState({ phase: "brief_done", briefPath, briefGate: gate, briefGateReason: reason });

if (gate === "blocked") {
  console.log(`\n[BRIEF GATE] blocked: ${reason || "(no reason)"}`);
  console.log(`Brief written to ${briefPath}. Resolve the open questions there, then re-run draft.`);
  // 不进 Stage 2，不写 mission.json / roadmap
  if (opts.draftJobDir) writeDraftState({ status: "blocked", endedAt: new Date().toISOString() });
  await runner.close();
  return;  // process.exitCode 0（这不是错误，是 gate 正常工作）
}
// 仅 gate === "pass" 或 null（旧 brief 无 marker，向后兼容）才进 Stage 2
```

**向后兼容**：`gate === null`（brief 没输出 marker，旧习惯）时，退化为当前行为（继续 Stage 2）。这样不会破坏已有 brief 写法。但 prompt 升级后，新 brief 都会带 marker。

#### 4.2.3 draft-state 扩展

`draft-state.json` 增加 `briefGate` / `briefGateReason` 字段（`:270-289` 的 `writeDraftState` patch 自然继承）。monitor draft-job UI 据此显示 gate 状态（pass / blocked + reason），而不是只能看 `phase`。

### 4.3 方案 C（缺陷 3）：路径统一走模板变量

#### 4.3.1 注入新变量

`main.js:301`（brief 模板渲染）和 `:340`（draft 模板渲染）的 `resolveTemplateVars` 调用，新增：

```js
resolveTemplateVars(rawPrompt, {
  missionsDir: resolved.missionsDir,
  projectRoot: resolved.projectRoot,
  backlogDir: resolve(resolved.projectRoot, "docs/backlog"),   // 新增
  briefPath: briefPath || "",
  flowHint: resolved.flowHint || "",
});
```

> **plansRoot omitted (N4 fix, mdr-remediate-1)** — the original design draft listed a peer `plansRoot: resolve(resolved.projectRoot, "docs/plans")` here. WI3 deliberately did **not** ship it: no prompt currently references `{{plansRoot}}`, so injecting it would be dead code. See `docs/logs/2026/07-21.md` WI3 entry ("未引入 `plansRoot` 模板变量 … 预添加属 dead code"). The remaining vars match `src/main.js:423-429` (Stage 1 brief render) and `src/main.js:487-493` (Stage 2 draft render).

#### 4.3.2 prompt 改造

`mission-brief.md`：把所有字面量 `docs/backlog/<slug>-brief.md` 换成 `{{backlogDir}}/<slug>-brief.md`（`:13,27,30` 三处）。

`mission-draft.md`：
- `:13`：一行内**三处**字面量 `docs/backlog/` 全部替换为 `{{backlogDir}}/`：`docs/backlog/{mission-name}-roadmap.md`（存在性检查）、`docs/backlog/00-roadmap-authoring-guide.md`（编写指南引用）、`docs/backlog/{mission-name}-roadmap.md`（保存目标）。早期版本遗漏了 `00-roadmap-authoring-guide.md` 这一处——它会被 grep 锚点（test/draft-path-consistency.test.js Case E）抓出，故一并替换。
- `:19`：`{{missionsDir}}/{mission-name}.json`（已经是变量，不变）

这样 brief / roadmap / mission.json **全部**按 `projectRoot` 解析，基准统一。

#### 4.3.3 产物路径校验（防御性）

`parseDraftArtifact`（`main.js:180-234`）在 `<MISSION_FILE>` 命中后，加一段校验。**用 `path.relative + startsWith("..")` 而非字符串 `startsWith`**（避免 `/foo/bar` 前缀误匹配 `/foo/barbaz` 这类边界，以及 Windows drive-letter 大小写歧义）：

```js
import { relative, isAbsolute } from "node:path";  // 与 resolve/dirname 同 module
// ... parseDraftArtifact 内 <MISSION_FILE> 命中分支，out.missionFile 赋值之后：
const rel = relative(resolve(missionsDir), resolve(dirname(file)));
if (rel.startsWith("..") || isAbsolute(rel)) {
  process.stderr.write(
    `[WARN] mission.json landed outside expected missionsDir: ` +
    `got ${file}, expected under ${resolve(missionsDir)}. ` +
    `This usually means projectRoot / cwd mismatch.\n`
  );
}
```

不强制失败（agent 可能合理地放到项目级 `missions/`），但打 warn 让用户意识到位置异常。配合 4.3.2 的路径统一，正常情况下不会再触发。

#### 4.3.4 关于 `projectRoot ≠ 仓库根` 本身

不做强制对齐（即不把 projectRoot 自动改写成仓库根）。从子模块目录发起 draft 是合法用法（module-scoped mission）。只要路径基准统一（方案 C 主体），产物就会一致地落在该 projectRoot 下，monitor / list 按 projectRoot 找也能找到。本次事故的根因不是"projectRoot 错了"，而是"两种基准混用"。

### 4.4 方案 D（缺陷 4）：修固 mission-check.mjs 跨平台 CLI 入口

#### 4.4.1 入口判断改用 pathToFileURL

`mission-check.mjs:106` 改为：

```js
import { pathToFileURL } from "node:url";
// ...
if (import.meta.url === pathToFileURL(process.argv[1]).href) { ... }
```

`pathToFileURL` 把任意平台路径（Windows 反斜杠、相对路径、带 drive letter）规范成与 `import.meta.url` 同形的 `file:///` URL，三平台一致。

> 这是 Node 官方推荐的"是否作为主模块运行"判法，等价于 `import.meta.main` 提案落地前的标准模式，零依赖（`node:url` 内置）。

#### 4.4.2 替代：run-check 子命令

另一种做法是删掉独立 CLI 入口，把校验收进 `main.js` 的 commander 体系，新增 `mission-driver check <mission>` 子命令。好处是入口统一（都走 commander，不依赖 `import.meta.url` 判断）、可在所有平台一致暴露。坏处是改动面更大（新增子命令 + 帮助文本），且 `mission-check.mjs` 作为"纯函数模块 + 可独立运行"的既有形态被打破。

**推荐 4.4.1**（最小改动、直击根因）。若后续 `mission-check` 需要更多 CLI 能力（如 `--fix`、`--strict`），再升级到 4.4.2 的子命令形态。

#### 4.4.3 测试锚点

加一个 `mission-check-cli.test.js`：用 `child_process.spawnSync('node', ['mission-check.mjs', <bad-mission>, '.'])` 在 Windows 与非 Windows 上都跑一遍，断言 exit code 为 1 且 stderr 含 "does not exist"。这能锁住"独立 CLI 真的执行了校验"这一不变性，防止回归。

### 4.5 方案 E（缺陷 5）：subflowRuns 增量落盘

#### 4.5.1 复用 `_onAgentStepUpdate` 的"找当前 running 记录并 patch"模式

`engine.js:415-424` 的 `_onAgentStepUpdate` 已经实现了"在 `workflow.steps` 里按 name+status==="running" 找到当前步记录、patch 字段、`_writeWorkflow()`"的模式（用于流式更新 logFile/promptFile/sessionId）。WI5 镜像这个模式加一个 `_wfAppendSubflowRun(stepName, visits, run)`：

```js
_wfAppendSubflowRun(stepName, visits, run) {
  if (!this.workflow) return;
  const steps = this.workflow.steps;
  for (let i = steps.length - 1; i >= 0; i--) {
    if (steps[i].name === stepName && steps[i].visits === visits && steps[i].status === "running") {
      if (!Array.isArray(steps[i].subflowRuns)) steps[i].subflowRuns = [];
      steps[i].subflowRuns.push(run);
      this.workflow.updatedAt = new Date().toISOString();
      this._writeWorkflow();
      return;
    }
  }
}
```

#### 4.5.2 在 forEach 每项完成后调用

`_executeSubflowStep` 的 concurrency=1 路径（`:983`）和 sliding-window 路径（`:1013`）现有 `subflowRuns.push(...)` 之后，立即追加一次增量落盘：

```js
subflowRuns.push({ forEachIndex: i, forEachItem: item, file: ..., status: childResult.status });
this._wfAppendSubflowRun(stepName, visit, subflowRuns[subflowRuns.length - 1]);
```

sliding-window 路径在 `recordResult`（`:1011`）里同样追加。因为 `subflowRuns.sort(...)`（`:1050`）只在并发路径结尾重排，增量写时顺序可能是 resolve 序而非 forEachIndex 序——可接受（最终 return 时局部 `subflowRuns` 仍会 sort，且 `_wfClose` 用最终顺序覆盖；增量期间的临时乱序只影响实时观察，不影响最终记录）。

#### 4.5.3 为什么不只在 `_wfClose` 写

`_wfClose` 只在 step 正常结束时被调用（`run()` 循环里 transition 之后）。父进程中途被杀时 `_wfClose` 根本没机会跑 → 主 run-state 永远停在 `_wfOpen` 的初始 `[]`。增量写是唯一能在"父进程随时可能死"的前提下保证已落盘的方法。代价是每完成一个子流程多一次原子写（tmp+rename），开销可忽略（子流程本身是分钟级，多一次 ms 级文件写无感）。

#### 4.5.4 测试锚点

`subflow-incremental.test.js`：mock 一个 forEach=3 的 subflow step，在第 2 项完成后**模拟父进程状态丢失**（直接读磁盘 run-state.json，不再调 engine 的后续方法），断言 `subflowRuns.length === 2` 且两条记录的 file/status 正确。这锁住"增量落盘"的不变性。

#### 4.5.5 非 forEach 分支的扩展（mdr-remediate-4）

WI5 的初始实现只覆盖 forEach 分支。生产 `DEEP_AUDIT` step（`flows/mission-driver.json`）是非 forEach 的单子流程：`_executeSubflowStep` 的非 forEach 分支（`engine.js:1115-1121` 区域）原本只 `await _runChildSubflow` 后由 `_wfClose` 写最终态，**子流程执行期间主 run-state.json 的 placeholder 仍是初始 `subflowRuns: []`** —— 父进程中途被杀则丢失"在跑"信号。

mdr-remediate-4 后扩展到非 forEach 分支：`_executeSubflowStep` 在 `await _runChildSubflow` **之前**对 forEach 与非 forEach 两条路径都先调 `_wfAppendSubflowRun(... { status: "running", forEachIndex: 0, ... })` 写一个 pre-run placeholder，子流程返回后由 `_wfClose` 用终态覆盖该 placeholder（matched by `name + visits + status==="running"`，无重复、无残留 running）。`_wfAppendSubflowRun` 的 no-op safety（无匹配 placeholder 时静默 return）对两条路径都成立。

§2.6 的"run-state.json 自包含"目标至此对**所有 subflow 类型**都成立，不再只限 forEach。monitor 的 `mergeSubflowChildren` fallback（commit `06749fa`）仍保留作为"孤儿 child"的 defense-in-depth（producer 根本没机会写 placeholder 的极端情形），但常规路径已不再依赖它。

---

## 5. 实施影响与风险

### 5.1 代码改动清单（仅评估，不实施）

| 文件 | 改动 | 风险 |
|------|------|------|
| `src/main.js` `cmdDraftMission` 入口 (:244) | 加 `validateDraftDesc` 调用 | 低；纯前置校验，不改主流程 |
| `src/main.js` Stage 1 后 (:332 附近) | 加 `extractBriefGate` + blocked 分支 | 中；改变 Stage 2 进入条件，需测试 gate=pass/blocked/null 三分支 |
| `src/main.js` `extractBriefGate` 新增 | 镜像 `extractBriefPath` | 低 |
| `src/main.js` 模板渲染 (:301, :340) | 注入 `backlogDir` 变量 | 低 |
| `src/main.js` `parseDraftArtifact` (:180) | 加路径校验 warn | 低；只打 warn 不改返回 |
| `prompts/mission-brief.md` (:13,27,30) | `docs/backlog/` → `{{backlogDir}}/`；加 `<BRIEF_GATE>` marker 要求 | 中；prompt 改动需验证 AI 稳定输出 gate marker |
| `prompts/mission-draft.md` (:13) | `docs/backlog/` → `{{backlogDir}}/` | 低 |
| `missions/base.json` | （可选）加 `draft.minDescLength: 4` | 低；有默认值 |
| `src/mission-check.mjs` (:106) | 入口判断 `` import.meta.url === `file://${process.argv[1]}` `` 改用 `pathToFileURL(process.argv[1]).href` | 低；一行替换，零新依赖（`node:url` 内置） |
| `src/engine.js` `_executeSubflowStep` (:941) + 新增 `_wfAppendSubflowRun` | forEach 每项完成后增量追加 subflowRuns 到主 run-state（镜像 `_onAgentStepUpdate` 模式） | 中；并发路径增量顺序非 forEachIndex（最终 sort 在 return 时修正）；每项多一次原子写 |
| `src/monitor.js` `mergeSubflowChildren` (:267) | （已修）移除 `status === "running"` gate，fallback 扫描磁盘文件不再受 status 限制 | 已完成，commit 06749fa |
| 新增测试 | `draft-desc-validate.test.js`、`brief-gate.test.js`（mock runner 注入 blocked/pass/null 三种输出）、`draft-path-consistency.test.js`、`mission-check-cli.test.js`（spawnSync 验证独立 CLI 真执行校验）、`subflow-incremental.test.js`（mock 父中断，断言已完成的 subflowRuns 已落盘） | — |

### 5.2 关键风险

| 风险 | 对策 |
|------|------|
| AI 不稳定输出 `<BRIEF_GATE>` marker | `gate === null` 时退化为旧行为（继续 Stage 2），不强制阻塞；prompt 里给正面+反面例子。可叠加 `extractTagTolerant` 风格的容错正则。 |
| 描述校验阈值误伤合法短描述 | 阈值设为 4 字符（覆盖 `"add X"` 级别），并通过 base.json 可配置；黑名单只列明显占位词。 |
| `{{backlogDir}}` 注入后，旧 prompt 缓存仍用字面量 | prompt 是运行时读取（`readFileSync`，无缓存），同批发布即一致。 |
| monitor draft-job UI 未识别 `briefGate` 字段 | `draft-state.json` 是 patch 合并（`:271-279`），新字段对旧 UI 透明（忽略未知字段）；UI 升级非阻塞。 |

### 5.3 向后兼容

- `--skip-brief`（单段式 draft，`main.js:248`）：跳过 Stage 1，gate 机制不介入，行为不变。
- 旧 brief 无 `<BRIEF_GATE>` marker：`gate === null` → 继续 Stage 2（旧行为）。
- 旧 draft-state.json 无 `briefGate` 字段：读取时 `?? null`，UI 显示"未知/旧格式"。

---

## 6. 备选方案与为何不选

### 6.1 备选 1：用 AI 判断描述是否有意义

想法：在 Stage 0 跑一个轻量 agent 判断 desc 是否可执行。

**不选**：不可测试、不可复现、增加一次模型调用。确定性规则（方案 A）+ brief gate（方案 B，本就有 AI 参与）已覆盖。"是否可执行"本就是 brief 的职责，不必再加一层。

### 6.2 备选 2：brief gate 用文件内容 grep 而非 marker

想法：Stage 2 前读 brief 文件，正则找"信息不足"等关键词。

**不选**：依赖 brief 的中文/英文措辞，脆。结构化 marker（方案 B）显式、可测、语言无关。

### 6.3 备选 3：强制 projectRoot = 仓库根

想法：向上找 `.git`，把 projectRoot 对齐到仓库根。

**不选**：破坏从子模块发起 draft 的合法用法；且 `--dir` 的语义会被覆盖。根因是基准混用（方案 C 已修），不是 projectRoot 取值。

### 6.4 备选 4：Stage 2 完全去掉，draft 只产 brief

想法：draft 命令只到 brief，mission.json 由人工或后续命令生成。

**不选**：改变两段式 draft 的既有契约（`mission-design.md` 已记录），破坏向后兼容。gate（方案 B）已能在 brief 阶段拦下不合理输入，不必砍 Stage 2。

---

## 7. 验证策略（实施后）

1. **单元测试**
   - `draft-desc-validate.test.js`：`""` / `" "` / `"d"` / `"test"` / `"asdf"` → reject；`"add audit count"` → pass。
   - `brief-gate.test.js`：mock runner 分别返回 `gate=pass` / `gate=blocked` / `gate=null`（无 marker），断言 Stage 2 执行 / 不执行 / 执行（兼容）。
   - `draft-path-consistency.test.js`：设 `projectRoot = tools/mission-driver`，跑 draft，断言 brief / roadmap / mission.json **全部**落在 `tools/mission-driver/{docs/backlog,missions}/` 下（基准一致）。
   - `mission-check-cli.test.js`：`spawnSync` 跑 `node mission-check.mjs <bad-mission> .`，断言 exit 1 + stderr 含 "does not exist"（锁住独立 CLI 真执行校验，防 Windows 静默失效回归）。
2. **回归**：现有 `draft-brief.test.js`（`main.js:24` 提到的 testability seam）应继续通过（`--skip-brief` 路径不变）。
3. **人工**：跑 `draft "d"`，确认被缺陷 1 拦下；跑 `draft "优化 draft 健壮性"`，确认 brief 输出 gate marker、Stage 2 正常。

---

## 8. 实施顺序建议

五个方案相互独立，可任意顺序。按"拦住本次事故 + 恢复校验可信度"的优先级：

1. **Step 1（方案 D）**：修固 `mission-check.mjs` 跨平台入口。最小改动（一行），立即让校验工具恢复可信，后续所有 WI 的"校验通过"才真的算数。
2. **Step 2（方案 A）**：CLI 描述校验。挡住所有"明显垃圾"输入。
3. **Step 3（方案 C）**：路径统一。消除位置错乱。
4. **Step 4（方案 B）**：brief gate marker + 引擎强制。风险最高（改 Stage 2 进入条件 + prompt 契约），放最后配齐测试。
5. **Step 5（方案 E）**：subflowRuns 增量落盘。monitor 侧渲染已修（06749fa），此项让 run-state.json 自包含，`--analyze` 等直读消费方也能看到子流程进度。

---

## 9. 一页摘要

- **缺陷 1（输入无校验）**：`cmdDraftMission`（`main.js:244`）对 desc 零校验，`"d"` 一路通过 → 方案 A：CLI 层长度/占位校验。
- **缺陷 2（gate 不强制）**：`mission-draft.md:7` 声明 brief 是 gate，但 `main.js:334+` 无条件进 Stage 2 → 方案 B：brief 输出 `<BRIEF_GATE>pass|blocked` marker，引擎据 marker 决定是否继续。
- **缺陷 3（路径双轨）**：`{{missionsDir}}` 绝对解析 vs prompt 里 `docs/backlog/` 字面量相对解析，projectRoot ≠ 仓库根时发散 → 方案 C：所有路径走 `{{backlogDir}}` 等模板变量 + 产物路径校验 warn。
- **缺陷 4（校验 CLI Windows 失效）**：`mission-check.mjs:106` 的 `import.meta.url === \`file://${process.argv[1]}\`` 在 Windows 永不相等，独立 CLI 静默 no-op、exit 0，给假阳性 → 方案 D：改用 `pathToFileURL(process.argv[1]).href` 比较。
- **缺陷 5（subflowRuns 不增量落盘）**：`engine.js:964` 把 subflowRuns 攒在局部变量、只在 forEach 结束 return 时写主 run-state，父进程中途被杀则永远 `[]` → 方案 E：镜像 `_onAgentStepUpdate` 模式，每项完成后增量追加并 `_writeWorkflow`。（monitor 侧渲染已修，commit 06749fa。）
- 五者独立、可并行；与 `mission-driver-step-audit` 优化无依赖。
