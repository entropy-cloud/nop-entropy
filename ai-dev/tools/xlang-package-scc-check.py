#!/usr/bin/env python3
"""Round-2: counterfactual SCC analysis for nop-xlang package graph.
Experiments:
  E1: remove XLangConstants node -> is the 21-node SCC a fake-hub artifact?
  E2: remove candidate thin edges (ast->xpl, api->scope, utils->xdef, exec->xpl, expr->compile, api->xpl subset) -> which single edge breaks most?
  E3: enumerate simple cycles length>=3 NOT passing XLangConstants, with file-level chains.
  E4: edge classification per 2-cycle edge: interface<->impl (api/IXxx, I-prefixed) vs concrete.
"""
import os, re, collections, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'nop-kernel/nop-xlang'
root = os.path.join(ROOT, 'src/main/java/io/nop/xlang')
pkgs = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
ROOTFILES = {'XLangConfigs', 'XLangConstants', 'XLangErrors'}

filepkg = {}
for dirpath, _, files in os.walk(root):
    for f in files:
        if f.endswith('.java'):
            rel = os.path.relpath(os.path.join(dirpath, f), root)
            stem = f[:-5]
            if stem in ROOTFILES:
                top = stem
            elif os.sep in rel:
                top = rel.split(os.sep)[0]
            else:
                top = '(root-other)'
            filepkg[os.path.join(dirpath, f)] = top

imp_re = re.compile(r'^import\s+(?:static\s+)?((?:io\.nop\.xlang)(?:\.[A-Za-z0-9_]+)*)\s*;')

def classify(fqcn):
    segs = fqcn.split('.')
    if len(segs) == 3:
        return '(root-other)'
    t = segs[3]
    if t in ROOTFILES:
        return t
    if t[0].isupper():
        return '(root-other)'
    return t

edge_imp = collections.Counter()
edge_files = collections.defaultdict(set)   # (a,b) -> set of (file-rel, lineno, fqcn, simple-class-name)
for path, frompkg in sorted(filepkg.items()):
    with open(path, encoding='utf-8') as fh:
        for lineno, line in enumerate(fh, 1):
            m = imp_re.match(line.strip())
            if not m:
                continue
            fq = m.group(1)
            topkg = classify(fq)
            if topkg == frompkg:
                continue
            edge_imp[(frompkg, topkg)] += 1
            edge_files[(frompkg, topkg)].add(
                (os.path.relpath(path, ROOT), lineno, fq, fq.rsplit('.', 1)[-1]))

def build_graph(exclude_nodes=(), exclude_edges=()):
    g = collections.defaultdict(set)
    for (a, b) in edge_imp:
        if a in exclude_nodes or b in exclude_nodes:
            continue
        if (a, b) in exclude_edges:
            continue
        g[a].add(b)
    return g

def tarjan_sccs(g, nodes):
    index = {}; low = {}; onstk = set(); stk = []; sccs = []; counter = [0]
    sys.setrecursionlimit(10000)
    def strongconnect(v):
        index[v] = low[v] = counter[0]; counter[0] += 1
        stk.append(v); onstk.add(v)
        for w in sorted(g.get(v, ())):
            if w not in index:
                strongconnect(w)
                low[v] = min(low[v], low[w])
            elif w in onstk:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            scc = []
            while True:
                w = stk.pop(); onstk.discard(w); scc.append(w)
                if w == v: break
            sccs.append(sorted(scc))
    for v in nodes:
        if v not in index:
            strongconnect(v)
    return sccs

all_nodes = ['(root-other)'] + sorted(ROOTFILES) + pkgs

print("=== E0: current SCC baseline (red line: members must not grow beyond post-R1.1 baseline of 20; compile must stay out) ===")
g0 = build_graph()
sccs0 = [s for s in tarjan_sccs(g0, all_nodes) if len(s) > 1]
for s in sccs0:
    print(f"  SCC({len(s)}): {' | '.join(sorted(s))}")
print(f"  compile in SCC: {any('compile' in s for s in sccs0)}")

print("\n=== E1: counterfactual — remove XLangConstants ===")
g = build_graph(exclude_nodes={'XLangConstants'})
sccs = [s for s in tarjan_sccs(g, [n for n in all_nodes if n != 'XLangConstants']) if len(s) > 1]
print(f"non-trivial SCCs after removing XLangConstants: {len(sccs)}")
for s in sccs:
    print(f"  SCC({len(s)}): {' | '.join(s)}")

print("\n=== E1b: counterfactual — remove XLangConstants AND the 4 constant-impl 2-cycles (filter/xdsl/xmeta/xpl back-edges) ===")
# constant back edges are X -> XLangConstants imports; removing XLangConstants already kills them. Just show remaining.

