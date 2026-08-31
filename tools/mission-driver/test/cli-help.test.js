import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MAIN_JS = resolve(__dirname, "..", "src", "main.js");

function runCli(...args) {
  return execFileSync(process.execPath, [MAIN_JS, ...args], {
    encoding: "utf8",
    timeout: 10000,
  });
}

// ── Subcommands registered in commander ─────────────────────────────────────

describe("CLI subcommands", () => {
  it("registers run subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /run/);
    assert.match(text, /运行指定 mission/);
  });

  it("registers list subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /list/);
    assert.match(text, /列出所有可用 mission/);
  });

  it("registers list-steps subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /list-steps/);
  });

  it("registers draft subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /draft/);
  });

  it("registers analyze subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /analyze/);
  });

  it("registers monitor subcommand", () => {
    const text = runCli("--help");
    assert.match(text, /monitor/);
  });
});

// ── Legacy flags removed ────────────────────────────────────────────────────

describe("Legacy flags are removed", () => {
  it("no longer exposes --list-missions", () => {
    const text = runCli("--help");
    assert.doesNotMatch(text, /--list-missions/);
  });

  it("no longer exposes --list-steps as a flag", () => {
    const text = runCli("--help");
    assert.doesNotMatch(text, /\[backward compat\].*--list-steps/);
  });

  it("no longer exposes --draft-mission", () => {
    const text = runCli("--help");
    assert.doesNotMatch(text, /--draft-mission/);
  });

  it("no longer exposes --analyze-run", () => {
    const text = runCli("--help");
    assert.doesNotMatch(text, /--analyze-run/);
  });
});

// ── run subcommand help ─────────────────────────────────────────────────────

describe("run subcommand", () => {
  it("shows help with --help", () => {
    const text = runCli("run", "--help");
    assert.match(text, /运行指定 mission/);
    assert.match(text, /--dry-run/);
    assert.match(text, /--step/);
    assert.match(text, /--max-cycles/);
  });
});
