#!/usr/bin/env python3
"""Apply translated column labels from a compact Markdown file back into a Nop ORM XML.

Input Markdown format (produced by extract_labels_to_markdown.py):
- One section per table using: "## <tableName>"
- Followed by a Markdown table:

  | fieldName | displayName | enDisplayName |
  |---|---|---|
  | applicationId | Application ID | Application ID |

Rules
- We match by (tableName, fieldName).
- Fixed convention (recommended by this repo):
    - Markdown `displayName` (Chinese / primary language) -> XML `displayName`
    - Markdown `enDisplayName` (English) -> XML `i18n-en:displayName`
- Default behavior: only fill when the corresponding XML attribute is missing.
- Use --overwrite to overwrite existing attributes.

Namespace safety
- Same tolerant line-based editing as other scripts.

Usage
    python ./apply_labels_from_markdown.py --in-xml ./app-demo.orm.xml --in-md ./labels.app-demo.md --out-xml ./app-demo.orm.xml

"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# Parsing Markdown
TABLE_HEADER_RE = re.compile(r"^##\s+(.+?)\s*$")
MD_ROW_RE = re.compile(r"^\|(.+?)\|(.+?)\|(.+?)\|\s*$")


def md_unescape_cell(s: str) -> str:
    s = s.strip()
    s = s.replace("\\|", "|")
    return s


def parse_md(md_text: str) -> Dict[str, Dict[str, Dict[str, str]]]:
    """Return mapping: tableName -> fieldName -> {displayName, enDisplayName}."""
    mapping: Dict[str, Dict[str, Dict[str, str]]] = {}
    current_table: Optional[str] = None
    in_md_table = False

    for raw in md_text.splitlines():
        m = TABLE_HEADER_RE.match(raw)
        if m:
            current_table = m.group(1).strip()
            mapping.setdefault(current_table, {})
            in_md_table = False
            continue

        if current_table is None:
            continue

        # detect markdown table start/end
        if raw.strip().startswith("| fieldName"):
            in_md_table = True
            continue
        if in_md_table and raw.strip().startswith("|---"):
            continue
        if in_md_table and raw.strip() == "":
            in_md_table = False
            continue

        if in_md_table:
            rm = MD_ROW_RE.match(raw)
            if not rm:
                continue
            field = md_unescape_cell(rm.group(1))
            display = md_unescape_cell(rm.group(2))
            en_display = md_unescape_cell(rm.group(3))
            field = field.strip()
            if not field:
                continue
            mapping[current_table][field] = {
                "displayName": display.strip(),
                "enDisplayName": en_display.strip(),
            }

    return mapping


# Editing ORM XML (tolerant)
ENTITY_RE = re.compile(r"(?P<indent>\s*)<entity\b(?P<attrs>[^>]*)>")
COLUMN_RE = re.compile(r"(?P<indent>\s*)<column\b(?P<attrs>[^>]*)/>")
ATTR_RE = re.compile(r"\b([A-Za-z_][A-Za-z0-9_:\-]*)=\"([^\"]*)\"")


def parse_attrs(attr_text: str) -> Dict[str, str]:
    return {m.group(1): m.group(2) for m in ATTR_RE.finditer(attr_text)}


def set_attr(attr_text: str, key: str, value: str) -> str:
    if re.search(rf"\b{re.escape(key)}=\"", attr_text):
        return re.sub(rf"\b{re.escape(key)}=\"[^\"]*\"", f'{key}="{value}"', attr_text)
    m = re.search(r"\bname=\"[^\"]*\"", attr_text)
    insert = f' {key}="{value}"'
    if m:
        i = m.end()
        return attr_text[:i] + insert + attr_text[i:]
    return attr_text + insert


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in-xml", required=True)
    ap.add_argument("--in-md", required=True)
    ap.add_argument("--out-xml", required=True)
    ap.add_argument("--overwrite", action="store_true")
    ap.add_argument(
        "--apply-zh",
        dest="apply_zh",
        action="store_true",
        default=True,
        help="Apply Markdown displayName into XML displayName (Chinese/primary language).",
    )
    ap.add_argument(
        "--no-apply-zh",
        dest="apply_zh",
        action="store_false",
        help="Do not apply Markdown displayName into XML displayName.",
    )
    ap.add_argument(
        "--apply-en",
        dest="apply_en",
        action="store_true",
        default=True,
        help="Apply Markdown enDisplayName into XML i18n-en:displayName.",
    )
    ap.add_argument(
        "--no-apply-en",
        dest="apply_en",
        action="store_false",
        help="Do not apply Markdown enDisplayName into XML i18n-en:displayName.",
    )
    args = ap.parse_args()

    md_map = parse_md(Path(args.in_md).read_text(encoding="utf-8", errors="ignore"))

    xml_lines = Path(args.in_xml).read_text(encoding="utf-8", errors="ignore").splitlines(keepends=True)

    current_table: Optional[str] = None
    updated = 0
    missing_map = 0

    out_lines: List[str] = []
    for line in xml_lines:
        em = ENTITY_RE.search(line)
        if em:
            attrs = parse_attrs(em.group("attrs"))
            current_table = attrs.get("tableName")
            out_lines.append(line)
            continue

        cm = COLUMN_RE.search(line)
        if cm and current_table:
            attrs_text = cm.group("attrs")
            attrs = parse_attrs(attrs_text)
            field_name = attrs.get("name")

            if not field_name:
                out_lines.append(line)
                continue

            table_map = md_map.get(current_table)
            if not table_map:
                missing_map += 1
                out_lines.append(line)
                continue

            row = table_map.get(field_name)
            if not row:
                missing_map += 1
                out_lines.append(line)
                continue

            zh = (row.get("displayName") or "").strip()
            en = (row.get("enDisplayName") or "").strip()

            new_attrs_text = attrs_text
            changed_this_line = 0

            if args.apply_zh and zh:
                has_zh = "displayName" in attrs
                if args.overwrite or not has_zh:
                    new_attrs_text = set_attr(new_attrs_text, "displayName", zh)
                    if new_attrs_text != attrs_text:
                        changed_this_line = 1

            if args.apply_en and en:
                has_en = "i18n-en:displayName" in attrs
                if args.overwrite or not has_en:
                    # keep i18n-en prefix as a plain attribute (namespace-safe)
                    new_attrs_text2 = set_attr(new_attrs_text, "i18n-en:displayName", en)
                    if new_attrs_text2 != new_attrs_text:
                        new_attrs_text = new_attrs_text2
                        changed_this_line = 1

            if changed_this_line and new_attrs_text != attrs_text:
                updated += 1
                new_line = line[: cm.start("attrs")] + new_attrs_text + line[cm.end("attrs"):]
                out_lines.append(new_line)
                continue

            out_lines.append(line)
            continue

        out_lines.append(line)

    Path(args.out_xml).write_text("".join(out_lines), encoding="utf-8")
    print(f"Updated columns: {updated}")
    print(f"Columns missing mapping: {missing_map}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
