import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { SCRIPT_REGISTRY } from "../src/flow-loader.js";

const PLAN_WITH_UNCHECKED = `# 99 test plan

> **Plan Status**: planned
> **Module**: test-mod
> Last Reviewed: 2026-06-14

## Goals

- goal a

## Non-Goals

- none

## Current Baseline

- baseline

## Execution Plan

### Phase 1 - build

Status: planned

- [ ] do task one
- [ ] do task two

Exit Criteria:

- [ ] task one done
- [ ] task two done

## Closure Gates

- [ ] all done

## Closure

Status Note: *(pending)*

Closure Audit Evidence:

- *(pending)*

Follow-up:

- none
`;

const PLAN_ALL_CHECKED_COMPLETED = `# 98 test plan

> **Plan Status**: completed
> **Module**: test-mod
> Last Reviewed: 2026-06-14

## Goals

- goal a

## Non-Goals

- none

## Current Baseline

- baseline

## Execution Plan

### Phase 1 - build

Status: completed

- [x] do task one

Exit Criteria:

- [x] task one done

## Closure Gates

- [x] all done

## Closure

Status Note: finished all work and verified tests pass.

Closure Audit Evidence:

Reviewer / Agent: test-agent
Evidence:
  - src/Foo.java implemented
  - TestFoo.java passes (10/10)
  - check-plan-checklist strict exits 0

Follow-up:

- none
`;

let tmpDir;

describe("closure-script-check (SCRIPT_REGISTRY)", () => {
  let check;

  before(() => {
    check = SCRIPT_REGISTRY["closure-script-check"];
    tmpDir = mkdtempSync(join(process.cwd(), "_tmp", "ogd-closure-"));
  });

  after(() => {
    try { rmSync(tmpDir, { recursive: true, force: true }); } catch {}
  });

  function writePlan(name, content) {
    const p = join(tmpDir, name);
    writeFileSync(p, content);
    return p;
  }

  function makeVars(planPath) {
    const m = new Map();
    m.set("PLAN_FILE", planPath);
    return m;
  }

  it("reports FAIL when unchecked items remain (regression: was always PASS)", async () => {
    const plan = writePlan("unchecked.md", PLAN_WITH_UNCHECKED);
    const vars = makeVars(plan);
    const result = await check({}, vars);
    assert.equal(result, "fail");
    assert.equal(vars.get("SCRIPT_CHECK_RESULT"), "FAIL");
    assert.match(vars.get("SCRIPT_CHECK_DETAILS"), /unchecked items remain/i);
  });

  it("reports PASS when all items checked and closure evidence present", async () => {
    const plan = writePlan("complete.md", PLAN_ALL_CHECKED_COMPLETED);
    const vars = makeVars(plan);
    const result = await check({}, vars);
    assert.equal(result, "pass");
    assert.equal(vars.get("SCRIPT_CHECK_RESULT"), "PASS");
  });

  it("reports FAIL for a completed-phase plan that still has [ ] items (plan-180 scenario)", async () => {
    const content = PLAN_WITH_UNCHECKED.replace(
      "Status: planned",
      "Status: completed"
    );
    const plan = writePlan("status-mismatch.md", content);
    const vars = makeVars(plan);
    const result = await check({}, vars);
    assert.equal(result, "fail");
    assert.match(vars.get("SCRIPT_CHECK_DETAILS"), /unchecked items remain/i);
  });

  it("reports FAIL when PLAN_FILE is missing in flowVars", async () => {
    const vars = new Map();
    const result = await check({}, vars);
    assert.equal(result, "fail");
  });
});
