#!/usr/bin/env node
/**
 * I*Biz 接口合规检查：服务实现层接口的方法必须满足平台契约。
 *
 * 目标：`I*Biz.java` 接口（位于 `*-dao` 模块的 biz 包，`extends ICrudBiz<...>`）。
 *   I*Biz 是后端服务间调用契约（见 nop-entropy/docs-for-ai/02-core-guides/service-layer.md），
 *   接口无 BizLoader/构造函数/私有 helper，方法都是 public abstract 服务契约。
 *
 * 规则（对齐 service-layer.md 反模式表）：
 *   1. 每个方法必须标注 @BizQuery / @BizMutation / @BizAction 之一
 *      —— BizProxyInvocationHandler 通过注解扫描路由，无注解则代理无法识别、GraphQL 不暴露。
 *   2. 方法最后一个参数必须是 IServiceContext
 *      —— 对齐基类 ICrudBiz 契约（全方法末参为 context），承载 IUserContext（身份/数据权限）、
 *      缓存、事务上下文；跨服务调用（A 域注入调 B 域 I*Biz）必须透传，否则下游丢身份、跳权限。
 *
 * 解析：tree-sitter-java（WASM，经 web-tree-sitter），免 native 编译。
 *
 * 用法:
 *   node check-ibiz-interfaces.mjs [path...]   # 默认扫描 nop-entropy 与兄弟 nop-app-erp
 *   node check-ibiz-interfaces.mjs --json
 *   node check-ibiz-interfaces.mjs module-purchase
 *
 * 退出码: 有违规为 1，无违规为 0。
 */

import { Parser, Language } from 'web-tree-sitter';
import { createRequire } from 'module';
import { readFileSync, existsSync, statSync, readdirSync } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const require = createRequire(import.meta.url);
const __dirname = fileURLToPath(new URL('.', import.meta.url));
const WASM = require.resolve('tree-sitter-java/tree-sitter-java.wasm');

await Parser.init();
const lang = await Language.load(WASM);
const parser = new Parser();
parser.setLanguage(lang);

const BIZ_ANNOS = new Set(['BizQuery', 'BizMutation', 'BizAction']);
const SKIP_DIRS = new Set(['node_modules', 'target', 'test', 'tests', '.git', '_gen', 'build']);

// ---- 文件收集 ----

function* walkJava(dir) {
  let ents;
  try {
    ents = readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of ents) {
    if (e.isDirectory()) {
      if (SKIP_DIRS.has(e.name) || e.name.startsWith('.')) continue;
      yield* walkJava(path.join(dir, e.name));
    } else if (e.isFile() && isIBizFile(e.name)) {
      yield path.join(dir, e.name);
    }
  }
}

/** I*Biz 接口文件名：I 开头 + 大写 + ... + Biz.java */
function isIBizFile(name) {
  return /^I[A-Z][A-Za-z0-9_]*Biz\.java$/.test(name);
}

function collectTargets(args) {
  const out = [];
  const rest = args.filter(a => !a.startsWith('--'));
  if (rest.length === 0) {
    const nopEntropy = path.resolve(__dirname, '../..');
    out.push(nopEntropy);
    const sibling = path.resolve(__dirname, '../../../nop-app-erp');
    if (existsSync(sibling)) out.push(sibling);
    return out;
  }
  for (const a of rest) {
    if (!existsSync(a)) continue;
    out.push(a);
  }
  return out;
}

// ---- AST helpers ----

function childOfType(node, type) {
  for (const c of node.children) if (c.type === type) return c;
  return null;
}

/** 该接口声明是否 extends ICrudBiz */
function isBizInterface(ifaceNode) {
  const ex = childOfType(ifaceNode, 'extends_interfaces');
  return !!(ex && /\bICrudBiz\b/.test(ex.text));
}

/** 方法的最近祖先 I*Biz 接口（确认是 Biz 契约方法） */
function enclosingBizInterface(node) {
  let cur = node.parent;
  while (cur) {
    if (cur.type === 'interface_declaration') {
      return isBizInterface(cur) ? cur : null;
    }
    cur = cur.parent;
  }
  return null;
}

function methodAnnotations(methodNode) {
  const mods = childOfType(methodNode, 'modifiers');
  const set = new Set();
  if (!mods) return set;
  for (const m of mods.children) {
    if (m.type === 'marker_annotation' || m.type === 'annotation') {
      const name = m.childForFieldName('name');
      if (name) set.add(name.text);
    }
  }
  return set;
}

function lastParamType(methodNode) {
  const params = methodNode.childForFieldName('parameters');
  if (!params) return null;
  const fps = params.children.filter(c => c.type === 'formal_parameter');
  if (fps.length === 0) return null;
  const t = fps[fps.length - 1].childForFieldName('type');
  return t ? t.text : null;
}

function methodName(methodNode) {
  const n = methodNode.childForFieldName('name');
  return n ? n.text : '<anonymous>';
}

// ---- 单文件检查 ----

function checkFile(file) {
  const code = readFileSync(file, 'utf-8');
  const tree = parser.parse(code);
  const violations = [];
  const stack = [tree.rootNode];
  while (stack.length) {
    const node = stack.pop();
    if (node.type === 'method_declaration' && enclosingBizInterface(node)) {
      const annos = methodAnnotations(node);
      const hasBiz = [...annos].some(a => BIZ_ANNOS.has(a));
      const line = node.startPosition.row + 1;
      const name = methodName(node);
      if (!hasBiz) {
        violations.push({ file, line, rule: 'ibiz-missing-annotation', name,
          detail: `${name} 缺少 @BizQuery/@BizMutation/@BizAction 注解（I*Biz 契约要求，动态代理路由依赖它）` });
      }
      const last = lastParamType(node);
      if (last !== 'IServiceContext') {
        violations.push({ file, line, rule: 'ibiz-missing-context', name,
          detail: `${name} 最后一个参数应为 IServiceContext（当前: ${last || '无参数'}）。对齐 ICrudBiz 契约，承载身份/权限/缓存，跨服务调用必须透传` });
      }
    }
    for (let i = node.childCount - 1; i >= 0; i--) stack.push(node.child(i));
  }
  return violations;
}

// ---- main ----

const argv = process.argv.slice(2);
const asJson = argv.includes('--json');
const targets = collectTargets(argv);

const files = [];
for (const t of targets) {
  if (statSync(t).isDirectory()) for (const f of walkJava(t)) files.push(f);
  else if (isIBizFile(path.basename(t))) files.push(t);
}

const violations = [];
for (const f of files) violations.push(...checkFile(f));

if (asJson) {
  process.stdout.write(JSON.stringify(violations, null, 2) + '\n');
} else {
  const rel = f => path.relative(process.cwd(), f);
  for (const v of violations) {
    console.log(`\x1b[31mERROR\x1b[0m [${v.rule}] ${rel(v.file)}:${v.line} — ${v.detail}`);
  }
  const byRule = {};
  for (const v of violations) byRule[v.rule] = (byRule[v.rule] || 0) + 1;
  console.log(`\n扫描 ${files.length} 个 I*Biz 接口，命中 ${violations.length} 处违规：` +
    (Object.keys(byRule).length ? Object.entries(byRule).map(([k, n]) => `${k}=${n}`).join(' ') : '无'));
}

process.exit(violations.length ? 1 : 0);
