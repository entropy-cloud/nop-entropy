Generate a mission config file for the mission driver, based on the user request below.

Read `AGENTS.md` **completely** for project structure, tech stack, build commands, and conventions.

## Brief gate (two-stage draft)

If `{{briefPath}}` is non-empty, a mission brief was generated in stage 1. **Read it first** (`{{briefPath}}`) and use its 目标 / 范围 / 目标产物 / 验收标准 / 模块 / 依赖 / 非目标 to drive the roadmap + mission.json generation. The brief is the authoritative scope gate — do not contradict its 非目标.

If `{{briefPath}}` is empty, no brief exists; fall back to the user request text directly (backward-compatible single-stage behaviour).

> Note: the user request may reference directories, multiple files, or an abstract goal — it is not limited to a single file. `--target-file` (when provided) is just one optional input aid that points the brief agent at a file/directory to ground the brief; it is never a required constraint.

## Roadmap

If a roadmap already exists at `{{backlogDir}}/{mission-name}-roadmap.md` or is referenced by the user, use it. Otherwise, generate the roadmap first following the format in `{{backlogDir}}/00-roadmap-authoring-guide.md`, save it at `{{backlogDir}}/{mission-name}-roadmap.md`, then generate the mission.json referencing it. The roadmap must include phase status, framework/platform reuse, current baseline, phase table, phase details, dependency graph, and cross-cutting concerns.

## Flow hint

When `{{flowHint}}` is non-empty, set the mission.json `flowName` to `{{flowHint}}` (the user/wizard explicitly selected this flow). Only omit `flowName` (to use the built-in `mission-driver` flow) when `{{flowHint}}` is empty.

Scan the project to determine correct values. Generate the file at `{{missionsDir}}/{mission-name}.json` and return results in the following format:
```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<MISSION_FILE>{{missionsDir}}/{mission-name}.json</MISSION_FILE>
```

The mission.json MUST follow this format:
```json
{
  "name": "{mission-name}",
  "description": "{what this mission covers}",
  "flowName": "{flow-name, defaults to mission-driver if omitted}",
  "roadmapPath": "{path/to/roadmap.md}",
  "plansDir": "{path/to/plans-dir}",
  "planGuide": "{path/to/plan-guide.md}",
  "auditsDir": "{path/to/audits-dir}",
  "contextDir": "{path/to/context-dir}",
  "moduleDir": "{path/to/module-or-project-root}",
  "commands": {
    "test": "{test command}",
    "build": "{build command}",
    "lint": "{lint command}",
    "typecheck": "{typecheck command}",
    "check": "{optional deterministic-state gate command, e.g. mvn clean compile; empty/omitted = git conflict-marker fallback}"
  },
  "prompts": {
    "multiAudit": "{path/to/multi-audit-prompt.md}",
    "openAudit": "{path/to/open-audit-prompt.md}"
  },
  "commitFormat": "{commit message format}"
}
```

Notes:
- `plansDir` — MUST be a per-mission subdirectory: `docs/plans/{USER}/{mission-name}`. Determine `{USER}` from `git config user.name` (slug: lowercase, spaces to `-`). Each mission MUST have its own subdirectory to prevent plan cross-contamination between missions. Create the directory if it does not exist.
- `flowName` — custom main flow name; omit to use the built-in `mission-driver` flow. When a flow hint was provided via `{{flowHint}}`, use that value verbatim. Custom flows are loaded from `missions/flows/<flowName>.json` first, then the tool's built-in `flows/`
- `moduleDir` — the target module or project directory for this mission; audit steps focus on this scope (code, config, tests, docs). Use project root for simple single-module projects
- `prompts.multiAudit` / `prompts.openAudit` — project-specific audit skill prompt files; empty or omitted = skip that audit type
- `commitFormat` — git commit message format hint for BUILD_VERIFY, e.g. `feat(<scope>): <title>` or `imperative mood; reference plan path in footer`
- `commands.check` — optional deterministic-state gate for the CHECK step. Set it when the project has a fast command that confirms a clean/deterministic state (e.g. `mvn clean compile` for a Java project). Empty or omitted falls back to git conflict-marker detection. Note: `extends: "base"` is a shallow merge, so if you set any `commands` key you should also set `check` explicitly (otherwise the base `check` default is dropped).