print("\n=== E2: single-edge removal experiments (with XLangConstants present) ===")
base = build_graph()
cand_edges = [
    ('ast', 'xpl'),          # 1 import: XLangASTNode? check
    ('api', 'scope'),
    ('utils', 'xdef'),
    ('exec', 'xpl'),
    ('expr', 'compile'),
    ('api', 'xpl'),
    ('scope', 'xpl'),
    ('expr', 'xpl'),
    ('delta', 'xdsl'),
    ('feature', 'xdsl'),
    ('xdsl', 'delta'),
    ('xdsl', 'feature'),
    ('api', 'ast'),
    ('scope', 'ast'),
    ('xmeta', 'xdef'),
    ('xdsl', 'xdef'),
    ('xmeta', 'xdsl'),
    ('xdef', 'xdsl'),
    ('xdef', 'xmeta'),
    ('xdsl', 'xmeta'),
]
for e in cand_edges:
    if e not in edge_imp:
        print(f"  remove {e[0]}->{e[1]}: edge does not exist")
        continue
    g2 = build_graph(exclude_edges={e})
    sccs2 = [s for s in tarjan_sccs(g2, all_nodes) if len(s) > 1]
    sizes = '+'.join(str(len(s)) for s in sccs2) if sccs2 else 'DAG'
    print(f"  remove {e[0]}->{e[1]} ({edge_imp[e]} imp, {len(edge_files[e])}f): {len(sccs2)} SCCs [{sizes}]")

print("\n=== E2b: greedy sequential cut (each step: best single edge) ===")
remaining = set()
gcur = build_graph()
step = 0
while True:
    sccs_cur = [s for s in tarjan_sccs(gcur, all_nodes) if len(s) > 1]
    if not sccs_cur:
        print(f"  step {step}: DAG reached")
        break
    step += 1
    best = None
    for e in list(edge_imp):
        if e in remaining:
            continue
        g2 = build_graph(exclude_edges=remaining | {e})
        s2 = [s for s in tarjan_sccs(g2, all_nodes) if len(s) > 1]
        score = (len(s2), -edge_imp[e])
        if best is None or score < best[0]:
            best = (score, e, s2)
    score, e, s2 = best
    remaining.add(e)
    sizes = '+'.join(str(len(s)) for s in s2) if s2 else 'DAG'
    print(f"  step {step}: cut {e[0]}->{e[1]} ({edge_imp[e]} imp/{len(edge_files[e])}f) -> {len(s2)} SCCs [{sizes}]")
    if step > 30:
        break

print("\n=== E3: simple cycles length>=3 avoiding XLangConstants (package level, capped) ===")
g3 = build_graph(exclude_nodes={'XLangConstants'})
cycles = []
def dfs(v, start, path, visited):
    if len(cycles) >= 400:
        return
    for w in sorted(g3.get(v, ())):
        if w == start and len(path) >= 3:
            cycles.append(path + [w])
        elif w not in visited and w > start and len(path) < 6:
            dfs(w, start, path + [w], visited | {w})
for n in sorted(g3):
    dfs(n, n, [n], {n})
print(f"total simple cycles len>=3 (canonical, avoiding XLangConstants, len<=6): {len(cycles)}")
from collections import Counter as C
lens = C(len(c) - 1 for c in cycles)
print(f"by length: {dict(sorted(lens.items()))}")
# which edges appear most often across cycles
edgeuse = C()
for c in cycles:
    for i in range(len(c) - 1):
        edgeuse[(c[i], c[i + 1])] += 1
print("top edges used in cycles:", edgeuse.most_common(12))

print("\n=== E4: file-level chains for representative cycles ===")
def edge_detail(a, b, limit=3):
    out = []
    for (f, ln, fq, cls) in sorted(edge_files[(a, b)]):
        out.append(f"      {f}:{ln}  imports {fq}")
        limit -= 1
        if limit <= 0:
            break
    return out

# representative cycles chosen: pick 5 distinct ones spanning different regions
picked = []
seen_set = set()
for c in cycles:
    key = frozenset(zip(c, c[1:]))
    # pick cycles that use different edge sets
    if key & seen_set:
        continue
    picked.append(c)
    seen_set |= key
    if len(picked) >= 6:
        break
for c in picked:
    print(f"  CYCLE: {' -> '.join(c)}")
    for i in range(len(c) - 1):
        a, b = c[i], c[i + 1]
        print(f"    {a} -> {b} ({edge_imp[(a,b)]} imp):")
        for line in edge_detail(a, b):
            print(line)
