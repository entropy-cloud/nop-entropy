import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { inferModuleName } from "../src/config.js";

// inferModuleName pure function — generic module-name inference (no
// project-specific module-code list). Drives the per-module memory store path.

describe("inferModuleName — tools/<name> branch", () => {
  it("tools/mission-driver → 'mission-driver'", () => {
    assert.equal(inferModuleName("tools/mission-driver", undefined), "mission-driver");
  });

  it("tools\\mission-driver (Windows backslash) → 'mission-driver'", () => {
    assert.equal(inferModuleName("tools\\mission-driver", undefined), "mission-driver");
  });

  it("tools/mission-driver/sub → 'mission-driver/sub' (full sub-path preserved)", () => {
    assert.equal(inferModuleName("tools/mission-driver/sub", undefined), "mission-driver/sub");
  });

  it("tools\\mission-driver\\sub (Windows nested) → preserved with backslash captured", () => {
    assert.equal(inferModuleName("tools\\mission-driver\\sub", undefined), "mission-driver\\sub");
  });
});

describe("inferModuleName — moduleDir basename", () => {
  it("single-segment moduleDir is returned as-is", () => {
    assert.equal(inferModuleName("my-module", undefined), "my-module");
  });

  it("packages/my-package → 'my-package' (last segment)", () => {
    assert.equal(inferModuleName("packages/my-package", undefined), "my-package");
  });

  it("apps\\billing\\svc (Windows nested) → 'svc' (last segment)", () => {
    assert.equal(inferModuleName("apps\\billing\\svc", undefined), "svc");
  });

  it("case is preserved (no upper-casing)", () => {
    assert.equal(inferModuleName("FooBar", undefined), "FooBar");
  });
});

describe("inferModuleName — mission-name fallback & edge cases", () => {
  it("mission name is used when moduleDir is absent", () => {
    assert.equal(inferModuleName(undefined, "tech-debt"), "tech-debt");
  });

  it("moduleDir takes precedence over mission name", () => {
    assert.equal(inferModuleName("packages/x", "other"), "x");
  });

  it("null/undefined inputs → null", () => {
    assert.equal(inferModuleName(null, undefined), null);
    assert.equal(inferModuleName(undefined, undefined), null);
  });

  it("empty strings → null", () => {
    assert.equal(inferModuleName("", ""), null);
    assert.equal(inferModuleName("", undefined), null);
  });
});
