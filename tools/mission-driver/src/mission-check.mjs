/**
 * mission-check.mjs — mission.json validator (parallel to plan-check.mjs for plans).
 *
 * Validates that a mission config has the required fields and that its paths
 * exist on disk. This is a FIXED contract validator — it enforces the mission
 * schema for ANY project, does not read project-specific config.
 */

import { existsSync, readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { pathToFileURL } from "node:url";

const REQUIRED_FIELDS = ["name", "roadmapPath", "plansDir", "commands"];
const REQUIRED_COMMANDS = ["test"];

/**
 * Shallow-merge base config into mission. Merge priority (low → high):
 *   1. `{extends}.json` — shared base
 *   2. `{extends}.local.json` (if exists) — per-user overrides, NOT committed
 *   3. mission.json fields — per-mission overrides
 * `extends` may be a filename (resolved relative to the mission file's
 * directory) or an absolute path.
 */
function resolveExtends(mission, missionDir) {
  const baseName = mission.extends;
  if (!baseName) return { ...mission };
  const baseFile = resolve(missionDir, `${baseName}.json`);
  if (!existsSync(baseFile)) {
    throw new Error(`extends target not found: ${baseFile}`);
  }
  const base = JSON.parse(readFileSync(baseFile, "utf8"));
  // Recursively resolve nested extends (base may extend another base).
  const resolved = resolveExtends(base, missionDir);
  // Strip _-prefixed internal keys from both base and mission.
  const stripMeta = (obj) => Object.fromEntries(
    Object.entries(obj).filter(([k]) => !k.startsWith("_"))
  );
  // User-local overrides: {extends}.local.json takes precedence over base
  // but can still be overridden by mission-specific fields.
  const localFile = resolve(missionDir, `${baseName}.local.json`);
  let localOverrides = {};
  if (existsSync(localFile)) {
    localOverrides = stripMeta(JSON.parse(readFileSync(localFile, "utf8")));
  }
  // Remove `extends` (load-time directive) from mission.
  const { extends: _, ...missionRest } = mission;
  // Merge: base → local → mission (later wins in shallow merge).
  const merged = { ...stripMeta(resolved), ...localOverrides, ...stripMeta(missionRest) };
  return merged;
}

/**
 * Validate a mission object (already parsed).
 * @param {object} mission
 * @param {string} [projectRoot] - if given, checks path existence
 * @returns {{valid: boolean, errors: string[]}}
 */
export function validateMission(mission, projectRoot) {
  const errors = [];

  for (const f of REQUIRED_FIELDS) {
    if (!mission[f]) errors.push(`missing required field: ${f}`);
  }

  if (mission.commands) {
    for (const c of REQUIRED_COMMANDS) {
      if (!mission.commands[c]) errors.push(`commands.${c} is required`);
    }
  } else if (mission.commands !== undefined) {
    errors.push("commands must be an object");
  }

  if (projectRoot) {
    for (const [field, val] of [
      ["roadmapPath", mission.roadmapPath],
      ["plansDir", mission.plansDir],
      ["contextDir", mission.contextDir],
      ["moduleDir", mission.moduleDir],
      ["promptsDir", mission.promptsDir],
    ]) {
      if (val && !existsSync(resolve(projectRoot, val))) {
        errors.push(`${field} does not exist: ${val}`);
      }
    }
  }

  return { valid: errors.length === 0, errors };
}

/**
 * Load and validate a mission json file.
 * @param {string} missionFile - absolute path to missions/<name>.json
 * @param {string} [projectRoot]
 * @returns {object} the parsed mission
 * @throws if invalid
 */
export function loadMission(missionFile, projectRoot) {
  const missionDir = dirname(missionFile);
  const raw = JSON.parse(readFileSync(missionFile, "utf8"));
  const mission = resolveExtends(raw, missionDir);
  const { valid, errors } = validateMission(mission, projectRoot);
  if (!valid) {
    throw new Error(`Invalid mission '${missionFile}':\n  ${errors.join("\n  ")}`);
  }
  return mission;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [file, root] = process.argv.slice(2);
  if (!file) {
    console.error("Usage: mission-check.mjs <mission.json> [projectRoot]");
    process.exit(2);
  }
  try {
    const mission = loadMission(file, root);
    console.log(JSON.stringify({ valid: true, name: mission.name, file }, null, 2));
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}
