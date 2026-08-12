#!/usr/bin/env node
/**
 * 检查所有 beans.xml 中的 bean id 命名是否符合平台强约定：
 *   - 短名字（单标识符，不含 '.'）必须以 `nop` 为前缀（docs-for-ai/02-core-guides/code-style.md §IoC Bean 命名）
 *   - 允许的例外前缀：`biz_`（codegen 生成的 BizModel bean，`_service.beans.xml`）、`test`/`testMock`（测试 bean）
 *   - 全限定类名（含 '.'）视为合法（BeanModel 支持 class 名直接作为 id）
 *   - 测试目录（src/test）与生成物（_dump、_gen、target）排除
 *
 * 同时检查受影响的位置：
 *   - `ref="xxx"` / `value-ref="xxx"`：引用短名 bean 时，被引用 id 也应遵守命名约定（ref 本身不改名，
 *     但被引用 id 违规时给出提示，帮助定位需要改名的 ref 点）
 *   - `ioc:collect-beans name-prefix="xxx"`：前缀本身若不是 nop/biz_ 前缀则提示（收集约定与命名强约定应一致）
 *
 * 用法:
 *   node ai-dev/tools/check-bean-naming.mjs                  # 全仓库检查
 *   node ai-dev/tools/check-bean-naming.mjs nop-auth          # 指定模块
 *   node ai-dev/tools/check-bean-naming.mjs --json            # JSON 输出（CI 友好）
 *
 * 退出码：0 = 无违规；1 = 存在违规（--strict 时 ref 提示也计为违规）
 */

import { readFileSync } from 'node:fs';
import { globSync } from 'node:fs';
import { resolve, relative } from 'node:path';

const ROOT = resolve(import.meta.dirname, '../..');

const args = process.argv.slice(2);
const strictMode = args.includes('--strict');
const jsonMode = args.includes('--json');
let modules = args.filter(a => !a.startsWith('--'));

// 合法前缀（短名 bean 允许的）
const LEGAL_PREFIXES = ['nop', 'biz_'];
// 测试 bean 前缀
const TEST_PREFIXES = ['test', 'testMock'];
// 豁免：AI 工具注册 bean（id 形如 "ai-tools:bash" / "ai-agent-tools:call-agent"——冒号前是工具命名空间，
// 经 <ioc:collect-beans by-type="IToolExecutor"/> 收集，是平台既有约定，非传统 bean id 语义）
const TOOL_NAMESPACE_PREFIXES = ['ai-tools:', 'ai-agent-tools:'];
// 豁免：BizModel 变体注册（id 形如 "NopAuthUserBizModel_tenant"——bizObjName 变体如 NopAuthUser_tenant，
// 平台多租户/多应用约定）
const BIZMODEL_VARIANT_RE = /^[A-Z][A-Za-z0-9]*BizModel_[A-Za-z0-9_]+$/;
// 排除路径段
const EXCLUDED_SEGMENTS = ['target', '_dump', '_gen', 'node_modules', '.git'];

function toPosix(path) {
  return path.replace(/\\/g, '/');
}

function isExcluded(path) {
  const normalized = toPosix(path);
  for (const seg of EXCLUDED_SEGMENTS) {
    if (normalized.includes(`/${seg}/`) || normalized.endsWith(`/${seg}`)) {
      return true;
    }
  }
  return false;
}

function isTestPath(path) {
  const normalized = toPosix(path);
  return normalized.includes('/src/test/') || normalized.includes('/src\\test/')
    || normalized.includes('/test/') || normalized.endsWith('/test')
    || normalized.includes('/__tests__/');
}

function isLegalShortName(id) {
  // 全限定类名（含 .）合法
  if (id.includes('.')) return true;
  for (const p of LEGAL_PREFIXES) {
    if (id.startsWith(p)) return true;
  }
  return false;
}

function isExemptShortName(id) {
  // AI 工具注册名（ai-tools:xxx / ai-agent-tools:xxx）
  for (const p of TOOL_NAMESPACE_PREFIXES) {
    if (id.startsWith(p)) return true;
  }
  // BizModel 变体注册（NopXxxBizModel_tenant 等）
  if (BIZMODEL_VARIANT_RE.test(id)) return true;
  return false;
}

function isTestName(id) {
  for (const p of TEST_PREFIXES) {
    if (id.startsWith(p)) return true;
  }
  return false;
}

