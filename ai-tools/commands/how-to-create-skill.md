# OpenCode Skill 使用指南（提示词如何触发）

本文档说明：OpenCode 的 **skill** 是什么、如何被发现（discovery）、以及最重要的——**用户提示词如何更可靠地触发 agent 加载某个 skill**。

> skill 不会自动执行。
> skill 只有在 agent 选择调用原生工具 `skill({ name: "..." })` 时才会被加载。

---

## “触发 skill”到底指什么

在 OpenCode 中，skill 是存放在 `SKILL.md` 里的可复用“指令包/流程指南”。

当 tool 列表被构建时：

- agent 会在 `skill` 工具的描述里看到 `<available_skills>`（包含每个 skill 的 `name` 与 `description`）
- 当你的请求与某个 skill 的 `description`/用途高度匹配时，agent **可能**会主动调用：
  - `skill({ name: "<skill-name>" })`
- 该工具会读取对应的 `SKILL.md`，并把正文内容注入到 agent 当前上下文里

因此，“触发 skill”通常意味着：

1. skill 文件存在且能被扫描到（discoverable）
2. 权限允许该 agent 访问该 skill（否则会被隐藏或需要确认）
3. 你的提示词让 agent 判断：**加载这个 skill 是下一步最合理的动作**

---

## `SKILL.md` 放在哪里

OpenCode 会从这些位置发现 skills（项目级 + 全局）：

- 项目（OpenCode）：`.opencode/skill/<name>/SKILL.md`
- 全局（OpenCode）：`~/.config/opencode/skill/<name>/SKILL.md`
- 项目（Claude 兼容）：`.claude/skills/<name>/SKILL.md`
- 全局（Claude 兼容）：`~/.claude/skills/<name>/SKILL.md`

注意：

- 文件名必须是全大写：`SKILL.md`
- 项目级发现会从当前工作目录向上查找直到 git worktree（沿途的 `.opencode/` 与 `.claude/` 都可能被扫描）

---

## `SKILL.md` 最小结构

每个 `SKILL.md` 都必须以 YAML frontmatter 开头（否则会被跳过或加载失败）。

### 最小示例

创建 `.opencode/skill/git-release/SKILL.md`：

```markdown
---
name: git-release
description: Create consistent releases and changelogs
---

## 我会做什么

- Draft release notes from merged PRs
- Propose a version bump
- Provide a copy-pasteable release command

## 什么时候用我

Use this when you are preparing a tagged release.
Ask clarifying questions if the versioning scheme is unclear.
```

### frontmatter 字段要求

OpenCode 在 discovery 阶段会读取并要求：

- `name`（必填）
- `description`（必填）

现有文档也提到一些可选字段（即使写了，也不影响 discovery 的最小成功条件）：

- `license`（可选）
- `compatibility`（可选）
- `metadata`（可选，string-to-string map）

> 说明：当前代码在技能列表里只使用 `name` 与 `description`；其它字段不会出现在 `<available_skills>`，但仍可用于你们自己的约定或在正文里解释。

---

## 用户提示词如何触发 skill

### 核心原则（让 agent 更愿意调用 `skill` tool）

为了提高触发概率，你的提示词需要让 agent 满足两个判断：

1) “有一个 skill 很可能正好覆盖这个任务”

2) “现在就应该加载它，而不是直接开始写代码/回答”

实践上可以这么做：

1. **直接点名 skill 名称**（最稳）
2. 不知道名称时，**复用 skill description 的关键词**，并明确要求“从可用 skills 里选择并加载”
3. 强调你要的是**可复用流程/清单/步骤**（这类需求与 skill 的定位最匹配）

### 高命中提示词模式（推荐直接复制）

#### 模式 A：点名加载（name-based，最可靠）

当你知道 skill 的 `name`：

- “请**加载** `git-release` 这个 skill，并按它的流程帮我准备本仓库的 release。”
- “加载 `pr-review` skill，用它的 checklist 来 review 我接下来的改动。”
- “请调用 `skill({ name: \"code-review\" })` 并按该 skill 的规则对这段 diff 做 code review。”

