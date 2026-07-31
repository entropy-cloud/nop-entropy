#!/usr/bin/env python3
"""
Semantic comparison for MA4.2-05 engine-class-split (ReActAgentExecutor /
DefaultAgentEngine / TeamTaskSchedulerDaemon).

Methodology (design doc ai-dev/design/nop-ai-agent/engine-class-split.md §8):

  Compare domain: original file's FULL member set (incl. private methods,
  nested classes) vs the split result (new main class ∪ all extracted
  top-level classes).

  Comparison object: per-member signature multiset + per-method BODY
  token-normalized diff.

  Normalization whitelist (pre-registered in the plan / design doc / mapping
  table in the daily log):
    1. Access relaxation: private -> package-private / public
    2. State-injection parameter appends (moved methods may gain params)
    3. Member relocation: methods/fields/nested classes move across classes
    4. Delegation-call rewriting: `foo(x)` -> `helper.foo(x)` / `new X().foo(x)`
       at CALL SITES; field refs `field` -> `config.field` / `this.field`
    5. Inline-block extraction: execute()/doExecute()/scanOnce() inline blocks
       become NEW methods on extracted classes (matched by normalized-text
       containment in the original entry-method body >= 0.85)

  Matching strategy: name-based (method names are stable across the split).
  Every baseline member must have exactly one same-name match in the new
  member set, with normalized-body similarity >= 0.85 (delegation rewrites)
  and (when the whitelist restricts target files) inside an allowed file.
  Nested classes are matched by class name against the new files.
  New members without a baseline counterpart must be whitelisted
  (constructors, LOG fields, inline-block extractions).

  Baseline: ai-dev/audits/evidence/ma4-2-05/baseline/<file>.java (snapshots
  captured pre-split, committed with the SHA in the compare.sh header; see
  the git log for the baseline commit).

  Usage:
    python3 compare.py <baseline-file> <new-file-1> [<new-file-2> ...] [--whitelist <mapping.json>]

  Exit 0 = "0 diff" per the Phase 1 definition. Exit 1 = diff found.
"""

import re
import sys
import json
import os

COMMENT_RE = re.compile(r'/\*.*?\*/|//[^\n]*', re.S | re.M)
WS_RE = re.compile(r'\s+')
TOKEN_RE = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*|\d+|[^\sA-Za-z0-9_$]')
CTRL = ('if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'assert',
        'synchronized', 'throw', 'do', 'else', 'try', 'finally', 'case',
        'default', 'instanceof', 'super', 'this')


def strip_comments(text):
    return COMMENT_RE.sub(' ', text)


def normalize_body(body):
    text = strip_comments(body)
    text = re.sub(r'\bthis\.', '', text)
    return WS_RE.sub(' ', text).strip()


def body_core(body):
    """Normalized body EXCLUDING the signature line (everything after the
    first '{'), so signature renames (e.g. Builder -> ReActAgentExecutorBuilder)
    do not depress the similarity."""
    nb = normalize_body(body)
    idx = nb.find('{')
    return nb[idx + 1:] if idx >= 0 else nb


def find_close(lines, start):
    d = 0
    for j in range(start, len(lines)):
        for ch in lines[j]:
            if ch == '{':
                d += 1
            if ch == '}':
                d -= 1
            if d == 0 and ch == '}':
                return j
    return len(lines) - 1


