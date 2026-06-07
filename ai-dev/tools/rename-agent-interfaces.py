#!/usr/bin/env python3
"""
Rename generic interface names to domain-qualified names in ai-agent design docs.

Uses perl word-boundary matching via subprocess for precise replacement.
Only renames I-prefixed interface names and their impl classes.
Does NOT rename concept words like "Hook lifecycle" or "Hook engine".

Mapping (processed longest-first):
  ProgressiveCompactor -> ProgressiveContextCompactor
  NoOpCompactor        -> NoOpContextCompactor
  PassThroughRouter    -> PassThroughModelRouter
  SmartRouter          -> SmartModelRouter
  NoOpGuardrail        -> NoOpContentGuardrail
  IHook                -> IAgentLifecycleHook
  ICompactor           -> IContextCompactor
  IRouter              -> IModelRouter
  IGuardrail           -> IContentGuardrail

Usage:
  python3 ai-dev/tools/rename-agent-interfaces.py           # dry run
  python3 ai-dev/tools/rename-agent-interfaces.py --apply    # apply changes
"""

import subprocess
import glob
import os
import sys

TARGET_DIR = os.path.join("ai-dev", "design", "nop-ai-agent")

SUBST = (
    r"s/\bProgressiveCompactor\b/ProgressiveContextCompactor/g;"
    r"s/\bNoOpCompactor\b/NoOpContextCompactor/g;"
    r"s/\bPassThroughRouter\b/PassThroughModelRouter/g;"
    r"s/\bSmartRouter\b/SmartModelRouter/g;"
    r"s/\bNoOpGuardrail\b/NoOpContentGuardrail/g;"
    r"s/\bIHook\b/IAgentLifecycleHook/g;"
    r"s/\bICompactor\b/IContextCompactor/g;"
    r"s/\bIRouter\b/IModelRouter/g;"
    r"s/\bIGuardrail\b/IContentGuardrail/g;"
)

APPLY = "--apply" in sys.argv

files = sorted(glob.glob(os.path.join(TARGET_DIR, "*.md")))
total = 0

for filepath in files:
    with open(filepath, "r") as f:
        original = f.read()

    result = subprocess.run(
        ["perl", "-pe", SUBST],
        input=original,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"ERROR processing {os.path.basename(filepath)}: {result.stderr}")
        continue

    renamed = result.stdout

    if original == renamed:
        continue

    total += 1
    orig_lines = original.splitlines()
    new_lines = renamed.splitlines()
    changed_count = sum(1 for a, b in zip(orig_lines, new_lines) if a != b)

    print(f"\n{os.path.basename(filepath)}: {changed_count} line(s) changed")

    if APPLY:
        with open(filepath, "w") as f:
            f.write(renamed)
        print("  -> APPLIED")
    else:
        print("  -> DRY RUN")
        for i, (a, b) in enumerate(zip(orig_lines, new_lines), 1):
            if a != b:
                print(f"    L{i}: -{a.strip()}")
                print(f"         +{b.strip()}")

print()
if total == 0:
    print("All names already up to date.")
else:
    print(f"{total} file(s) with renames.")
    if not APPLY:
        print("Dry run. Run with --apply to apply.")
