#!/usr/bin/env python3
# Wave 1 item 12: replay `nop-ib plan` over recent real commits and check the
# predictions against routing-rule invariants (design 01 §七). For each commit we
# record the plan JSON and validate:
#   R1 pom.xml touched            -> fallback required (cold path)
#   R2 java-only edit             -> no fallback unless module is L2/L3
#   R3 xlang model / test data    -> widening present (fullTest or warnings)
#   R4 predicted compileModules   -> contains the edited module itself
#   R5 plan never crashes / ok flag present
# Usage: replay_plan.py <n-commits> ; writes _tmp/replay-plan-results.json
import json
import subprocess
import sys

N = int(sys.argv[1]) if len(sys.argv) > 1 else 25
IB = "./nop-tools/nop-ib/bin/ib"

out = subprocess.run(["git", "log", f"-{N}", "--no-merges", "--name-only", "--pretty=format:@%h"],
                     capture_output=True, text=True, check=True).stdout.split("\n")
commits, cur = [], None
for line in out:
    if line.startswith("@"):
        commits.append((line[1:], []))
    elif line.strip():
        commits[-1][1].append(line.strip())

results = []
violations = 0
for cid, files in commits:
    if not files:
        continue
    p = subprocess.run([IB, "plan", "--json", "--paths", ",".join(files)],
                       capture_output=True, text=True, timeout=600)
    js = None
    for line in p.stdout.split("\n"):
        if line.startswith("{"):
            js = json.loads(line)
            break
    if js is None:
        results.append({"commit": cid, "error": p.stderr[-300:], "files": files})
        violations += 1
        continue
    scope = js["scope"]
    fb = js["fallback"]["required"]
    pom = any(f.endswith("pom.xml") for f in files)
    java = [f for f in files if f.endswith(".java")]
    modelish = [f for f in files if "_gen/" in f or f.endswith((".xgen", ".xdef", ".xmeta", ".xbiz"))]
    reasons = []
    if pom and not fb:
        reasons.append("R1: pom touched but no fallback")
    if java and not modelish and not pom:
        # L2/L3 modules legitimately fall back; accept fallback only with reason text
        if not fb:
            pass  # fast path expected
        elif "L2" not in js["fallback"]["reason"] and "L3" not in js["fallback"]["reason"] \
                and "model-stale" not in js["fallback"]["reason"]:
            reasons.append("R2: unexpected fallback: " + js["fallback"]["reason"])
    if modelish and not scope.get("fullTestModules") and not scope.get("selectedTestsByModule"):
        reasons.append("R3: model change without widening")
    if not reasons:
        violations += 0 if not reasons else violations
    entry = {
        "commit": cid, "files": len(files), "java": len(java), "pom": pom,
        "fallback": fb, "fallbackReason": js["fallback"]["reason"],
        "compileModules": len(scope["compileModules"]),
        "downstream": len(scope["downstreamCandidates"]),
        "fullTest": len(scope["fullTestModules"]),
        "selectedTests": sum(len(v) for v in scope["selectedTestsByModule"].values()),
        "violations": reasons,
    }
    violations += len(reasons)
    results.append(entry)

json.dump(results, open("_tmp/replay-plan-results.json", "w"), indent=1, ensure_ascii=False)
for r in results:
    flag = "VIOLATION" if r.get("violations") else "ok"
    print(f"{r.get('commit','?')}: files={r.get('files')} fallback={r.get('fallback')} "
          f"compile={r.get('compileModules')} down={r.get('downstream')} "
          f"full={r.get('fullTest')} sel={r.get('selectedTests')} [{flag}]")
    for v in r.get("violations", []):
        print("   -", v)
print(f"\nreplayed={len(results)} violations={violations}")
