#!/usr/bin/env python3
"""
Rename manage-todo-list -> update-todos across the entire project.

Handles:
1. File name: manage-todo-list.tool.xml -> update-todos.tool.xml
2. Java class name: ManageTodoListExecutor -> UpdateTodosExecutor
3. Java file name: ManageTodoListExecutor.java -> UpdateTodosExecutor.java
4. Java test file name: ManageTodoListExecutorTest.java -> UpdateTodosExecutorTest.java
5. Bean id: ai-tools:manage-todo-list -> ai-tools:update-todos
6. XML element/tag: <manage-todo-list ...> -> <update-todos ...>
7. String literal "manage-todo-list" -> "update-todos"
8. Comments/docs references

Usage:
    python3 ai-dev/tools/rename-manage-todo-list.py --dry-run   # preview
    python3 ai-dev/tools/rename-manage-todo-list.py --apply     # execute
"""
import subprocess
import sys
import os

DRY_RUN = "--dry-run" in sys.argv
APPLY = "--apply" in sys.argv

if not DRY_RUN and not APPLY:
    print("Usage: --dry-run or --apply")
    sys.exit(1)

PROJECT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

def run(cmd, **kw):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=PROJECT, **kw)
    return result.stdout.strip()

# --- Step 1: sed replacements in files ---
replacements = [
    # order matters: longer strings first
    ("ManageTodoListExecutorTest", "UpdateTodosExecutorTest"),
    ("ManageTodoListExecutor", "UpdateTodosExecutor"),
    ("manage-todo-list", "update-todos"),
]

# find all files containing the old names
files_cmd = 'grep -rl "manage-todo-list\\|ManageTodoListExecutor" --include="*.java" --include="*.xml" --include="*.md" --include="*.beans.xml" --include="*.xdef" .'
files = run(files_cmd).split("\n")
files = [f for f in files if f.strip() and "node_modules" not in f and "_gen/" not in f and "/target/" not in f and "/.idea/" not in f]

print(f"Found {len(files)} files to update\n")

for f in files:
    for old, new in replacements:
        cmd = f"grep -c '{old}' '{f}' 2>/dev/null || true"
        count = run(cmd)
        if count and int(count) > 0:
            action = "would replace" if DRY_RUN else "replacing"
            print(f"  {action} '{old}' -> '{new}' in {f} ({count} occurrences)")
            if APPLY:
                sed_cmd = f"sed -i '' 's/{old}/{new}/g' '{f}'"
                run(sed_cmd)

# --- Step 2: rename files ---
file_renames = [
    "nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/manage-todo-list.tool.xml:::nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/update-todos.tool.xml",
    "nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ManageTodoListExecutor.java:::nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/UpdateTodosExecutor.java",
    "nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/ManageTodoListExecutorTest.java:::nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/UpdateTodosExecutorTest.java",
]

print()
for item in file_renames:
    old_path, new_path = item.split(":::")
    old_full = os.path.join(PROJECT, old_path)
    new_full = os.path.join(PROJECT, new_path)
    action = "would rename" if DRY_RUN else "renaming"
    print(f"  {action} {old_path}")
    print(f"        -> {new_path}")
    if APPLY:
        os.rename(old_full, new_full)

# --- Step 3: git add ---
if APPLY:
    print("\ngit add ...")
    run("git add -A")

print("\nDone." if APPLY else "\nDry run complete. Use --apply to execute.")
