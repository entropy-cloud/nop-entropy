import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { resolveAuditsDir } from "../src/config.js";

// resolveAuditsDir is a pure helper (derivation + best-effort mkdir). The mkdir
// side-effect is exercised against an isolated temp project root so the repo is
// never polluted. Derivation logic is path-string only — asserted directly.

describe("resolveAuditsDir — per-mission derivation", () => {
  let tmpRoot;
  beforeEach(() => { tmpRoot = mkdtempSync(join(tmpdir(), "audits-dir-")); });
  afterEach(() => { try { rmSync(tmpRoot, { recursive: true, force: true }); } catch {} });

  it("derives audits dir from plans dir by swapping the plans segment", () => {
    const got = resolveAuditsDir(
      "docs/audits",
      "docs/plans/huang-jiang/mission-driver-overall-optimization",
      tmpRoot,
    );
    assert.equal(got, "docs/audits/huang-jiang/mission-driver-overall-optimization");
    assert.ok(existsSync(join(tmpRoot, got)), "derived dir should be created");
  });

  it("derives when auditsDir is undefined", () => {
    const got = resolveAuditsDir(undefined, "docs/plans/u/m", tmpRoot);
    assert.equal(got, "docs/audits/u/m");
  });

  it("derives with Windows backslash plans dir", () => {
    const got = resolveAuditsDir("docs/audits", "docs\\plans\\huang-jiang\\m", tmpRoot);
    assert.equal(got, "docs/audits/huang-jiang/m");
  });
});

describe("resolveAuditsDir — explicit override respected", () => {
  let tmpRoot;
  beforeEach(() => { tmpRoot = mkdtempSync(join(tmpdir(), "audits-dir-")); });
  afterEach(() => { try { rmSync(tmpRoot, { recursive: true, force: true }); } catch {} });

  it("custom auditsDir is returned as-is (not derived)", () => {
    const got = resolveAuditsDir("custom/audits/here", "docs/plans/u/m", tmpRoot);
    assert.equal(got, "custom/audits/here");
  });

  it("absolute custom auditsDir is respected", () => {
    const got = resolveAuditsDir("/abs/audits", "docs/plans/u/m", tmpRoot);
    assert.equal(got, "/abs/audits");
  });
});

describe("resolveAuditsDir — fallbacks (no derivation)", () => {
  let tmpRoot;
  beforeEach(() => { tmpRoot = mkdtempSync(join(tmpdir(), "audits-dir-")); });
  afterEach(() => { try { rmSync(tmpRoot, { recursive: true, force: true }); } catch {} });

  it("no plansDir + global auditsDir → returns global default", () => {
    assert.equal(resolveAuditsDir("docs/audits", undefined, tmpRoot), "docs/audits");
  });

  it("no plansDir + undefined auditsDir → returns global default", () => {
    assert.equal(resolveAuditsDir(undefined, undefined, tmpRoot), "docs/audits");
  });

  it("plansDir without /plans/ segment → returns auditsDir as-is (no guess)", () => {
    assert.equal(resolveAuditsDir("docs/audits", "some/other/dir", tmpRoot), "docs/audits");
  });

  it("flat plansDir 'docs/plans' (no trailing segment) → returns global (no guess)", () => {
    assert.equal(resolveAuditsDir("docs/audits", "docs/plans", tmpRoot), "docs/audits");
  });
});
