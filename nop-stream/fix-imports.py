#!/usr/bin/env python3
"""
Fix Java import ordering in nop-stream module.
Rules:
  1. java.*
  2. jakarta.*
  3. third-party (io.nop.* excluding io.nop.stream.*, org.*, com.*, etc.)
  4. io.nop.stream.* (project internal)

Blank line between groups. Within each group, alphabetical order (case-insensitive).
Only modifies non-generated source files (excludes _gen/ directories).
"""

import re
import sys
from pathlib import Path


def get_import_group(import_line):
    """Return group number for an import line."""
    stripped = import_line.strip()
    if stripped.startswith("import static "):
        body = stripped[len("import static "):].rstrip(";")
    elif stripped.startswith("import "):
        body = stripped[len("import "):].rstrip(";")
    else:
        return -1

    if body.startswith("java."):
        return 0
    elif body.startswith("jakarta."):
        return 1
    elif body.startswith("io.nop.stream."):
        return 3
    else:
        return 2


def sort_imports(imports):
    """Sort imports: group by category, then alphabetically within group."""
    if not imports:
        return []

    actual_imports = [imp for imp in imports if imp.strip().startswith("import ")]
    if not actual_imports:
        return []

    def sort_key(imp):
        group = get_import_group(imp)
        is_static = 1 if imp.strip().startswith("import static ") else 0
        stripped = imp.strip()
        # Use the full import text for alphabetical comparison (lowercase)
        return (group, is_static, stripped.lower(), stripped)

    sorted_imports = sorted(actual_imports, key=sort_key)

    # Insert blank lines between groups
    result = []
    last_group = None
    for imp in sorted_imports:
        group = get_import_group(imp)
        if last_group is not None and group != last_group:
            result.append("")
        result.append(imp)
        last_group = group

    return result


def process_file(filepath):
    """Process a single Java file to fix import ordering. Returns True if modified."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    lines = content.split('\n')

    # Find package declaration
    package_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith('package '):
            package_idx = i
            break

    if package_idx == -1:
        return False

    # Find import region
    import_start = -1
    i = package_idx + 1
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith('import '):
            import_start = i
            break
        elif stripped == '' or stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*'):
            i += 1
            continue
        else:
            return False

    if import_start == -1:
        return False

    # Collect all imports, skip blank lines within import block
    imports = []
    import_end = import_start
    i = import_start
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith('import '):
            imports.append(lines[i])
            import_end = i + 1
            i += 1
        elif stripped == '':
            # Look ahead for more imports
            j = i + 1
            while j < len(lines) and lines[j].strip() == '':
                j += 1
            if j < len(lines) and lines[j].strip().startswith('import '):
                i = j  # skip blank lines within import block
            else:
                import_end = i
                break
        else:
            import_end = i
            break

    if not imports:
        return False

    # Sort imports
    sorted_imports = sort_imports(imports)

    # Reconstruct: before imports / sorted imports / after imports
    before = lines[:import_start]
    after = lines[import_end:]

    # Ensure exactly one blank line after package/comment before imports
    while before and before[-1].strip() == '':
        before.pop()
    before.append('')

    # Ensure exactly one blank line between imports and next code
    # Remove leading blank lines from 'after'
    while after and after[0].strip() == '':
        after.pop(0)
    after_with_sep = [''] + after

    new_lines = before + sorted_imports + after_with_sep
    new_content = '\n'.join(new_lines)

    # Ensure file ends with single newline
    while new_content.endswith('\n\n\n'):
        new_content = new_content[:-1]
    if not new_content.endswith('\n'):
        new_content += '\n'

    if new_content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True

    return False


def main():
    if len(sys.argv) < 2:
        print("Usage: fix-imports.py <file_or_dir> [file_or_dir2 ...]")
        sys.exit(1)

    targets = sys.argv[1:]
    total_modified = 0

    for target in targets:
        path = Path(target)

        if path.is_file():
            if '/_gen/' in str(path) or '/_gen\\' in str(path):
                continue
            if path.name == 'package-info.java':
                continue
            try:
                if process_file(str(path)):
                    total_modified += 1
                    print(f"  Fixed: {path.name}")
            except Exception as e:
                print(f"  Error: {path}: {e}")
        elif path.is_dir():
            for java_file in sorted(path.rglob('*.java')):
                filepath = str(java_file)
                if '/_gen/' in filepath or '\\_gen\\' in filepath:
                    continue
                if java_file.name == 'package-info.java':
                    continue
                try:
                    if process_file(filepath):
                        total_modified += 1
                        print(f"  Fixed: {java_file.relative_to(path)}")
                except Exception as e:
                    print(f"  Error: {java_file}: {e}")
        else:
            print(f"Not found: {target}")

    print(f"\nTotal files modified: {total_modified}")


if __name__ == '__main__':
    main()
