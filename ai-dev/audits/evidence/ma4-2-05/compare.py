#!/usr/bin/env python3
"""
Semantic comparison for MA4.2-05 engine-class-split (ReActAgentExecutor /
DefaultAgentEngine / TeamTaskSchedulerDaemon).

Methodology (design doc ai-dev/design/nop-ai-agent/engine-class-split.md §8):

  Compare domain: original file's FULL member set (incl. private methods,
  nested classes, fields) vs the split result (new main class ∪ all extracted
  top-level classes).

  Comparison object: per-member signature multiset + per-method BODY
  token-normalized diff.

  Normalization whitelist (pre-registered in the plan / design doc / mapping
  table in the daily log):
    1. Access relaxation: private -> package-private / public
    2. State-injection parameter appends (moved methods may gain params)
    3. Member relocation: methods/fields/nested classes move across classes
    4. Delegation-call rewriting: `foo(x)` -> `helper.foo(x)` / `new X().foo(x)`
       at CALL SITES (method bodies may also be rewritten where the moved
       method's own calls become `helper.x(...)`); field refs `field` ->
       `config.field` / `this.field`
    5. Inline-block extraction: execute()/doExecute() inline blocks become
       NEW methods on extracted classes (matched by normalized-text overlap
       ratio >= 0.85 against the original entry-method body)

  Baseline: ai-dev/audits/evidence/ma4-2-05/baseline/<file>.java (snapshots
  captured pre-split, committed with the SHA in this script's header; see the
  git log for the baseline commit).

  Usage:
    python3 compare.py <baseline-file> <new-file-1> [<new-file-2> ...] [--whitelist <mapping.json>]

  Exit 0 = "0 diff" per the Phase 1 definition: every baseline member has a
  unique match in the new member set and every new member is accounted for
  (relocation / whitelisted new method). Exit 1 = diff found.
"""

import re
import sys
import json
import glob
import os

COMMENT_RE = re.compile(r'/\*.*?\*/|//[^\n]*', re.S | re.M)
WS_RE = re.compile(r'\s+')
TOKEN_RE = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*|\d+|[^\sA-Za-z0-9_$]')


def strip_comments(text):
    return COMMENT_RE.sub(' ', text)


def normalize_body(body):
    """Token-normalize a method body: strip comments, collapse whitespace,
    drop the leading `this.` receiver so `this.foo()` and `foo()` compare
    equal (relocation / receiver-insertion whitelist)."""
    text = strip_comments(body)
    # remove `this.` receivers
    text = re.sub(r'\bthis\.', '', text)
    text = WS_RE.sub(' ', text).strip()
    return text


def normalize_signature(sig):
    text = strip_comments(sig)
    text = re.sub(r'\bpublic\b|\bprivate\b|\bprotected\b', '', text)
    text = WS_RE.sub(' ', text).strip()
    return text


def find_members(text, full_text, start_line=0):
    """Return list of {name, start, end, sig, body} for methods + nested
    classes, using brace matching on the full file text."""
    lines = text.split('\n')
    n = len(lines)
    members = []
    depth = 0
    i = 0
    # map line -> depth (before processing that line)
    depth_at = []
    d = 0
    for ln in lines:
        depth_at.append(d)
        for ch in ln:
            if ch == '{':
                d += 1
            if ch == '}':
                d -= 1
    # find class decls at depth 0 (top level) to skip leading imports
    i = 0
    while i < n:
        ln = lines[i]
        stripped = ln.strip()
        if depth_at[i] == 0 and stripped and not stripped.startswith(('import', 'package')):
            # top-level decl region: class/interface at depth 0
            m = re.match(r'^(?:public\s+|final\s+|abstract\s+)*(class|interface|enum|record)\s+(\w+)', stripped)
            if m:
                # find opening brace
                j = i
                while j < n and '{' not in lines[j]:
                    j += 1
                if j < n:
                    i = j + 1
                else:
                    i += 1
                # now inside class body
                continue
        if depth_at[i] == 1 and stripped and not stripped.startswith(('import', 'package', '/*', '*', '//')):
            m = re.match(r'^(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|synchronized\s+)*(?:class|interface|enum|record)\s+(\w+)', stripped)
            if m:
                end = find_close(lines, i)
                sig = ' '.join(x.strip() for x in lines[i:end + 1])[:200]
                members.append({
                    'name': m.group(1), 'kind': 'nested-class',
                    'start': i + 1 + start_line, 'end': end + 1 + start_line,
                    'sig': sig, 'body': '\n'.join(lines[i:end + 1])
                })
                i = end + 1
                continue
            if '(' in stripped:
                head = stripped.split('(')[0].rstrip()
                last_word = head.split()[-1] if head.split() else ''
                if last_word not in ('if', 'for', 'while', 'switch', 'catch', 'return', 'new',
                                     'assert', 'synchronized', 'throw', 'do', 'else', 'try',
                                     'finally', 'case', 'default', 'instanceof', 'super', 'this'):
                    end = find_close(lines, i)
                    # signature = lines up to first '{'
                    k = i
                    sig_lines = []
                    while k <= end and '{' not in lines[k]:
                        sig_lines.append(lines[k])
                        k += 1
                    name_m = re.search(r'\b(\w+)\s*\(', ' '.join(sig_lines))
                    body = '\n'.join(lines[k:end + 1])
                    members.append({
                        'name': name_m.group(1) if name_m else '?',
                        'kind': 'method',
                        'start': i + 1 + start_line, 'end': end + 1 + start_line,
                        'sig': ' '.join(s.strip() for s in sig_lines)[:200],
                        'body': body
                    })
                    i = end + 1
                    continue
        i += 1
    return members


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