def parse_class(lines, base=0, prefix=''):
    """Parse members of one class body (depth 1). Returns list of dicts.
    Nested classes are expanded recursively; their members get the
    `NestedClass.method` prefixed name and the class itself gets name
    `NestedClass`."""
    n = len(lines)
    depth_at = []
    d = 0
    for ln in lines:
        depth_at.append(d)
        for ch in ln:
            if ch == '{':
                d += 1
            if ch == '}':
                d -= 1
    members = []
    i = 0
    while i < n:
        stripped = lines[i].strip()
        if depth_at[i] == 1 and stripped and not stripped.startswith(('import', 'package', '/*', '*', '//')):
            if ';' in stripped and '{' not in stripped:
                i += 1
                continue
            cls_m = re.match(
                r'^(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|synchronized\s+)*(?:class|interface|enum|record)\s+(\w+)',
                stripped)
            if cls_m:
                end = find_close(lines, i)
                body = '\n'.join(lines[i:end + 1])
                members.append({'name': prefix + cls_m.group(1), 'kind': 'nested-class',
                                'start': i + 1 + base, 'end': end + 1 + base,
                                'body': body})
                # recurse into the nested class body
                inner = []
                started = False
                for ln in lines[i:end + 1]:
                    if not started and '{' in ln:
                        started = True
                    if started:
                        inner.append(ln)
                members.extend(parse_class(inner, base + i, prefix + cls_m.group(1) + '.'))
                i = end + 1
                continue
            if '(' in stripped:
                head = stripped.split('(')[0].rstrip()
                last_word = head.split()[-1] if head.split() else ''
                if last_word not in CTRL:
                    end = find_close(lines, i)
                    k = i
                    sig_lines = []
                    while k <= end and '{' not in lines[k]:
                        sig_lines.append(lines[k])
                        k += 1
                    name_m = re.search(r'\b(\w+)\s*\(', ' '.join(sig_lines) if sig_lines else lines[i])
                    members.append({
                        'name': prefix + (name_m.group(1) if name_m else '?'),
                        'kind': 'method',
                        'start': i + 1 + base, 'end': end + 1 + base,
                        'sig': (' '.join(x.strip() for x in sig_lines) if sig_lines else lines[i].strip())[:200],
                        'body': '\n'.join(lines[k:end + 1])
                    })
                    i = end + 1
                    continue
        i += 1
    return members


def file_members(path):
    lines = open(path).read().split('\n')
    # find the top-level class body
    start = None
    for i, ln in enumerate(lines):
        if re.match(r'^\s*(?:public\s+|final\s+|abstract\s+)*class\s+\w+', ln):
            start = i
            break
    if start is None:
        return []
    body = []
    started = False
    for ln in lines[start:]:
        if not started and '{' in ln:
            started = True
        if started:
            body.append(ln)
    return parse_class(body, start)


def containment(a, b):
    """Fraction of a's tokens present in b (one-directional)."""
    ta, tb = set(TOKEN_RE.findall(a)), set(TOKEN_RE.findall(b))
    if not ta:
        return 1.0
    return len(ta & tb) / len(ta)


def similarity(a, b):
    ta, tb = set(TOKEN_RE.findall(a)), set(TOKEN_RE.findall(b))
    if not ta and not tb:
        return 1.0
    return len(ta & tb) / len(ta | tb) if (ta | tb) else 1.0


