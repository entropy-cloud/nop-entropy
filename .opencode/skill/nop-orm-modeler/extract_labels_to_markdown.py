#!/usr/bin/env python3
"""Extract a compact table/column label mapping from Nop ORM XML into Markdown.

Motivation
- YAML is very verbose (tokens heavy) when you have thousands of columns.
- For AI translation / review, a compact Markdown table is easier and cheaper:
  - one section per table
  - each row: fieldName | displayName | enDisplayName

Design
- Key is (tableName, fieldName) where fieldName is the ORM column attribute "name".
- displayName column contains current displayName from XML (may be empty).
- enDisplayName is left empty for AI to fill.

Namespace safety
- We do not use strict XML parsing (ORM files may have undeclared prefixes).
- We use tolerant line-based parsing for <entity ...> and <column .../>.

Usage
    python ./extract_labels_to_markdown.py --in ./app-demo.orm.xml --out ./labels.app-demo.md

"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Dict, List, Optional


ENTITY_RE = re.compile(r"\s*<entity\b([^>]*)>")
COLUMN_RE = re.compile(r"\s*<column\b([^>]*)/>")
ATTR_RE = re.compile(r"\b([A-Za-z_][A-Za-z0-9_:\-]*)=\"([^\"]*)\"")


def parse_attrs(attr_text: str) -> Dict[str, str]:
    return {m.group(1): m.group(2) for m in ATTR_RE.finditer(attr_text)}


def md_escape_cell(s: str) -> str:
    # keep it simple: escape pipes and normalize newlines
    s = (s or "").replace("\n", " ").replace("|", "\\|")
    return s


def build_mapping(xml_text: str) -> Dict[str, List[Dict[str, str]]]:
    tables: Dict[str, List[Dict[str, str]]] = {}
    current_table: Optional[str] = None

    for line in xml_text.splitlines():
        em = ENTITY_RE.match(line)
        if em:
            attrs = parse_attrs(em.group(1))
            current_table = attrs.get("tableName")
            if current_table:
                tables.setdefault(current_table, [])
            continue

        cm = COLUMN_RE.match(line)
        if cm and current_table:
            attrs = parse_attrs(cm.group(1))
            name = attrs.get("name")
            if not name:
                continue
            tables[current_table].append(
                {
                    "fieldName": name,
                    "displayName": attrs.get("displayName", ""),
                    "enDisplayName": "",
                }
            )

    # sort fields per table to keep deterministic output
    for t in tables:
        tables[t].sort(key=lambda r: r["fieldName"].lower())

    return dict(sorted(tables.items(), key=lambda kv: kv[0].lower()))


def dump_md(tables: Dict[str, List[Dict[str, str]]]) -> str:
    out: List[str] = []
    out.append("# ORM Column displayName Translation\n")
    out.append("\n")
    out.append("Instructions:\n")
    out.append("- Fill the **enDisplayName** column with English **Title Case** labels.\n")
    out.append("- Keep **fieldName** unchanged.\n")
    out.append("- Keep the table sections and row order unchanged if possible.\n")
    out.append("- Common acronyms should stay uppercase (ID, DOB, URL, SQL, WFM, LOS, CAS, etc.).\n")
    out.append("\n")

    for table_name, rows in tables.items():
        out.append(f"## {table_name}\n")
        out.append("\n")
        out.append("| fieldName | displayName | enDisplayName |\n")
        out.append("|---|---|---|\n")
        for r in rows:
            out.append(
                f"| {md_escape_cell(r['fieldName'])} | {md_escape_cell(r['displayName'])} | {md_escape_cell(r['enDisplayName'])} |\n"
            )
        out.append("\n")

    return "".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out", dest="out", required=True)
    args = ap.parse_args()

    in_path = Path(args.inp)
    out_path = Path(args.out)

    xml_text = in_path.read_text(encoding="utf-8", errors="ignore")
    tables = build_mapping(xml_text)

    out_path.write_text(dump_md(tables), encoding="utf-8")
    print(f"Extracted tables: {len(tables)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