#### 模式 B：任务描述 + 明确指令（让 agent 自己挑选）

当你不知道 skill 名称，但希望 agent “从可用 skills 里挑一个最合适的并加载”：

- “我需要一个**固定的 release 工作流**：生成 changelog、建议版本号、给出发布命令。请先从可用 skills 中挑一个最匹配的并加载，然后按步骤执行。”
- “我要发布一个包；如果存在发布/打包相关的 skill，请先加载 skill 再开始。”

#### 模式 C：先列出可用 skills，再选一个加载（适合 skill 很多的团队）

适合多个 skill 都可能命中的情况：

- “先列出当前可用的 skills（名称+描述），尤其是跟 文档/变更日志/release 相关的；然后选择最匹配的那个并加载。”

#### 模式 D：权限敏感（ask/deny 场景）

如果你们配置了 skill 权限可能需要确认：

- “如果加载 skill 需要我确认权限，请先问我；我确认后再加载最合适的 release 相关 skill 并按其流程执行。”

---

## 可直接复制的触发示例（中文）

### 示例 1：已知名称，直接触发

> 请加载 `git-release` skill。我要基于最近 20 个合并 PR 生成 release notes，并给出建议的版本号升级（patch/minor/major）与原因。

### 示例 2：通过 description 关键词触发（不知道名称）

> 我需要**一致的 releases 和 changelogs**流程。如果存在覆盖 releases/changelogs 的 skill，请先加载 skill 再开始执行，并按步骤输出。

### 示例 3：触发“代码评审”类 skill

> 请像资深工程师一样 review 我的改动。如果有 PR/code review 相关的 skill，请先加载并严格按 checklist 来做：包括风险点、边界情况、可维护性与测试建议。

### 示例 4：触发“排查/调试”类工作流 skill

> 我在排查一个 flaky test。如果有 debugging/triage 相关 skill，请先加载并按流程引导我。第一步先列出可能原因假设，以及每个假设的验证方法。

### 示例 5：先列出再选择（团队 skill 很多时）

> 请先列出可用 skills 里与“refactor / performance / architecture”相关的项，再选择一个最适合改造该模块的 skill 并加载，然后按它的步骤执行。

### 示例 6：显式要求“先加载 skill 再开始动手”

> 在你开始任何代码修改/方案输出之前：先查看并加载一个最匹配的 skill（如果存在）。如果没有匹配 skill，再按你自己的最佳实践继续。

### 示例 7：给 agent 一个明确的“触发信号词”

> 这是一项需要可复用流程的工作。请优先使用 skill（如果有）而不是直接回答。先给出你将要加载的 skill 名称，再加载它。

---

## 权限：为什么 skill 可能看不到/无法加载

skill 的访问由 pattern-based permissions 控制。若权限为 `deny`，该 skill 会从 agent 可见的 `<available_skills>` 列表中消失。

概念示例（以你们仓库实际配置为准）：

```json
{
  "permission": {
    "skill": {
      "*": "allow",
      "internal-*": "deny",
      "experimental-*": "ask"
    }
  }
}
```

行为：

- `allow`：直接加载
- `ask`：加载前会询问用户确认
- `deny`：隐藏/拒绝

---

## 排障清单（不生效时看这里）

skill 没出现或加载失败时，按优先级排查：

1. 文件名是否严格是 `SKILL.md`（全大写）
2. 文件是否以合法 YAML frontmatter 开头（`---`...`---`）
3. frontmatter 是否包含 `name` 与 `description`
4. `name` 是否与其它位置重复（重复会覆盖且会 warn）
5. 权限是否 deny 或 ask（deny 会直接隐藏）

---

## 参考实现

- Web 文档：`packages/web/src/content/docs/skills.mdx`
- 发现逻辑：`packages/opencode/src/skill/skill.ts`
- 加载工具：`packages/opencode/src/tool/skill.ts`