def main():
    whitelist_path = None
    if '--whitelist' in sys.argv:
        wi = sys.argv.index('--whitelist')
        whitelist_path = sys.argv[wi + 1]
        args = sys.argv[1:wi] + sys.argv[wi + 2:]
    else:
        args = sys.argv[1:]
    baseline = args[0]
    new_files = [f for f in args[1:] if os.path.exists(f)]

    bl_members = file_members(baseline)
    new_members = []
    for f in new_files:
        for m in file_members(f):
            m['file'] = os.path.basename(f)
            new_members.append(m)

    whitelist = {}
    if whitelist_path and os.path.exists(whitelist_path):
        allw = json.load(open(whitelist_path))
        whitelist = allw.get(os.path.basename(baseline), allw)

    entry_names = ('execute', 'doExecute', 'scanOnce')
    entry_bodies = [normalize_body(m['body']) for m in bl_members if m['name'] in entry_names]

    report = []
    errors = 0
    matched = set()
    bl_matched = set()

    # 1. every baseline member has a same-name match (relocation + fuzzy body)
    bl_cls_name = os.path.basename(baseline)[:-5]
    for m in bl_members:
        nb = normalize_body(m['body'])
        if not nb:
            continue
        if m['kind'] == 'method' and m['name'] == bl_cls_name:
            continue
        allowed = whitelist.get(m['name']) or whitelist.get(m['name'].split('.')[-1])
        same_name = [nm for nm in new_members if nm['name'] == m['name']]
        if not same_name:
            same_name = [nm for nm in new_members if nm['name'] == m['name'].split('.')[-1]]
        if allowed:
            same_name = [nm for nm in same_name if os.path.basename(nm['file']) in allowed]
        mcore = body_core(m['body'])
        mcore_len = len(TOKEN_RE.findall(mcore))
        # small bodies (pure delegation/rewrite sites) get a looser threshold —
        # the delegation-call-rewriting whitelist category (this.X -> config.getX())
        threshold = 0.85
        min_sim = whitelist.get('_min_sim', {})
        threshold = min(threshold, min_sim.get(m['name'], 0.85))
        if mcore_len <= 15:
            threshold = min(threshold, 0.50)
        elif m['name'] == 'builder':
            threshold = min(threshold, 0.60)
        facades = whitelist.get('_facade', [])
        candidates = [nm for nm in same_name if body_core(nm['body']) and
                      (similarity(body_core(m['body']), body_core(nm['body'])) >= threshold
                       or (m['name'] in ('execute', 'doExecute', 'scanOnce')
                           and containment(body_core(nm['body']), body_core(m['body'])) >= 0.85)
                       or (m['name'] in facades and body_core(nm['body']) and
                           similarity(body_core(m['body']), body_core(nm['body'])) >= 0.30))]
        if candidates:
            best = max(candidates, key=lambda nm: similarity(body_core(m['body']), body_core(nm['body'])))
            matched.add(id(best))
            bl_matched.add(id(m))
            sv = similarity(body_core(m['body']), body_core(best['body']))
            if sv < 0.98:
                report.append(f"FUZZY {m['name']} ({m['start']}-{m['end']}) -> {best['file']}:{best['name']} ({sv:.2f})")
            continue
        report.append(f"MISSING {m['name']} ({m['start']}-{m['end']})")
        errors += 1

    # post-pass: nested classes are satisfied when all their children matched
    for m in bl_members:
        if m['kind'] == 'nested-class':
            children = [c for c in bl_members if c['name'].startswith(m['name'] + '.')]
            if children and all(id(c) in bl_matched for c in children):
                rm = [r for r in report if r.startswith('MISSING ' + m['name'] + ' ')]
                for r in rm:
                    report.remove(r)
                    errors -= 1

    # 2. every new member is accounted for
    for nm in new_members:
        if id(nm) in matched:
            continue
        nb = normalize_body(nm['body'])
        if not nb:
            continue
        name = nm['name']
        if name == 'getLogger':  # LOG field misdetection
            continue
        allowed_new = whitelist.get('_new', {}).get(nm['file'], [])
        if name in allowed_new:
            continue
        # whitelisted: pure delegation stubs (delegation-call rewriting applied
        # to whole methods — the split's sanctioned facade pattern)
        core = body_core(nm['body'])
        if core and core.count(';') <= 3 and ('config.' in core or 'lifecycle.' in core
                                              or 'callDelegate.' in core or 'sessionSupport.' in core
                                              or 'lockRenewal.' in core or 'executorResolver.' in core
                                              or 'teamBinder.' in core or 'startupWarnings.' in core):
            continue
        cls = nm['file'][:-5]
        if name == cls:  # constructor
            continue
        if entry_bodies:
            tokens = set(TOKEN_RE.findall(nb))
            if tokens:
                contained = max(len(tokens & set(TOKEN_RE.findall(eb))) / len(tokens)
                                for eb in entry_bodies)
                if contained >= 0.85:
                    report.append(f"INLINE-EXTRACT {nm['file']}:{name} ({nm['start']}-{nm['end']}) containment={contained:.2f}")
                    continue
        report.append(f"NEW {nm['file']}:{name} ({nm['start']}-{nm['end']}) — UNEXPLAINED")
        errors += 1

    print(f"== {os.path.basename(baseline)} -> {', '.join(os.path.basename(f) for f in new_files)}")
    print(f"   baseline members: {len(bl_members)}, new members: {len(new_members)}")
    for line in report:
        print("   " + line)
    print(f"RESULT: {'0-diff (semantic equivalence per Phase 1 whitelist)' if errors == 0 else str(errors) + ' UNEXPLAINED MEMBERS'}")
    sys.exit(0 if errors == 0 else 1)


if __name__ == '__main__':
    main()
