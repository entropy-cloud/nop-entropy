#!/usr/bin/env node
// Flux page static checker.
//
// Purpose: catch AMIS constructs that silently fail to render under the flux
// render mode. The flux runtime drops unknown component types to `null`, so a
// page migrated from AMIS can lose whole toolbars/buttons without any error.
// This tool scans page YAML files and reports such constructs before runtime.
//
// Detection is a high-confidence denylist (AMIS-only component types / keys),
// which has no false positives. Optional `--type-check` adds a best-effort
// "unknown flux type" warning based on a built-in baseline plus (when
// available) an on-disk scan of a nop-chaos-flux checkout.
//
// Usage:
//   node check-flux-pages.mjs [paths...] [--root <dir>] [--include-dump]
//                             [--include-page-yaml] [--type-check] [--strict] [--json]
//
// With no paths, scans <root> (default: repo root) for every `*.flux.yaml`
// (excluding node_modules / target / _dump). `--include-dump` also scans
// `_dump/` dirs; that output reflects the render mode of the last build, so
// only meaningful after a flux-mode build. `--include-page-yaml` additionally
// scans `*.page.yaml` (resolved flux models; a sibling flux.yaml wins at
// runtime and is skipped here).
//
// Exit code: 1 if any error, else 0 (warnings alone do not fail unless --strict).

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import yaml from 'js-yaml';

// AMIS component types with no flux equivalent (dropped by the flux compiler).
// Sources: docs-for-ai/02-core-guides/flux-rendering.md (「双引擎能力面不同」),
// nop-chaos-flux flux-guide/09-amis-migration.md, flux button-group design.
const AMIS_ONLY_TYPES = new Set([
  'button-toolbar',
  'service',
  'static',
  'static-mapping',
  'static-image',
  'input-rich-text',
  'wrapper',
  'each',
  'formitem',
  'grid-2d',
  'panel',
]);

// AMIS-only schema keys. Checked only on nodes that carry a `type` (i.e. real
// schema nodes), never on arbitrary nested data (json-view values etc.).
const AMIS_ONLY_KEYS = new Set([
  'actionType',
  'visibleOn',
  'hiddenOn',
  'disabledOn',
  'validations',
  'asideResizor',
  'initApi',
  'initFetch',
  'initFetchOn',
]);

// Best-effort canonical flux schema types (from nop-chaos-flux `schemas.ts`
// interface declarations). Used only by --type-check; incomplete by design.
const FLUX_TYPE_BASELINE = new Set([
  'ai-attachments', 'ai-bubble', 'ai-chat', 'ai-citations', 'ai-conversations',
  'ai-feedback', 'ai-message-list', 'ai-prompts', 'ai-sender', 'ai-suggestions',
  'ai-token-usage', 'ai-tool-call', 'ai-voice-input', 'ai-welcome', 'alert',
  'audio', 'badge', 'barcode', 'batch-bar', 'button', 'button-group',
  'button-group-select', 'card', 'cards', 'carousel', 'chart', 'collapse',
  'command-palette', 'container', 'countdown', 'dashboard', 'date-range',
  'designer-canvas', 'designer-edge-row', 'designer-field', 'designer-node-card',
  'designer-page', 'designer-palette', 'dialog', 'diff-view', 'drawer',
  'dropdown-button', 'dynamic-renderer', 'echarts', 'editor', 'empty', 'flex',
  'form', 'fragment', 'graph', 'grid', 'hidden', 'html', 'icon', 'image',
  'infinite-scroll', 'input-color', 'input-date', 'input-datetime', 'input-file',
  'input-image', 'input-number', 'input-time', 'input-tree', 'json-view',
  'keyboard', 'line', 'link', 'list', 'loop', 'map', 'mapping', 'markdown',
  'markdown-editor', 'notice-bar', 'page', 'pageNumber', 'pagination',
  'pivot-table', 'printDate', 'progress', 'pull-refresh', 'qrcode',
  'query-filter', 'rating', 'rect', 'recurse', 'reaction', 'resizable',
  'responsive', 'result', 'scada-canvas', 'scope-debug', 'separator', 'slider',
  'sparkline', 'spinner', 'stat-tile', 'statistics', 'status', 'steps',
  'swipe-cell', 'table', 'tabs', 'text', 'three-canvas', 'timeline', 'tree',
  'tree-select', 'video', 'wizard', 'data-source', 'picker',
]);

const isMap = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

