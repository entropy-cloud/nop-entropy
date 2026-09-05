import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { inspectPlan } from "../src/plan-check.mjs";

function tmpPlan(name, content) {
  const dir = mkdtempSync(join(tmpdir(), "plan-check-"));
  const file = join(dir, name);
  writeFileSync(file, content, "utf8");
  return { dir, file };
}

describe("inspectPlan — unchecked items", () => {
  it("flags plans with unchecked checklist items", () => {
    const { file, dir } = tmpPlan("001.md", `# 1 test

> Plan Status: active
> Package: @nop-chaos/flux-runtime

## Execution Plan

### Phase 1 - build

Status: planned

Exit Criteria:

- [ ] task one
- [ ] task two
`);
    try {
      const r = inspectPlan(file);
      assert.equal(r.passed, false);
      assert.equal(r.totalUnchecked, 2);
      assert.equal(r.planStatus, "active");
      assert.ok(r.details.some(d => d.includes("unchecked items")));
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });

  it("passes a plan with everything checked and closure evidence", () => {
    const { file, dir } = tmpPlan("002.md", `# 2 done

> Plan Status: completed
> Last Reviewed: 2026-06-19

### Phase 1 - done

Status: completed

Exit Criteria:

- [x] task one

## Closure

Status Note: shipped

Closure Audit Evidence:

- pnpm --filter @nop-chaos/flux-runtime test green
- typecheck passes
`);
    try {
      const r = inspectPlan(file);
      assert.equal(r.passed, true);
      assert.equal(r.totalUnchecked, 0);
      assert.equal(r.planStatus, "completed");
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
});

describe("inspectPlan — closure evidence", () => {
  it("flags a completed plan whose Closure section has only a placeholder", () => {
    const { file, dir } = tmpPlan("003.md", `# 3 hollow

> Plan Status: completed

### Phase 1 - x

Status: completed

Exit Criteria:

- [x] done

## Closure

Status Note: *(pending)*

Closure Audit Evidence:

- *(pending)*
`);
    try {
      const r = inspectPlan(file);
      assert.equal(r.passed, false);
      assert.equal(r.planStatus, "completed");
      assert.ok(r.details.includes("missing closure evidence"),
        `expected 'missing closure evidence' in ${JSON.stringify(r.details)}`);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });

  it("passes a completed plan with real evidence items", () => {
    const { file, dir } = tmpPlan("004.md", `# 4 ok

> Plan Status: completed

### Phase 1 - x

Status: completed

Exit Criteria:

- [x] done

## Closure

Status Note: done

Closure Audit Evidence:

- All unit tests pass
`);
    try {
      const r = inspectPlan(file);
      assert.equal(r.passed, true);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });

  it("flags a hollow Closure even when ## Closure Gates precedes it", () => {
    // Regression: CLOSURE_HEADER_RE must match "## Closure", not "## Closure
    // Gates". The plan template always places Closure Gates before Closure,
    // so a naive /\b/ anchor sampled the Gates items as evidence and let a
    // placeholder-only Closure pass.
    const { file, dir } = tmpPlan("005.md", `# 5 hollow behind gates

> Plan Status: completed

### Phase 1 - x

Status: completed

Exit Criteria:

- [x] done

## Closure Gates

- [x] gate one
- [x] gate two

## Closure

Status Note: *(pending)*

Closure Audit Evidence:

- *(pending)*
`);
    try {
      const r = inspectPlan(file);
      assert.equal(r.passed, false);
      assert.equal(r.planStatus, "completed");
      assert.ok(r.details.includes("missing closure evidence"),
        `expected 'missing closure evidence' in ${JSON.stringify(r.details)}`);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
});

describe("inspectPlan — status parsing", () => {
  it("parses both bold and plain Plan Status front matter", () => {
    const bold = `# x\n\n> **Plan Status**: In Progress\n`;
    const plain = `# x\n\n> Plan Status: partially completed\n`;
    for (const [label, content, expected] of [
      ["bold", bold, "in progress"],
      ["plain", plain, "partially completed"],
    ]) {
      const { file, dir } = tmpPlan(`${label}.md`, content);
      try {
        const r = inspectPlan(file);
        assert.equal(r.planStatus, expected);
      } finally { rmSync(dir, { recursive: true, force: true }); }
    }
  });
});
