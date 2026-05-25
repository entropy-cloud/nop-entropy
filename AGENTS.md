# AGENTS.md - Nop Entropy Development Guide

## Project Overview

`nop-entropy` 是一个可替代 Spring 的全栈 Java 框架（Nop 平台），采用可逆计算原理。

**Tech Stack**: Java 21, Maven (wrapper 4.0.0-rc-5), JUnit 5, Nop AutoTest, XLang。

模块分组和依赖关系见 `docs-for-ai/01-repo-map/module-groups.md`。

---

## Documentation Routing

**`docs-for-ai/INDEX.md` is the authoritative docs navigation baseline.** 下表覆盖最常见的 agent 工作流。**当 By Task 和 By Code Location 都命中时，合并去重阅读清单。**

### By Task

| Task | Read first | Then read |
|------|-----------|-----------|
| 修改业务逻辑（service、action、biz） | `docs-for-ai/02-core-guides/service-layer.md` | `docs-for-ai/02-core-guides/api-and-graphql.md` |
| 新增或修改 ORM 模型 / 数据库表 | `docs-for-ai/02-core-guides/model-first-development.md` | `docs-for-ai/02-core-guides/delta-customization.md` |
| 修改 IoC 配置、注入、bean 定义 | `docs-for-ai/02-core-guides/ioc-and-config.md` | — |
| 修改 GraphQL API、crud 操作 | `docs-for-ai/02-core-guides/api-and-graphql.md` | `docs-for-ai/02-core-guides/service-layer.md` |
| 修改 Delta 定制、覆盖已有模块 | `docs-for-ai/02-core-guides/delta-customization.md` | `docs-for-ai/02-core-guides/model-first-development.md` |
| 处理错误码、异常处理 | `docs-for-ai/02-core-guides/error-handling.md` | — |
| 修改平台框架核心（nop-core/nop-xlang/nop-xdef 等） | `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` | `docs-for-ai/02-core-guides/xdef-and-xdsl.md` |
| 编写或修改测试 | `docs-for-ai/02-core-guides/testing.md` | — |
| 编写或运行平台 E2E 测试（Playwright） | `nop-entropy-e2e/README.md` | — |
| 执行特定类型任务（按 runbook） | `docs-for-ai/03-runbooks/README.md` | 对应 runbook 文件 |
| 新增模块或修改包结构 | `docs-for-ai/01-repo-map/module-groups.md` | `docs-for-ai/01-repo-map/domain-module-pattern.md` |
| Draft, execute, or audit a plan under `ai-dev/plans/` | `ai-dev/plans/00-plan-authoring-and-execution-guide.md` | `ai-dev/logs/00-log-writing-guide.md` |
| Write or update daily dev log | `ai-dev/logs/00-log-writing-guide.md` | — |
| Write a bug fix note | `ai-dev/bugs/00-bug-fix-note-writing-guide.md` | — |
| Write an analysis | `ai-dev/analysis/00-analysis-writing-guide.md` | — |
| Write a design doc | `ai-dev/design/00-design-writing-guide.md` | `ai-dev/design/README.md` |
| Write or update skills | Check existing skills under `.opencode/skills/` first | — |

### By Code Location

> **⚠️动手前检查 Protected Areas**：如果代码位于下表 Protected Area 中，必须先遵守对应规则（plan-first / ask-first），再读技术文档。

| When touching this code | Read this |
|------------------------|-----------|
| `nop-core/`, `nop-xlang/` | `docs-for-ai/01-repo-map/module-groups.md` |
| `nop-biz/`, service/action 层 | `docs-for-ai/02-core-guides/service-layer.md` |
| `nop-dao/`, ORM/model 层 | `docs-for-ai/02-core-guides/model-first-development.md` |
| `nop-graphql/` | `docs-for-ai/02-core-guides/api-and-graphql.md` |
| `nop-chaos/` (demo app) | `docs-for-ai/01-repo-map/domain-module-pattern.md` |
| 业务模块（`nop-auth/`, `nop-job/`, `nop-wf/`, `nop-task/` 等） | `docs-for-ai/02-core-guides/service-layer.md` + 对应 runbook |
| 其他 `nop-*` 模块 | `docs-for-ai/01-repo-map/module-groups.md` |
| `nop-entropy-e2e/` (e2e tests) | `nop-entropy-e2e/README.md` |
| `ai-dev/` (any subdirectory) | 对应子目录的 `00-*-guide.md` 或 `README.md` |

---

## Docs Maintenance

**Docs live in `docs-for-ai/`** and are the primary source of platform usage knowledge. Always consult `docs-for-ai/INDEX.md` first for navigation. See `ai-dev/logs/00-log-writing-guide.md` for log writing conventions.

**`ai-dev/`** records the development process of the platform itself; it is not normative documentation for platform users.

### Documentation Domains

