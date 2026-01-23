#!/usr/bin/env python3
"""Generate Nop ORM (.orm.xml) from SQL DDL files.

Current parser implementation is tuned for **MySQL-style** CREATE TABLE syntax.
The CLI option `--dialect` only controls the value written into the generated
ORM header attribute `ext:dialect` (it does not switch parsing rules).

Scope/contract
- Inputs:
        - One or more .sql files containing CREATE TABLE statements.
        - NOTE: parsing is MySQL-oriented (backticks, KEY/UNIQUE KEY, etc.).
- Outputs:
  - An ORM XML file compatible with nop-orm-modeler/entity.xdef + orm.xdef

What we generate (MVP, but safe):
- <entity tableName=...>
- <columns><column .../></columns> for table columns
- primary key columns (primary=true)
- <unique-keys> from UNIQUE KEY constraints
- <indexes> from KEY / INDEX definitions (non-unique)
- <relations><to-one .../></relations> for FOREIGN KEY constraints (best-effort)

What we intentionally skip (for now):
- triggers, procedures, functions
- CREATE TABLE ... LIKE ..., CREATE TABLE ... AS SELECT ... (no column list)
- CREATE VIEW (and view SQL text output)
- advanced index expressions

This script is designed to be deterministic and re-runnable.

Usage:
    python3 ./generate_orm_from_mysql_ddl.py --out ./app-demo.orm.xml \
        --app-name app-demo --dialect mysql \
        --sql ./path/to/schema.sql

"""

from __future__ import annotations

import argparse
import dataclasses
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclasses.dataclass
class ColumnDef:
    name: str  # db column name as in DDL
    mysql_type: str  # parsed SQL type token (MySQL-oriented), e.g. varchar(50), bigint(20)
    nullable: bool
    auto_increment: bool
    default: Optional[str]
    is_primary: bool = False


@dataclasses.dataclass
class IndexDef:
    name: str
    columns: List[str]
    unique: bool


@dataclasses.dataclass
class ForeignKeyDef:
    name: str
    columns: List[str]
    ref_table: str
    ref_columns: List[str]


@dataclasses.dataclass
class TableDef:
    name: str
    columns: List[ColumnDef]
    primary_key: List[str]
    unique_keys: List[IndexDef]
    indexes: List[IndexDef]
    foreign_keys: List[ForeignKeyDef]


def strip_mysql_comments(sql: str) -> str:
    # remove -- ... endline comments
    sql = re.sub(r"--[^\n]*", "", sql)
    # remove /* ... */ comments including /*!40101 ... */
    sql = re.sub(r"/\*![\s\S]*?\*/", "", sql)
    sql = re.sub(r"/\*[\s\S]*?\*/", "", sql)
    return sql


def split_statements(sql: str) -> List[str]:
    # split on semicolons not inside quotes
    stmts: List[str] = []
    buf: List[str] = []
    in_s = False
    in_d = False
    esc = False
    for ch in sql:
        if esc:
            buf.append(ch)
            esc = False
            continue
        if ch == "\\":
            buf.append(ch)
            esc = True
            continue
        if ch == "'" and not in_d:
            in_s = not in_s
        elif ch == '"' and not in_s:
            in_d = not in_d
        if ch == ";" and not in_s and not in_d:
            stmt = "".join(buf).strip()
            if stmt:
                stmts.append(stmt)
            buf = []
        else:
            buf.append(ch)
    tail = "".join(buf).strip()
    if tail:
        stmts.append(tail)
    return stmts


_re_ident = r"`([^`]+)`|([A-Za-z0-9_]+)"


def _unquote_ident(s: str) -> str:
    s = s.strip()
    if s.startswith("`") and s.endswith("`"):
        return s[1:-1]
    return s


def _snake_to_camel(s: str) -> str:
    parts = [p for p in re.split(r"[^A-Za-z0-9]+", s) if p]
    if not parts:
        return s
    return parts[0].lower() + "".join(p[:1].upper() + p[1:].lower() for p in parts[1:])