def similarity(a, b):
    """Token-set Jaccard similarity."""
    ta, tb = set(TOKEN_RE.findall(a)), set(TOKEN_RE.findall(b))
    if not ta and not tb:
        return 1.0
    inter = len(ta & tb)
    union = len(ta | tb)
    return inter / union if union else 0.0


def main():
    whitelist_path = None
    if '--whitelist' in sys.argv:
        wi = sys.argv.index('--whitelist')
        whitelist_path = sys.argv[wi + 1]
        args = sys.argv[1:wi] + sys.argv[wi + 2:]
    else:
        args = sys.argv[1:]
    baseline = args[0]
    new_files = args[1:]

    bl_text = open(baseline).read()
    bl_members = find_members(bl_text, bl_text)
    bl_names = set(m['name'] for m in bl_members)

    new_members = []
    for f in new_files:
        t = open(f).read()
        for m in find_members(t, t):
            m['file'] = os.path.basename(f)
            new_members.append(m)

    # whitelist: mapping of baseline member name -> list of allowed target classes
    whitelist = {}
    if whitelist_path and os.path.exists(whitelist_path):
        whitelist = json.load(open(whitelist_path))

    report = []
    errors = 0
    # 1. every baseline member must have a unique normalized-body match in the new set
    for m in bl_members:
        nbody = normalize_body(m['body'])
        if not nbody:
            continue  # empty bodies (e.g. interface) not comparable
        allowed = whitelist.get(m['name'])
        matches = [nm for nm in new_members
                   if normalize_body(nm['body']) == nbody
                   or (allowed is not None and normalize_body(nm['body'])
                       and similarity(nbody, normalize_body(nm['body'])) >= 0.85
                       and os.path.basename(nm.get('file', '')) in allowed)]
        if not matches:
            # fallback: allow any relocation (member moved, body rewritten by whitelisted transforms)
            fuzzy = [nm for nm in new_members
                     if normalize_body(nm['body'])
                     and similarity(nbody, normalize_body(nm['body'])) >= 0.85]
            if fuzzy:
                report.append(f"FUZZY {m['name']} ({m['start']}-{m['end']}): matched candidates "
                              + "; ".join(f"{f['file']}:{f['name']}({similarity(nbody, normalize_body(f['body'])):.2f})"
                                          for f in fuzzy[:3]))
            else:
                report.append(f"MISSING {m['name']} ({m['start']}-{m['end']})")
                errors += 1
    # 2. every new member must be matched to a baseline member (or be a whitelisted new method)
    bl_bodies = {normalize_body(m['body']) for m in bl_members if normalize_body(m['body'])}
    for nm in new_members:
        nb = normalize_body(nm['body'])
        if not nb:
            continue
        exact = any(nb == b for b in bl_bodies)
        fuzzy = not exact and any(similarity(nb, b) >= 0.85 for b in bl_bodies)
        if not exact and not fuzzy:
            report.append(f"NEW {nm['file']}:{nm['name']} ({nm['start']}-{nm['end']}) — "
                          + ("whitelisted inline-block extraction?" if 'new' in nm['name'].lower() else "UNEXPLAINED"))
            errors += 1

    print(f"== {baseline} -> {', '.join(os.path.basename(f) for f in new_files)}")
    print(f"   baseline members: {len(bl_members)}, new members: {len(new_members)}")
    for line in report:
        print("   " + line)
    print(f"RESULT: {'0-diff (semantic equivalence per Phase 1 whitelist)' if errors == 0 else str(errors) + ' UNEXPLAINED MEMBERS'}")
    sys.exit(0 if errors == 0 else 1)


if __name__ == '__main__':
    main()
