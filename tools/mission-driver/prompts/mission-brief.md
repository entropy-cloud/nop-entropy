Generate a concise mission brief that gates the subsequent roadmap + mission.json generation.

Read `AGENTS.md` **completely** for project structure, tech stack, build commands, and conventions. Also read `docs/context/project-context.md` for the module map and validation commands.

## Inputs

- **User goal** (provided in the `## User Goal` section below).
- **flowName**: `{{flowHint}}` (may be empty — empty means the built-in `mission-driver` flow).
- **Target file or directory** (optional): `{{targetFile}}` (project-relative path; may be empty). The description may reference any path — a single file, a directory, multiple files, or an abstract goal. `--target-file` is an optional input aid, not a required constraint. When non-empty, read it (or scan it if it is a directory) to ground the brief in the actual code/design being changed.

## Task

Derive a `<slug>` (kebab-case) from the user goal. Produce a brief at `{{backlogDir}}/<slug>-brief.md` with EXACTLY these sections (in order), each as a `##` heading:

1. **目标** — one-to-three sentence statement of what this mission accomplishes.
2. **范围** — bullet list of the in-scope work (files, modules, features).
3. **目标产物(文件)** — bullet list of concrete deliverables (file paths or artifact descriptions). Include the target file from the input when provided.
4. **验收标准** — bullet list of observable, testable acceptance criteria (e.g. "npm test passes", "page renders the new column", "batch job produces the CSV").
5. **模块** — the target module(s) from the project module map (e.g. `module-a`, `module-b`, or `mission-driver`).
6. **依赖** — bullet list of upstream/downstream dependencies (other modules, external services, existing contracts).
7. **非目标** — bullet list of explicitly out-of-scope items to prevent scope creep.

Keep the brief tight — it is a gate, not a design document. Avoid implementation detail; that belongs in the roadmap + plans.

## Brief Gate

After writing the brief, decide whether it is safe to proceed to roadmap + mission.json generation. Emit your decision via the `<BRIEF_GATE>` marker (the engine parses it — see §4.2 of `tools/mission-driver/design/draft-robustness-design.md`).

- `pass`: the description is enough to derive 目标 / 范围 / 产物 (even at coarse granularity). Examples: `"add audit count to dashboard"`, `"为 mission-driver 增加 draft 描述校验"`.
- `blocked`: the description is too thin to safely generate roadmap + mission.json. Examples: a bare keyword like `"optimize"` with no target module / metric / acceptance signal; pure placeholder like `"asdf stuff"`; a goal with no observable acceptance criteria.

When `blocked`, the engine will NOT enter Stage 2 (no roadmap, no mission.json). Use the brief's 非目标 / open questions to spell out what the user must clarify, then re-run `draft` after refining the description.

## Output

Write the file to `{{backlogDir}}/<slug>-brief.md` (create the directory if needed). Then return ONLY:

```
<BRIEF_FILE>{{backlogDir}}/<slug>-brief.md</BRIEF_FILE>
<BRIEF_GATE>pass|blocked</BRIEF_GATE>
<BRIEF_GATE_REASON>one short sentence (required when blocked, may be empty tag when pass)</BRIEF_GATE_REASON>
```
