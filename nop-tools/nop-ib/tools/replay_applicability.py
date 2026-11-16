#!/usr/bin/env python3
# Wave 0 measurement (roadmap item 3): applicability replay over recent real edits,
# per design 01 §七 routing. An edit can take the compile fast path only when ALL
# changed files are hand-written main/test Java sources in a fast-path-eligible
# context (no pom, no _gen, no XLang model/template, no test-data/resources).
import subprocess
import sys
from collections import Counter

N = int(sys.argv[1]) if len(sys.argv) > 1 else 25

def classify(f):
    if f.endswith(".java"):
        if "/_gen/" in f:
            return "generated-java"      # fallback (L1 generated product)
        if "/src/test/" in f:
            return "test-java"           # test-only: compile fast path + module tests
        return "main-java"               # fast path candidate
    if f.endswith("pom.xml"):
        return "pom"                     # cold path
    if "/_gen/" in f or f.endswith(".xgen") or f.endswith(".xdef") or f.endswith(".xmeta") \
       or f.endswith(".xplanet") or f.endswith(".xbiz") or f.endswith(".xwf") or f.endswith(".orm.xml") \
       or f.endswith(".xjob") or f.endswith(".xtask") or f.endswith(".xlib") or f.endswith(".xdsl"):
        return "xlang-model-or-gen"      # fallback + consumer full tests
    if "/src/main/resources/" in f or "/src/test/resources/" in f:
        return "resources"               # test widening / fallback per relaxation matrix
    return "other"

commits = subprocess.run(["git", "log", f"-{N}", "--no-merges", "--name-only", "--pretty=format:@%h"],
                         capture_output=True, text=True, check=True).stdout.split("\n")
buckets = Counter()
commits_with_fastpath = 0
per_commit = []
cur = None
files = []
for line in commits:
    if line.startswith("@"):
        if cur and files:
            per_commit.append((cur, files))
        cur = line[1:]
        files = []
    elif line.strip():
        files.append(line.strip())
if cur and files:
    per_commit.append((cur, files))

for cid, fs in per_commit:
    kinds = [classify(f) for f in fs]
    buckets.update(kinds)
    # strict fast-path eligibility: every file is main-java or test-java, no gen
    eligible = all(k in ("main-java", "test-java") for k in kinds) and any(k == "main-java" for k in kinds)
    commits_with_fastpath += 1 if eligible else 0

total_files = sum(buckets.values())
print(f"commits replayed: {len(per_commit)}")
print(f"files total: {total_files}")
for k, v in buckets.most_common():
    print(f"  {k}: {v} ({100.0*v/total_files:.1f}%)")
fast_files = buckets["main-java"]
print(f"fast-path-eligible commits: {commits_with_fastpath}/{len(per_commit)} ({100.0*commits_with_fastpath/len(per_commit):.1f}%)")
print(f"fast-path-eligible main-java file share: {100.0*fast_files/total_files:.1f}%")
