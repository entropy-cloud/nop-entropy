#!/usr/bin/env python3
# Wave 0 measurement (roadmap item 3): module capability classification L0-L3,
# per design 01 §六, using evidence signals (NOT inherited effective-pom
# declarations, which over-trigger: the root/nop-kernel parent poms declare the
# `aop`/`precompile` exec executions and Maven inherits them into every
# descendant — 134 modules — while the codegen/AOP tools no-op without
# templates/annotations).
#
# Evidence rules:
#   L3 (always fallback): *__aop.class present in target/classes (AOP products,
#      design 01 I3: 10 leaf modules measured) OR own-pom skipTests=true.
#   L2 (module fallback): effective compiler execution carries
#      annotationProcessorPaths or explicit <proc>.
#   L1 (generation-aware): module owns gen-*.xgen template dirs
#      (precompile/precompile2/postcompile).
#   L0: everything else (fast path).
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "{http://maven.apache.org/POM/4.0.0}"

def main(dump_path, repo_root):
    root_path = Path(repo_root)
    # L3 evidence: AOP artifacts actually present in built outputs
    aop_modules = set()
    for p in root_path.glob("**/target/classes/**/*__aop.class"):
        rel = p.relative_to(root_path).as_posix()
        m = re.match(r"^(.*)/target/classes/", rel)
        if m:
            aop_modules.add(m.group(1))
    # L1 evidence: modules owning gen-*.xgen templates
    l1_modules = set()
    for p in root_path.glob("**/gen-*.xgen"):
        rel = p.relative_to(root_path).as_posix()
        if "/target/" in rel:
            continue
        # module dir = path prefix before /src/ or /precompile|precompile2|postcompile
        l1_modules.add(re.sub(r"/(src|precompile2?|postcompile)/.*$", "", rel))

    tree = ET.parse(dump_path)
    projs = tree.getroot().findall(NS + "project")
    levels = {}
    details = {}
    for proj in projs:
        aid_el = proj.find(NS + "artifactId")
        parent_el = proj.find(NS + "parent")
        aid = aid_el.text.strip() if aid_el is not None and aid_el.text else "?"
        # locate module dir: repo-relative dirs are <groupPath>/<artifactId>
        # we match by trailing directory name == artifactId owning a pom.xml
        mdir = None
        for cand in aop_modules | l1_modules:
            if cand.endswith("/" + aid):
                mdir = cand
                break
        level = "L0"
        why = []
        if mdir and mdir in aop_modules:
            level, why = "L3", why + ["aop-artifacts"]
        # L2: effective compiler executions with processors / explicit proc
        for plug in proj.iter(NS + "plugin"):
            g = plug.find(NS + "groupId")
            a = plug.find(NS + "artifactId")
            if a is None or a.text != "maven-compiler-plugin":
                continue
            for cfg in plug.iter(NS + "configuration"):
                if cfg.find(NS + "annotationProcessorPaths") is not None:
                    level = "L3" if level == "L3" else "L2"
                    why.append("annotationProcessorPaths")
                proc = cfg.find(NS + "proc")
                if proc is not None and proc.text:
                    level = "L3" if level == "L3" else "L2"
                    why.append("proc=" + proc.text.strip())
            break
        if level == "L0" and mdir and mdir in l1_modules:
            level, why = "L1", why + ["xgen-templates"]
        levels[aid] = level
        details[aid] = why

    total = len(levels)
    counts = {}
    for k in ("L3", "L2", "L1", "L0"):
        counts[k] = [a for a, v in levels.items() if v == k]
    print(f"total reactor projects: {total}")
    for k in ("L3", "L2", "L1", "L0"):
        print(f"{k}: {len(counts[k])} ({100.0*len(counts[k])/max(total,1):.1f}%)")
    for a in counts["L3"]:
        print(f"  L3 {a}: {';'.join(details[a]) or '(effective fact)'}")
    l2 = [a for a in counts["L2"]]
    print("L2:", ", ".join(l2) if l2 else "(none)")
    l3_note = len(aop_modules)
    print(f"(aop-artifact module dirs on disk: {l3_note})")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