function collectBeanFiles() {
  let patterns;
  if (modules.length > 0) {
    patterns = modules.flatMap(m => [
      resolve(ROOT, m, '**/*.beans.xml'),
      resolve(ROOT, m, '**/*.beans.xml'),
    ]);
  } else {
    // 全仓库：扫描顶层 nop-* 与子模块目录
    const topDirs = globSync('*', { cwd: ROOT })
      .filter(d => {
        try {
          const entries = globSync(resolve(ROOT, d, '**/*.beans.xml'));
          return entries.length > 0;
        } catch { return false; }
      });
    patterns = topDirs.map(d => resolve(ROOT, d, '**/*.beans.xml'));
  }
  const files = new Set();
  for (const p of patterns) {
    for (const f of globSync(p)) {
      if (!isExcluded(f)) files.add(f);
    }
  }
  return [...files].sort();
}

function analyzeFile(file) {
  const content = readFileSync(file, 'utf8');
  const findings = [];
  const beanIds = new Set();

  // 1. 收集所有 bean id
  const beanRe = /<bean\s+id="([^"]+)"/g;
  let m;
  while ((m = beanRe.exec(content)) !== null) {
    const id = m[1];
    beanIds.add(id);
    if (isTestPath(file)) continue;
    if (isLegalShortName(id)) continue;
    if (isExemptShortName(id)) continue;
    if (isTestName(id)) continue;
    findings.push({
      type: 'BEAN-ID',
      file,
      beanId: id,
      message: `短名 bean id "${id}" 未以 nop/biz_ 为前缀（code-style.md §IoC Bean 命名强约定）`,
    });
  }

  // 2. ref / value-ref / depends-on 引用检查（引用短名 bean 时，被引用 id 也应合法）
  if (!isTestPath(file)) {
    const refRe = /(?:ref|value-ref|depends-on|ioc:default-ref)="([^"]+)"/g;
    while ((m = refRe.exec(content)) !== null) {
      const ref = m[1];
      if (ref.includes('.') || ref.startsWith('?') || ref.startsWith('~') || ref.startsWith('@')) continue;
      if (isLegalShortName(ref)) continue;
      if (isExemptShortName(ref)) continue;
      if (isTestName(ref)) continue;
      findings.push({
        type: 'REF',
        file,
        ref,
        message: `ref 引用短名 "${ref}" 未以 nop/biz_ 为前缀（被引用 bean 需改名或引用处同步）`,
      });
    }

    // 3. ioc:collect-beans name-prefix 检查
    const prefixRe = /<ioc:collect-beans[^>]*name-prefix="([^"]+)"/g;
    while ((m = prefixRe.exec(content)) !== null) {
      const prefix = m[1];
      if (isLegalShortName(prefix)) continue;
      findings.push({
        type: 'COLLECT-PREFIX',
        file,
        prefix,
        message: `collect-beans name-prefix "${prefix}" 未以 nop/biz_ 为前缀（收集约定应与命名强约定一致）`,
      });
    }
  }

  return { file, beanIds, findings };
}

function main() {
  const files = collectBeanFiles();
  const allFindings = [];
  let beanCount = 0;

  for (const file of files) {
    const { beanIds, findings } = analyzeFile(file);
    beanCount += beanIds.size;
    allFindings.push(...findings);
  }

  // 去重（同一 bean id 可能在多个文件出现）
  const seen = new Set();
  const uniqueFindings = allFindings.filter(f => {
    const key = `${f.type}|${f.beanId ?? f.ref ?? f.prefix}|${toPosix(f.file)}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  const beans = uniqueFindings.filter(f => f.type === 'BEAN-ID');
  const refs = uniqueFindings.filter(f => f.type === 'REF');
  const prefixes = uniqueFindings.filter(f => f.type === 'COLLECT-PREFIX');

  const summary = {
    scannedFiles: files.length,
    scannedBeans: beanCount,
    beanIdViolations: beans.length,
    refViolations: refs.length,
    collectPrefixViolations: prefixes.length,
  };

  if (jsonMode) {
    console.log(JSON.stringify({ summary, findings: uniqueFindings }, null, 2));
  } else {
    console.log(`扫描 beans.xml: ${files.length} 个文件, ${beanCount} 个 bean id`);
    console.log(`违规统计: beanId=${beans.length}, ref=${refs.length}, collect-prefix=${prefixes.length}`);
    for (const f of uniqueFindings) {
      console.log(`[${f.type}] ${relative(ROOT, toPosix(f.file))}: ${f.message}`);
    }
    if (uniqueFindings.length === 0) {
      console.log('✅ 全部通过：所有短名 bean id 均以 nop/biz_ 为前缀');
    }
  }

  // 退出码：BEAN-ID 违规必失败；REF/COLLECT-PREFIX 在 strict 模式也失败
  const fail = beans.length > 0 || (strictMode && (refs.length > 0 || prefixes.length > 0));
  process.exit(fail ? 1 : 0);
}

main();
