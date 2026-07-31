#!/usr/bin/env node
/**
 * Check AI dict consistency: nop-ai ORM <dicts> (single source) vs the generated
 * .dict.yaml files under nop-ai-meta/src/main/resources/_vfs/dict/ai/.
 *
 * The .dict.yaml files are codegen outputs of the ORM model's <dicts> section
 * (template: nop-codegen .../templates/orm/{appName}-meta/.../{dict.name}.dict.yaml.xgen,
 * marker `# __XGEN_FORCE_OVERRIDE__`). This script verifies that the generated
 * artifacts are in sync with the source model:
 *   1. every ORM <dict> under the ai/ namespace has a matching .dict.yaml file
 *   2. option value sets match between the ORM model and the generated yaml
 *
 * P2-D06-019 ruling (plan 2026-07-31-1834-3 Phase 5): single-source (ORM wins);
 * this script is the build-time consistency gate. Exit code 0 = consistent.
 *
 * Usage: node ai-dev/tools/check-ai-dict-consistency.mjs
 */
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import yaml from 'js-yaml';

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..');
const ORM_MODEL = join(PROJECT_ROOT, 'nop-ai', 'model', 'nop-ai.orm.xml');
const DICT_DIR = join(PROJECT_ROOT, 'nop-ai', 'nop-ai-meta', 'src', 'main', 'resources', '_vfs', 'dict', 'ai');

function parseOrmDicts(xml) {
  const dicts = [];
  const dictRe = /<dict\s+([^>]*)\/>|<dict\s+([^>]*)>([\s\S]*?)<\/dict>/g;
  let m;
  while ((m = dictRe.exec(xml)) !== null) {
    const attrs = (m[1] || m[2] || '');
    const body = m[3] || '';
    const attr = (name) => {
      const r = new RegExp(`${name}="([^"]*)"`).exec(attrs);
      return r ? r[1] : null;
    };
    const name = attr('name');
    if (!name || !name.startsWith('ai/')) continue;
    const options = [];
    const optRe = /<option\s+([^>]*)\/>/g;
    let om;
    while ((om = optRe.exec(body)) !== null) {
      const oa = om[1];
      const oattr = (n) => {
        const r = new RegExp(`${n}="([^"]*)"`).exec(oa);
        return r ? r[1] : null;
      };
      options.push({ code: oattr('code'), value: oattr('value') });
    }
    dicts.push({ name, label: attr('label'), valueType: attr('valueType'), options });
  }
  return dicts;
}

function loadYamlOptions(file) {
  const doc = yaml.load(readFileSync(file, 'utf8'));
  const options = (doc.options || []).map((o) => String(o.value));
  return { label: doc.label, valueType: doc.valueType, options };
}

// dict option values are numeric strings in the ORM source (e.g. "001"); the yaml
// codegen output parses back as YAML 1.1 integers ("001" -> 1). Compare numerically.
function normalizeValue(v) {
  const n = Number(v);
  return Number.isInteger(n) ? String(n) : v;
}

function main() {
  if (!existsSync(ORM_MODEL)) {
    console.error(`[check-ai-dict-consistency] ORM model not found: ${ORM_MODEL}`);
    process.exit(1);
  }
  if (!existsSync(DICT_DIR)) {
    console.error(`[check-ai-dict-consistency] dict dir not found: ${DICT_DIR}`);
    process.exit(1);
  }

  const ormDicts = parseOrmDicts(readFileSync(ORM_MODEL, 'utf8'));
  const yamlFiles = readdirSync(DICT_DIR).filter((f) => f.endsWith('.dict.yaml'));
  const yamlByShortName = new Map(
    yamlFiles.map((f) => [f.replace(/\.dict\.yaml$/, ''), join(DICT_DIR, f)])
  );

  const errors = [];
  const ormShortNames = new Set(ormDicts.map((d) => d.name.substring('ai/'.length)));

  for (const shortName of ormShortNames) {
    if (!yamlByShortName.has(shortName)) {
      errors.push(`dict ai/${shortName} declared in ORM <dicts> but missing generated file ${shortName}.dict.yaml`);
    }
  }
  for (const shortName of yamlByShortName.keys()) {
    if (!ormShortNames.has(shortName)) {
      errors.push(`generated file ${shortName}.dict.yaml exists but no ORM <dict> named ai/${shortName} (stale artifact?)`);
    }
  }

  for (const dict of ormDicts) {
    const shortName = dict.name.substring('ai/'.length);
    const yamlFile = yamlByShortName.get(shortName);
    if (!yamlFile) continue;
    const yamlDict = loadYamlOptions(yamlFile);
    const ormValues = new Set(dict.options.map((o) => normalizeValue(o.value)));
    const yamlValues = new Set(yamlDict.options.map(normalizeValue));
    if (ormValues.size !== yamlValues.size || [...ormValues].some((v) => !yamlValues.has(v))) {
      const onlyOrm = [...ormValues].filter((v) => !yamlValues.has(v));
      const onlyYaml = [...yamlValues].filter((v) => !ormValues.has(v));
      errors.push(
        `dict ai/${shortName} option mismatch: only-in-ORM=${JSON.stringify(onlyOrm)} only-in-yaml=${JSON.stringify(onlyYaml)}`
      );
    }
  }

  if (errors.length > 0) {
    console.error(`[check-ai-dict-consistency] ${errors.length} inconsistency(ies):`);
    for (const e of errors) console.error(`  - ${e}`);
    process.exit(1);
  }
  console.log(`[check-ai-dict-consistency] OK: ${ormDicts.length} ORM dicts consistent with ${yamlFiles.length} generated dict files`);
  process.exit(0);
}

main();