function collectPaths(root, opts) {
  const files = [];
  const skipDir = (name) => name === 'node_modules' || name === 'target' || name === '.git'
    || (name === '_dump' && !opts.includeDump);
  const walk = (dir) => {
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (!skipDir(entry.name)) walk(full);
        continue;
      }
      if (!entry.isFile()) continue;
      if (entry.name.endsWith('.flux.yaml')) {
        files.push(full);
        continue;
      }
      if (entry.name.endsWith('.page.yaml')) {
        // `_dump` reflects the render mode of the last build (often AMIS), so
        // page.yaml is opt-in only. flux.yaml is unambiguous and always scanned.
        if (!opts.includePageYaml) continue;
        // A sibling flux.yaml is the runtime-preferred source; skip it here to
        // avoid flagging the AMIS page that is never rendered in flux mode.
        const sibling = full.slice(0, -'.page.yaml'.length) + '.flux.yaml';
        if (fs.existsSync(sibling)) continue;
        files.push(full);
      }
    }
  };
  walk(root);
  return files.sort();
}

function checkNode(node, file, jsonPath, report, opts) {
  if (Array.isArray(node)) {
    node.forEach((child, i) => checkNode(child, file, `${jsonPath}[${i}]`, report, opts));
    return;
  }
  if (!isMap(node)) return;

  const type = typeof node.type === 'string' ? node.type : undefined;
  if (type) {
    if (AMIS_ONLY_TYPES.has(type)) {
      report.error(file, jsonPath, `AMIS-only component type '${type}' has no flux renderer (renders as null)`);
    } else if (opts.typeCheck && !FLUX_TYPE_BASELINE.has(type) && !type.includes(':')) {
      report.warn(file, jsonPath, `unknown flux component type '${type}' (best-effort check)`);
    }
    for (const key of AMIS_ONLY_KEYS) {
      if (Object.prototype.hasOwnProperty.call(node, key)) {
        report.error(file, jsonPath, `AMIS-only key '${key}' on '${type}' (convert for flux)`);
      }
    }
    if (type === 'page' && Object.prototype.hasOwnProperty.call(node, 'toolbar')) {
      report.error(
        file,
        jsonPath,
        "page-level 'toolbar' is not a flux page region; move actions under 'header'",
      );
    }
  }

  for (const [key, value] of Object.entries(node)) {
    checkNode(value, file, `${jsonPath}.${key}`, report, opts);
  }
}

function run(argv) {
  const paths = [];
  let root = process.cwd();
  const opts = { includePageYaml: false, includeDump: false, typeCheck: false, strict: false, json: false };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--root') root = argv[++i];
    else if (arg === '--include-page-yaml') opts.includePageYaml = true;
    else if (arg === '--include-dump') opts.includeDump = true;
    else if (arg === '--type-check') opts.typeCheck = true;
    else if (arg === '--strict') opts.strict = true;
    else if (arg === '--json') opts.json = true;
    else if (arg.startsWith('-')) {
      console.error(`unknown option: ${arg}`);
      process.exit(2);
    } else paths.push(arg);
  }

  const targets = [];
  for (const p of paths) {
    const abs = path.resolve(p);
    if (!fs.existsSync(abs)) {
      console.error(`path not found: ${p}`);
      process.exit(2);
    }
    if (fs.statSync(abs).isDirectory()) targets.push(...collectPaths(abs, opts));
    else targets.push(abs);
  }
  if (paths.length === 0) targets.push(...collectPaths(root, opts));

  const errors = [];
  const warnings = [];
  const report = {
    error: (file, jsonPath, message) => errors.push({ file, path: jsonPath, message }),
    warn: (file, jsonPath, message) => warnings.push({ file, path: jsonPath, message }),
  };

  let checked = 0;
  for (const file of targets) {
    let doc;
    try {
      doc = yaml.load(fs.readFileSync(file, 'utf8'));
    } catch (e) {
      report.error(file, '$', `YAML parse failed: ${e.message}`);
      continue;
    }
    checked++;
    checkNode(doc, file, '$', report, opts);
  }

  if (opts.json) {
    console.log(JSON.stringify({ checked, errors, warnings }, null, 2));
  } else {
    for (const e of errors) console.error(`ERROR ${e.file} ${e.path} ${e.message}`);
    for (const w of warnings) console.warn(`WARN  ${w.file} ${w.path} ${w.message}`);
    console.log(
      `check-flux-pages: ${checked} file(s) checked, ${errors.length} error(s), ${warnings.length} warning(s)`,
    );
  }

  const failed = errors.length > 0 || (opts.strict && warnings.length > 0);
  process.exit(failed ? 1 : 0);
}

run(process.argv.slice(2));
