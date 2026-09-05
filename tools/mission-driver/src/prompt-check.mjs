/**
 * prompt-check.mjs — lint prompts/*.md against the flow marker contract (§1.4-0, §11.8).
 *
 * Root cause of the "false failure" incident: a prompt example carried a
 * mismatched/typo'd result tag (`<AIE_STEP_RESULT>done</AI_STEP_RESULT>`), which
 * the model faithfully copied, defeating marker extraction. This linter makes
 * that class of bug impossible to reintroduce silently:
 *
 *   1. Every `<…STEP_RESULT>value</…STEP_RESULT>` example MUST use exactly
 *      `<AI_STEP_RESULT>` on BOTH the opening and closing tag (catches AIE_ typos
 *      and open/close mismatches).
 *   2. For prompts bound to a NON-forEach flow step, the example `value` MUST be a
 *      valid transition marker or top-level markerAlias for that step.
 *      (forEach steps aggregate per-item markers — e.g. plan-review emits
 *      `approved` which the engine folds into all_complete — so value membership
 *      is not enforced there; tag spelling still is.)
 *
 * Run standalone: `node src/prompt-check.mjs` (exit 1 on any error).
 */

import { readFileSync, readdirSync, existsSync } from "node:fs";
import { resolve, dirname, basename, join } from "node:path";
import { fileURLToPath } from "node:url";

const TOOL_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const FLOW_NAMES = ["mission-driver", "plan-execution", "deep-audit-loop"];

/**
 * Build map: prompt-basename → { markers: Set<string>, forEach: boolean }.
 * Aggregates across every flow step that references the prompt.
 */
export function buildPromptMarkerMap(rootDir = TOOL_ROOT) {
  const map = new Map();
  for (const name of FLOW_NAMES) {
    const flowPath = join(rootDir, "flows", `${name}.json`);
    if (!existsSync(flowPath)) continue;
    let flow;
    try {
      flow = JSON.parse(readFileSync(flowPath, "utf8"));
    } catch {
      continue;
    }
    const aliases = Object.keys(flow.markerAliases || {});
    for (const step of Object.values(flow.steps || {})) {
      if (!step.promptPath) continue;
      const base = basename(step.promptPath);
      const entry = map.get(base) || { markers: new Set(), forEach: false };
      for (const m of Object.keys(step.transitions || {})) entry.markers.add(m);
      for (const a of aliases) entry.markers.add(a);
      if (step.forEach) entry.forEach = true;
      map.set(base, entry);
    }
  }
  return map;
}

// Matches an XML-ish result-tag PAIR: <OPEN>value</CLOSE> where either tag name
// ends in STEP_RESULT. Deliberately loose on the tag name so typos are caught.
const TAG_PAIR_RE = /<\s*([A-Za-z_]*STEP_RESULT)\s*>\s*([A-Za-z_][A-Za-z_]*)\s*<\/\s*([A-Za-z_]*STEP_RESULT)\s*>/g;

/** Lint a single prompt's content. Returns an array of error strings. */
export function lintPrompt(fileName, content, markerInfo) {
  const errors = [];
  TAG_PAIR_RE.lastIndex = 0;
  let m;
  while ((m = TAG_PAIR_RE.exec(content)) !== null) {
    const [full, open, value, close] = m;
    if (open !== "AI_STEP_RESULT" || close !== "AI_STEP_RESULT") {
      errors.push(
        `${fileName}: malformed result tag "${full.trim()}" — both tags must be exactly ` +
        `<AI_STEP_RESULT>…</AI_STEP_RESULT> (found <${open}>…</${close}>)`,
      );
      continue;
    }
    if (markerInfo && !markerInfo.forEach && markerInfo.markers.size > 0) {
      const v = value.toLowerCase();
      if (!markerInfo.markers.has(v)) {
        errors.push(
          `${fileName}: marker value "${v}" is not a valid transition/alias for its step ` +
          `(allowed: ${[...markerInfo.markers].sort().join(", ")})`,
        );
      }
    }
  }
  return errors;
}

/** Lint every prompts/*.md. Returns an array of error strings (empty = clean). */
export function lintAllPrompts(rootDir = TOOL_ROOT) {
  const promptsDir = join(rootDir, "prompts");
  if (!existsSync(promptsDir)) return [];
  const map = buildPromptMarkerMap(rootDir);
  const errors = [];
  for (const f of readdirSync(promptsDir).filter((x) => x.endsWith(".md"))) {
    const content = readFileSync(join(promptsDir, f), "utf8");
    errors.push(...lintPrompt(f, content, map.get(f)));
  }
  return errors;
}

// CLI entry
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = lintAllPrompts();
  if (errors.length === 0) {
    console.log("prompt-check: OK — all prompt result-tag examples are well-formed.");
    process.exit(0);
  }
  console.error(`prompt-check: ${errors.length} problem(s) found:`);
  for (const e of errors) console.error("  - " + e);
  process.exit(1);
}