def _std_sql_type(mysql_type: str) -> Tuple[str, Optional[int], Optional[int]]:
    t = mysql_type.strip().lower()
    # extract base + (p,s)
    m = re.match(r"([a-z]+)\s*(\(([^)]*)\))?", t)
    base = m.group(1) if m else t
    args = m.group(3) if m else None
    precision = None
    scale = None
    if args:
        nums = [a.strip() for a in args.split(",")]
        if nums and nums[0].isdigit():
            precision = int(nums[0])
            if len(nums) > 1 and nums[1].isdigit():
                scale = int(nums[1])

    mapping = {
        "varchar": "VARCHAR",
        "char": "CHAR",
        "text": "VARCHAR",
        "longtext": "VARCHAR",
        "mediumtext": "VARCHAR",
        "tinytext": "VARCHAR",
        "int": "INTEGER",
        "integer": "INTEGER",
        "smallint": "SMALLINT",
        "bigint": "BIGINT",
        "tinyint": "TINYINT",
        "bit": "BIT",
        "decimal": "DECIMAL",
        "numeric": "DECIMAL",
        "double": "DOUBLE",
        "float": "FLOAT",
        "datetime": "TIMESTAMP",
        "timestamp": "TIMESTAMP",
        "date": "DATE",
        "time": "TIME",
        "blob": "BLOB",
        "longblob": "BLOB",
        "mediumblob": "BLOB",
        "tinyblob": "BLOB",
    }

    std = mapping.get(base, "VARCHAR")

    # for TEXT family, if args missing, pick a conservative precision (optional)
    if base in {"text", "tinytext", "mediumtext", "longtext"}:
        precision = None

    return std, precision, scale


def parse_create_table(stmt: str) -> Optional[TableDef]:
    # reject LIKE/AS SELECT forms which omit column list
    if re.search(r"\bcreate\s+table\b[\s\S]*\blike\b", stmt, flags=re.I):
        return None
    if re.search(r"\bcreate\s+table\b[\s\S]*\bas\s+select\b", stmt, flags=re.I):
        return None

    m = re.match(r"\s*create\s+table\s+(if\s+not\s+exists\s+)?(?P<name>`[^`]+`|[A-Za-z0-9_]+)\s*\((?P<body>[\s\S]*)\)\s*(?P<tail>[\s\S]*)$",
                 stmt, flags=re.I)
    if not m:
        return None

    table_name = _unquote_ident(m.group("name"))
    body = m.group("body")

    # split body by commas at top-level (not inside parentheses)
    parts: List[str] = []
    buf = []
    depth = 0
    in_s = False
    in_d = False
    esc = False
    for ch in body:
        if esc:
            buf.append(ch)
            esc = False
            continue
        if ch == "\\":
            buf.append(ch)
            esc = True
            continue
        if ch == "'" and not in_d:
            in_s = not in_s
        elif ch == '"' and not in_s:
            in_d = not in_d
        if not in_s and not in_d:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
        if ch == "," and depth == 0 and not in_s and not in_d:
            p = "".join(buf).strip()
            if p:
                parts.append(p)
            buf = []
        else:
            buf.append(ch)
    tailp = "".join(buf).strip()
    if tailp:
        parts.append(tailp)

    columns: List[ColumnDef] = []
    pk_cols: List[str] = []
    unique_keys: List[IndexDef] = []
    indexes: List[IndexDef] = []
    fks: List[ForeignKeyDef] = []

    # 1st pass: column defs
    for p in parts:
        p_strip = p.strip()
        if not p_strip:
            continue
        if re.match(r"^(primary\s+key|unique\s+key|unique\s+index|key|index|constraint)\b", p_strip, flags=re.I):
            continue
        cm = re.match(r"^\s*(?P<col>`[^`]+`|[A-Za-z0-9_]+)\s+(?P<type>[A-Za-z]+(?:\s*\([^)]*\))?)\s*(?P<rest>[\s\S]*)$",
                      p_strip, flags=re.I)
        if not cm:
            continue
        col_name = _unquote_ident(cm.group("col"))
        col_type = cm.group("type").strip()
        rest = cm.group("rest")
        nullable = not re.search(r"\bnot\s+null\b", rest, flags=re.I)
        auto_inc = bool(re.search(r"\bauto_increment\b", rest, flags=re.I))
        dflt = None
        dm = re.search(r"\bdefault\s+([^\s,]+|'[^']*'|\"[^\"]*\")", rest, flags=re.I)
        if dm:
            dflt = dm.group(1)
        columns.append(ColumnDef(name=col_name, mysql_type=col_type, nullable=nullable, auto_increment=auto_inc, default=dflt))

    col_by_name = {c.name: c for c in columns}

    # constraints/indexes
    for p in parts:
        p_strip = p.strip()
        if not p_strip:
            continue

        pm = re.match(r"^primary\s+key\s*\((?P<cols>[^)]*)\)", p_strip, flags=re.I)
        if pm:
            pk_cols = [_unquote_ident(x.strip()) for x in pm.group("cols").split(",") if x.strip()]
            continue

        um = re.match(r"^(unique\s+key|unique\s+index)\s+(?P<name>`[^`]+`|[A-Za-z0-9_]+)\s*\((?P<cols>[^)]*)\)",
                      p_strip, flags=re.I)
        if um:
            uk_name = _unquote_ident(um.group("name"))
            uk_cols = [_unquote_ident(x.strip().split()[0]) for x in um.group("cols").split(",") if x.strip()]
            unique_keys.append(IndexDef(name=uk_name, columns=uk_cols, unique=True))
            continue

        im = re.match(r"^(key|index)\s+(?P<name>`[^`]+`|[A-Za-z0-9_]+)\s*\((?P<cols>[^)]*)\)", p_strip, flags=re.I)
        if im:
            idx_name = _unquote_ident(im.group("name"))
            idx_cols = [_unquote_ident(x.strip().split()[0]) for x in im.group("cols").split(",") if x.strip()]
            indexes.append(IndexDef(name=idx_name, columns=idx_cols, unique=False))
            continue

        fkm = re.match(
            r"^constraint\s+(?P<name>`[^`]+`|[A-Za-z0-9_\-\.]+)\s+foreign\s+key\s*\((?P<cols>[^)]*)\)\s+references\s+(?P<ref>`[^`]+`|[A-Za-z0-9_]+)\s*\((?P<refcols>[^)]*)\)",
            p_strip,
            flags=re.I,
        )
        if fkm:
            fk_name = _unquote_ident(fkm.group("name"))
            fk_cols = [_unquote_ident(x.strip()) for x in fkm.group("cols").split(",") if x.strip()]
            ref_table = _unquote_ident(fkm.group("ref"))
            ref_cols = [_unquote_ident(x.strip()) for x in fkm.group("refcols").split(",") if x.strip()]
            fks.append(ForeignKeyDef(name=fk_name, columns=fk_cols, ref_table=ref_table, ref_columns=ref_cols))
            continue

    # mark pk
    for c in pk_cols:
        if c in col_by_name:
            col_by_name[c].is_primary = True

    return TableDef(
        name=table_name,
        columns=columns,
        primary_key=pk_cols,
        unique_keys=unique_keys,
        indexes=indexes,
        foreign_keys=fks,
    )


