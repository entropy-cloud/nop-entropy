import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { _scanOpenAuditsList } from "../src/flow-loader.js";

// mdc-1 (convergence R1): the audit report keeps `> Audit Status: open` as-is,
// but draft-from-audit now closes P2-only audits with `> Audit Status: triaged`
// (reviewed, no P0/P1, no plan). `triaged` — like `planned` — is a terminal,
// non-open state that MUST NOT be counted by openAudits(), so a P2-only audit
// stops keeping the mission in the DEEP_AUDIT loop. This pins the flow-loader
// side of that contract so a future loosening of the status match regresses
// loudly instead of silently reviving the 3-round spin.

function auditFile(status) {
  return [
    `> Audit Status: ${status}`,
    `> Audit Type: multi-dimensional`,
    `> Mission: convergence-test`,
    ``,
    `# Audit`,
    `[P2] cosmetic nit`,
    ``,
  ].join("\n");
}

describe("mdc-1 R1 — _scanOpenAuditsList status filtering", () => {
  it("counts `open` but excludes `triaged` and `planned` mission-level audits", () => {
    const dir = mkdtempSync(join(tmpdir(), "mdc-audits-"));
    try {
      writeFileSync(join(dir, "a-open-multi-audit-x.md"), auditFile("open"));
      writeFileSync(join(dir, "b-triaged-multi-audit-x.md"), auditFile("triaged"));
      writeFileSync(join(dir, "c-planned-multi-audit-x.md"), auditFile("planned"));

      const open = _scanOpenAuditsList(dir);
      const names = open.map((p) => p.replace(/\\/g, "/").split("/").pop());

      assert.equal(open.length, 1, "only the `open` audit must be counted");
      assert.ok(names.includes("a-open-multi-audit-x.md"), "the open audit is counted");
      assert.ok(!names.includes("b-triaged-multi-audit-x.md"),
        "triaged audit MUST NOT be counted (P2-only closure must not keep the mission in the audit loop)");
      assert.ok(!names.includes("c-planned-multi-audit-x.md"),
        "planned audit MUST NOT be counted (already drafted)");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("a directory of only triaged audits yields zero open audits (clean short-circuit input)", () => {
    const dir = mkdtempSync(join(tmpdir(), "mdc-audits-"));
    try {
      writeFileSync(join(dir, "x-triaged-multi-audit-x.md"), auditFile("triaged"));
      writeFileSync(join(dir, "y-triaged-open-audit-x.md"),
        auditFile("triaged").replace("multi-dimensional", "open-ended"));
      assert.deepEqual(_scanOpenAuditsList(dir), [],
        "P2-only round → all audits triaged → openAudits()==0 feeds the engine clean short-circuit");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});