| | `docs-for-ai/` | `ai-dev/` |
|---|---|---|
| **定位** | **使用** Nop 平台所需的知识 | **开发** Nop 平台本身所需的知识 |
| **内容** | API、约定、开发模式、runbook | 设计决策、执行计划、复杂 bug 分析、经验教训 |
| **读者** | 基于 Nop 构建应用的 AI / 开发者 | 改造 Nop 框架本身的 AI / 开发者 |
| **权威性** | source of truth（规范性文档） | `design/` 规范性；`logs/`、`plans/`、`bugs/` 过程记录 |
| **路由入口** | `docs-for-ai/INDEX.md` | `ai-dev/logs/index.md` |

### Mandatory Updates

After completing any significant **CODE CHANGE**, you MUST:

1. **Update the daily dev log** at `ai-dev/logs/{year}/{month}-{day}.md` (reverse chronological, see `ai-dev/logs/00-log-writing-guide.md` for format).
2. **Update relevant owner docs** under `docs-for-ai/` when changing:
   - Service layer patterns → `docs-for-ai/02-core-guides/service-layer.md`
   - ORM/model patterns → `docs-for-ai/02-core-guides/model-first-development.md`
   - API or GraphQL → `docs-for-ai/02-core-guides/api-and-graphql.md`
   - General conventions → the smallest owning doc under `docs-for-ai/`
3. **Update `docs-for-ai/INDEX.md`** and `docs-for-ai/04-reference/source-anchors.md` if routing or implementation anchors changed.

### `ai-dev/` Directory Roles

| 目录 | 用途 | 什么时候写 | 写前必读 |
|---|---|---|---|
| `logs/` | 每日开发上下文、决策记录 | 每次 significant code/doc change（一天一个文件，追加在顶部） | `ai-dev/logs/00-log-writing-guide.md` |
| `plans/` | 执行计划（含 status、exit criteria） | 方案已确定、进入实施阶段时 | `ai-dev/plans/00-plan-authoring-and-execution-guide.md` |
| `design/` | 架构决策 + 使用契约 + 需求规格 | 方案确定后。按子系统组织子目录 | `ai-dev/design/00-design-writing-guide.md` |
| `analysis/` | AI 单方面调研、对比、评估 | 对比多个技术方案、评估代码质量时 | `ai-dev/analysis/00-analysis-writing-guide.md` |
| `discussions/` | 人与 AI 多轮对话，澄清模糊需求 | 需求不明确时 | `ai-dev/discussions/00-discussion-writing-guide.md` |
| `bugs/` | 复杂 bug 的修复记录 | 根因不明显、跨模块的 bug | `ai-dev/bugs/00-bug-fix-note-writing-guide.md` |
| `audits/` | 代码和设计审计记录 | 执行代码审计时 | `ai-dev/audits/README.md` |
| `lessons/` | 经验教训索引 | 踩坑后总结 | `ai-dev/lessons/README.md` |
| `skills/` | 可复用的 AI 审计/review prompt 模板 | 需要标准化审计流程时 | `ai-dev/audits/README.md` 中的 prompt 对照表 |

**所有 AI 开发计划必须写在 `ai-dev/plans/` 下，禁止写入 `docs/plans/`。**

---

## AI Autonomy Levels

当前项目状态和 autonomy 等级定义在 `docs-for-ai/00-start-here/project-context.md`。

### 等级定义

| 等级 | 含义 |
|------|------|
| `implement` | AI 可直接实施，前提是满足 Verification Checklist |
| `plan-first` | AI 可起草/更新计划，但实施需等待 plan audit |
| `ask-first` | AI 必须先询问，才能变更代码或用户可见行为 |
| `research-only` | AI 可 inspect、summarize、propose，但不能修改产品行为 |
| `blocked` | AI 不可继续，直到阻塞解除 |

### Protected Areas

以下区域变更需 `plan-first` 或 `ask-first`：

| 区域 | Rule | Required Evidence |
|------|------|-------------------|
| ORM 模型结构（`model/*.orm.xml`） | plan-first | owner doc + test |
| 跨模块公共 API（`nop-*-api`） | plan-first | owner doc + migration plan |
| 权限/认证模型（`nop-auth`） | ask-first | owner doc + test |
| 生成管线（`_gen/`、`_*.xml`、codegen 模板） | plan-first | 理解生成链路 |
| 框架核心引擎（`nop-core`/`nop-xlang`/`nop-xdef` 内部） | plan-first | 设计文档 + 回归测试 |

### AI Must Ask Before

1. 变更产品范围且 requirement 或 owner doc 有歧义
2. 跳过验证因为命令缺失或失败
3. 关闭一个 audit/verification/docs 证据缺失的 plan
4. 当 `docs-for-ai/` 与实际代码冲突且解决冲突会改变用户可见行为

---

## Plan Execution

When creating, revising, executing, or auditing a file under `ai-dev/plans/`, you **MUST** read `ai-dev/plans/00-plan-authoring-and-execution-guide.md` first. Then follow these hard rules:

1. **Read the plan guide first** — every time, no exceptions.
2. **Follow the guide mechanically** — it defines what to do before, during, and after execution. Treat its rules as hard constraints, not suggestions.
3. **Maintain the plan file as a live document** — toggle checkboxes immediately after each item, update phase/slice status in real time, never batch updates at the end.
4. **Closure requires independent audit** — use a separate subagent (different task_id) to verify exit criteria against live code. Never self-audit.
5. **Textual consistency before `completed`** — Plan Status, Phase Status, Exit Criteria, Closure Gates, and daily log must all agree before you set the plan to `completed`.

