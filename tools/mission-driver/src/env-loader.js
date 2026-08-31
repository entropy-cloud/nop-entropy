/**
 * Zero-dependency `.env` loader (NFR-1/NFR-2 — no dotenv npm package).
 *
 * Parses a simple KEY=VALUE `.env` file at the repo root and writes the
 * entries into `process.env`. Called once at engine startup (`src/main.js`),
 * before any mission/test config is read, so that `*Env` secret references
 * (resolved by `secret-resolver.js`) can find their values.
 *
 * Design choices :
 *  - A missing `.env` file is silently ignored — pure-CLI scenarios (CI with
 *    shell-exported vars) must not be blocked.
 *  - Already-set environment variables are NEVER overwritten — real shell/IDE
 *    exports take precedence over `.env` (matches `.env.example` precedence
 *    note: shell export > .env).
 *  - Supports double/single-quoted values and `#` comments (line-leading and
 *    trailing-after-whitespace for unquoted values).
 *  - Invalid keys (not matching `[A-Za-z_][A-Za-z0-9_]*`) are skipped, matching
 *    the tolerant style of `test-config.js`'s `stripJsonc`.
 */

import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Load a `.env` file into `process.env` (without overriding existing vars).
 *
 * @param {string} [projectRoot]  repo root containing `.env` (defaults to cwd)
 * @returns {number} number of variables loaded (0 if file missing)
 */
export function loadDotenv(projectRoot = ".") {
  const envPath = resolve(projectRoot, ".env");
  if (!existsSync(envPath)) return 0;

  let text;
  try {
    text = readFileSync(envPath, "utf8");
  } catch {
    return 0;
  }

  let count = 0;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const eq = line.indexOf("=");
    if (eq === -1) continue;

    const key = line.slice(0, eq).trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;

    let value = line.slice(eq + 1).trim();
    const firstCh = value[0];

    if ((firstCh === '"' || firstCh === "'") && value[value.length - 1] === firstCh && value.length >= 2) {
      // quoted value — take contents verbatim (no inline-comment stripping)
      value = value.slice(1, -1);
    } else {
      // unquoted value — strip a trailing ` # comment` (whitespace + #)
      const commentIdx = value.search(/\s+#/);
      if (commentIdx !== -1) value = value.slice(0, commentIdx).trim();
    }

    // never override an existing env var — shell/IDE export wins
    if (process.env[key] === undefined) {
      process.env[key] = value;
      count++;
    }
  }
  return count;
}
