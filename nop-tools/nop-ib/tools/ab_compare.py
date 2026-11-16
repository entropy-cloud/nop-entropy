#!/usr/bin/env python3
# Wave 2 item 17: product comparison harness. Snapshot a module's target/classes
# (class set + per-class sha256 + ABI digest via the nop-ib unit index) after a
# clean `mvn compile` and after a fast-path `nop-ib compile`; require zero
# difference. Usage:
#   ab_compare.py <module-dir-relative> snapshot <out.json>
#   ab_compare.py <module-dir-relative> compare <baseline.json> [--bytes]
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()

def snapshot(classes_dir):
    out = {}
    for p in sorted(Path(classes_dir).rglob("*.class")):
        rel = p.relative_to(classes_dir).as_posix()
        out[rel] = sha(p)
    return out

def main():
    module = sys.argv[1]
    cmd = sys.argv[2]
    classes_dir = Path(module) / "target" / "classes"
    if cmd == "snapshot":
        Path(sys.argv[3]).write_text(json.dumps(snapshot(classes_dir), indent=0, sort_keys=True))
        print(f"snapshot: {len(snapshot(classes_dir))} classes -> {sys.argv[3]}")
    elif cmd == "compare":
        baseline = json.loads(Path(sys.argv[3]).read_text())
        current = snapshot(classes_dir)
        missing = sorted(set(baseline) - set(current))
        extra = sorted(set(current) - set(baseline))
        diff = sorted(k for k in set(baseline) & set(current) if baseline[k] != current[k])
        print(f"baseline={len(baseline)} current={len(current)} "
              f"missing={len(missing)} extra={len(extra)} bytes-differ={len(diff)}")
        if missing:
            print("MISSING:", missing[:10])
        if extra:
            print("EXTRA:", extra[:10])
        if diff:
            print("BYTES-DIFFER:", diff[:10])
        if missing or extra or diff:
            sys.exit(1)
        print("PARITY OK (class set + per-class bytes identical)")
    else:
        sys.exit("usage: snapshot|compare")

if __name__ == "__main__":
    main()