These rules exist because agents routinely skip plan maintenance when focused on code changes. The guide's 19 minimum rules are the correction.

---

## Generated Files And Docs

- Files or directories prefixed with `_` are typically generated by the codegen/build pipeline and will be overwritten during `mvn install`. Do **not** hand-edit them unless the corresponding generator/template is being changed intentionally.
- Prefer editing the source model/template files instead of generated outputs. Typical examples: edit `model/*.orm.xml`, not generated `_app.orm.xml`, `_gen/*.java`, or other `_*.xml` / `_*.java` files.
- `docs-for-ai/` is the only runtime guidance source for normal development AI work. Do **not** read `docs/` or `docs-for-ai-old/` during ordinary development tasks.
- If `docs-for-ai/` is insufficient, prefer LSP / definition lookup on classes and methods referenced from `docs-for-ai/04-reference/`. Only read raw source in exceptional blocker cases or for documentation maintenance, and then update `docs-for-ai/`.
- If normal development reveals that `docs-for-ai/` is inaccurate, incomplete, or missing a high-frequency rule, treat that as a docs bug and fix the relevant owner doc in the same task whenever feasible.

---

## Nop IoC vs Spring (high-impact differences)

- **Field injection visibility**: NopIoC does **not** support injecting into `private` fields.
  - ✅ Prefer `protected` or package-private fields when using `@Inject`.
  - ✅ Prefer **setter injection** when you want explicit dependencies but don't want constructor-based wiring.
  - ❌ Avoid examples like `@Inject private Foo foo;` in code and documentation.

- **Value/config injection**: inject configuration values with `@InjectValue` (avoid Spring-only patterns like `@Value`).

- **AOP usage**: Do not assume Spring AOP patterns (`@Aspect`, `@Around`) work in Nop. Verify actual implementations exist in codebase before documenting or using them. Nop uses source-code generated AOP, not runtime bytecode manipulation.

---

## Commands

```bash
./mvnw clean install -T 1C          # full build (recommended)
./mvnw clean install -DskipTests -T 1C  # quick build without tests
./mvnw test                          # run tests
./mvnw test -pl nop-stream -am      # test single module with dependencies
```

Always run build and tests after making **CODE** changes.

### Java setup (Windows)

If `./mvnw` reports `JAVA_HOME not found`, auto-discover a local JDK from `javac` and set `JAVA_HOME`:

```powershell
$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
if (-not $javac) { throw "No javac found in PATH. Install JDK 21 first." }
$jdk = Split-Path (Split-Path $javac -Parent) -Parent
$env:JAVA_HOME = $jdk
if (-not ($env:Path -split ';' | Where-Object { $_ -eq "$jdk\bin" })) {
    $env:Path = "$jdk\bin;$env:Path"
}
setx JAVA_HOME "$jdk"
```

---

## Code Conventions

- **Naming**: PascalCase (classes), camelCase (methods/variables), UPPER_SNAKE_CASE (constants)
- **Formatting**: 4-space indentation, keep lines ~80–120 chars where reasonable
- **Package names**: modules use `io.nop.<module-name>` (e.g., `io.nop.ai.core`, `io.nop.stream.core`)
- **Imports**: grouped (java.* → jakarta.* → third-party → io.nop.*)
- **Error handling**: Prefer `NopException` + ErrorCode for business errors; include parameters via `.param(...)`; log with SLF4J; do **not** hardcode Chinese error messages in code
- Avoid noisy refactors; keep diffs minimal and focused

---

## Git Workflow

**IMPORTANT**: Commit frequently to avoid losing work.

- After completing a significant feature or fix, commit immediately
- use nop-git-master skill
- Never let uncommitted changes accumulate across multiple features

---

## Bug Fix Test Coverage Rule

After fixing any non-trivial bug, you MUST:

1. **Evaluate whether regression tests are needed.** If the bug had a non-obvious root cause, could be reintroduced by refactoring, or crossed module boundaries, add a test.
2. **Add tests that verify the correct result**, not just the absence of an error.
3. **Prefer adding new regression tests instead of rewriting or weakening existing ones.** Preserve prior coverage whenever possible.
4. **Record complex bugs** in `ai-dev/bugs/` following `ai-dev/bugs/00-bug-fix-note-writing-guide.md`.

---

## Verification Checklist

Before finishing any task:

- [ ] `./mvnw test -pl <affected-module> -am` passes (or full `./mvnw clean install` if touched multiple modules)
- [ ] Check style: imports grouped (java.* → jakarta.* → third-party → io.nop.*)
- [ ] Tests: add/extend JUnit 5 tests; use Nop AutoTest where the project already uses it
- [ ] `docs-for-ai/` updated (if conventions or APIs changed)
- [ ] `ai-dev/logs/` updated (for significant changes, see `ai-dev/logs/00-log-writing-guide.md`)
- [ ] `ai-dev/plans/` updated (if executing a plan, see Plan Execution rules above)
