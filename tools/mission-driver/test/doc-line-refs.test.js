import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { findLineRefs } from "../../check-doc-references.mjs";

// mdc-1 (convergence R3): owner / architecture docs must cite code by function
// or anchor name, never by `file.ext:NNN` line numbers. Line numbers rot the
// instant an unrelated edit shifts the target, which re-surfaces as a recurring
// P2 audit finding (the "line-number drift" self-perpetuation loop that made a
// clean run keep drafting doc-sync remediation plans). This test pins both the
// detector itself and the current cleanliness of the architecture docs.

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(HERE, "..", "..", "..");

function walkMarkdown(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const name of entries) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...walkMarkdown(full));
    else if (name.endsWith(".md")) out.push(full);
  }
  return out;
}

describe("mdc-1 R3 — findLineRefs detector", () => {
  it("flags file.ext:NNN citations (single line and ranges)", () => {
    const hits = findLineRefs(
      "See `src/engine.js:442` and open-audit.md:14 plus a range engine.js:427-436.",
    );
    const refs = hits.map((h) => h.ref);
    assert.ok(refs.includes("src/engine.js:442"), "single-line citation flagged");
    assert.ok(refs.includes("open-audit.md:14"), "bare filename:line flagged");
    assert.ok(refs.includes("engine.js:427-436"), "line-range citation flagged");
  });

  it("does NOT flag anchor-style or non-code references", () => {
    const hits = findLineRefs(
      "`_writeWorkflow` in `src/engine.js`; see http://host:8080; localhost:3000; ratio 3.5:1.",
    );
    assert.equal(hits.length, 0, `must not false-positive; got ${JSON.stringify(hits)}`);
  });
});

describe("mdc-1 R3 — architecture docs are line-number-citation free", () => {
  it("docs/architecture/*.md contain no file:NNN citations", () => {
    const archDir = join(REPO_ROOT, "docs", "architecture");
    const offenders = [];
    for (const file of walkMarkdown(archDir)) {
      const refs = findLineRefs(readFileSync(file, "utf8"));
      if (refs.length > 0) {
        offenders.push(`${relative(REPO_ROOT, file)} → ${refs.map((r) => r.ref).join(", ")}`);
      }
    }
    assert.deepEqual(offenders, [],
      `architecture docs must cite code by anchor, not line number:\n${offenders.join("\n")}`);
  });
});