def read_sql_files(paths: List[Path]) -> str:
    chunks: List[str] = []
    for p in paths:
        chunks.append(p.read_text(encoding="utf-8", errors="ignore"))
        chunks.append("\n")
    return "".join(chunks)


def escape_xml(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def generate_orm_xml(
    tables: List[TableDef],
    views: List[object],
    *,
    app_name: str,
    dialect: str,
    base_package: str,
    entity_package: str,
    use_std_fields: bool,
) -> str:
    # minimal domains set; columns will specify stdSqlType/precision/scale directly
    # (keeps it robust across many varied column widths)
    header = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<orm ext:registerShortName="true" ext:appName="{escape_xml(app_name)}" '
        f'ext:entityPackageName="{escape_xml(entity_package)}" ext:basePackageName="{escape_xml(base_package)}" '
        f'ext:dialect="{escape_xml(dialect)}" ext:useStdFields="{str(use_std_fields).lower()}" '\
        'x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef" >\n'
    )

    parts: List[str] = [header, "\n  <domains>\n", "  </domains>\n\n", "  <entities>\n"]

    prop_id = 1

    def reset_prop_id() -> int:
        return 1

    for t in sorted(tables, key=lambda x: x.name.lower()):
        prop_id = reset_prop_id()
        class_name = f"{entity_package}.{_snake_to_camel(t.name)[:1].upper()}{_snake_to_camel(t.name)[1:]}"
        parts.append(f'    <entity name="{escape_xml(class_name)}" tableName="{escape_xml(t.name)}">\n')
        parts.append("      <columns>\n")
        for c in t.columns:
            std, prec, scale = _std_sql_type(c.mysql_type)
            mandatory = "true" if not c.nullable else "false"
            primary = "true" if c.is_primary else "false"
            # defaultValue in ORM is a string.
            # We intentionally OMIT DEFAULT NULL because it's semantically redundant for nullable columns
            # and just adds noise to the generated model.
            default_attr = ""
            if c.default is not None:
                d = str(c.default).strip()
                if d.upper() != "NULL":
                    default_attr = f' defaultValue="{escape_xml(d)}"'
            prec_attr = f' precision="{prec}"' if prec is not None and std in {"VARCHAR", "CHAR", "DECIMAL"} else ""
            scale_attr = f' scale="{scale}"' if scale is not None and std == "DECIMAL" else ""

            parts.append(
                f'        <column name="{escape_xml(_snake_to_camel(c.name))}" code="{escape_xml(c.name)}" '
                f'propId="{prop_id}" stdSqlType="{std}" '
                f'mandatory="{mandatory}" primary="{primary}"{prec_attr}{scale_attr}{default_attr}/>'
                "\n"
            )
            prop_id += 1
        parts.append("      </columns>\n")

        # relations (to-one)
        if t.foreign_keys:
            parts.append("      <relations>\n")
            for fk in t.foreign_keys:
                # property name heuristic: <ref_table> or based on first column
                guess = fk.ref_table
                if len(fk.columns) == 1:
                    col = fk.columns[0]
                    if col.lower().endswith("_id"):
                        guess = col[:-3]
                prop = _snake_to_camel(guess)
                ref_class = f"{entity_package}.{_snake_to_camel(fk.ref_table)[:1].upper()}{_snake_to_camel(fk.ref_table)[1:]}"
                parts.append(
                    f'        <to-one name="{escape_xml(prop)}" constraint="{escape_xml(fk.name)}" '
                    f'refEntityName="{escape_xml(ref_class)}">\n'
                )
                parts.append("          <join>\n")
                for lc, rc in zip(fk.columns, fk.ref_columns):
                    parts.append(
                        f'            <on leftProp="{escape_xml(_snake_to_camel(lc))}" rightProp="{escape_xml(_snake_to_camel(rc))}"/>\n'
                    )
                parts.append("          </join>\n")
                parts.append("        </to-one>\n")
            parts.append("      </relations>\n")

        # unique keys
        if t.unique_keys:
            parts.append("      <unique-keys>\n")
            for uk in t.unique_keys:
                cols = ",".join(_snake_to_camel(c) for c in uk.columns)
                # name must be string; keep original index name for traceability
                parts.append(
                    f'        <unique-key name="{escape_xml(uk.name)}" columns="{escape_xml(cols)}" constraint="{escape_xml(uk.name)}"/>\n'
                )
            parts.append("      </unique-keys>\n")

        # indexes (non-unique)
        if t.indexes:
            parts.append("      <indexes>\n")
            for idx in t.indexes:
                parts.append(
                    f'        <index name="{escape_xml(idx.name)}" unique="false">\n'
                )
                for col in idx.columns:
                    parts.append(
                        f'          <column name="{escape_xml(_snake_to_camel(col))}"/>\n'
                    )
                parts.append("        </index>\n")
            parts.append("      </indexes>\n")

        parts.append("    </entity>\n\n")

    parts.append("  </entities>\n</orm>\n")
    return "".join(parts)


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sql", action="append", required=True, help="Path to an input .sql file (repeatable)")
    ap.add_argument("--out", required=True, help="Output orm.xml path")
    ap.add_argument("--app-name", default="app-demo")
    ap.add_argument(
        "--dialect",
        default="mysql",
        help="Value to write into ORM header ext:dialect (does not affect SQL parsing).",
    )
    ap.add_argument("--base-package", default="io.app.los")
    ap.add_argument("--entity-package", default="io.app.los.dao.entity")
    ap.add_argument("--use-std-fields", default="true", choices=["true", "false"])
    args = ap.parse_args(argv)

    sql_paths = [Path(p) for p in args.sql]
    out_path = Path(args.out)

    raw = read_sql_files(sql_paths)
    raw = strip_mysql_comments(raw)
    stmts = split_statements(raw)

    tables: Dict[str, TableDef] = {}
    views: Dict[str, object] = {}

    for s in stmts:
        if re.match(r"\s*create\s+table\b", s, flags=re.I):
            td = parse_create_table(s)
            if td:
                tables[td.name] = td

    xml_text = generate_orm_xml(
        list(tables.values()),
        list(views.values()),
        app_name=args.app_name,
        dialect=args.dialect,
        base_package=args.base_package,
        entity_package=args.entity_package,
        use_std_fields=(args.use_std_fields == "true"),
    )

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(xml_text, encoding="utf-8")

    print(f"Generated: {out_path} (tables={len(tables)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
