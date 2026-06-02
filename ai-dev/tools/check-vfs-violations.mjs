#!/usr/bin/env node
/**
 * 启发式检查：指定模块中使用 java.nio.file / java.io.File / Path 的代码
 *
 * VFS 优先原则（docs-for-ai/00-start-here/ai-defaults.md）：
 *   Files.readString(path) / new FileInputStream(file) → IResource.readText() / getInputStream()
 *   resource.toFile().toPath()                        → IResource.getPath() / getStdPath()
 *   遍历目录                                           → depthIterator + BatchQueue
 *
 * 用法:
 *   node ai-dev/tools/check-vfs-violations.mjs                      # 默认检查 nop-code, nop-stream
 *   node ai-dev/tools/check-vfs-violations.mjs nop-auth nop-wf      # 检查指定模块
 *   node ai-dev/tools/check-vfs-violations.mjs --all                # 检查所有 nop-* 模块
 */

import { readFileSync } from 'node:fs';
import { globSync } from 'node:fs';
import { resolve, relative, basename } from 'node:path';

const ROOT = resolve(import.meta.dirname, '../..');

const args = process.argv.slice(2);
const allMode = args.includes('--all');
let modules;
if (allMode) {
  const top = globSync('*', { cwd: ROOT }).filter(d => {
    try { globSync(resolve(ROOT, d, '**/*.java')); return true; } catch { return false; }
  }).filter(d => d.startsWith('nop-'));
  modules = [...new Set(top.map(d => d.split('/').filter(p => p.startsWith('nop-'))[0]))].filter(Boolean);
} else if (args.length > 0) {
  modules = args.filter(a => !a.startsWith('--'));
} else {
  modules = ['nop-code', 'nop-stream'];
}

const PATTERNS = [
  { regex: /import\s+java\.nio\.file\.Files\s*;/, tag: 'IMPORT-FILES', desc: 'import java.nio.file.Files' },
  { regex: /import\s+java\.nio\.file\.Path\s*;/, tag: 'IMPORT-PATH', desc: 'import java.nio.file.Path' },
  { regex: /import\s+java\.nio\.file\.\*\s*;/, tag: 'IMPORT-NIO-STAR', desc: 'import java.nio.file.*' },
  { regex: /import\s+java\.nio\.file\.Paths\s*;/, tag: 'IMPORT-PATHS', desc: 'import java.nio.file.Paths' },
  { regex: /new\s+java\.io\.File\s*\(/, tag: 'NEW-FILE', desc: 'new java.io.File(...)' },
  { regex: /(?<!\w)new\s+File\s*\(/, tag: 'NEW-FILE', desc: 'new File(...)' },
  { regex: /\.toPath\s*\(\)/, tag: 'TO-PATH', desc: '.toPath()' },
  { regex: /Files\.readString\s*\(/, tag: 'FILES-READ', desc: 'Files.readString(...)' },
  { regex: /Files\.readAllBytes\s*\(/, tag: 'FILES-READ', desc: 'Files.readAllBytes(...)' },
  { regex: /Files\.lines\s*\(/, tag: 'FILES-READ', desc: 'Files.lines(...)' },
  { regex: /(?<!\w)Files\.size\s*\(/, tag: 'FILES-SIZE', desc: 'Files.size(path)' },
  { regex: /Files\.walk\s*\(/, tag: 'FILES-WALK', desc: 'Files.walk(...)' },
  { regex: /Files\.getLastModifiedTime\s*\(/, tag: 'FILES-MTIME', desc: 'Files.getLastModifiedTime(...)' },
  { regex: /Files\.isRegularFile\s*\(/, tag: 'FILES-ISFILE', desc: 'Files.isRegularFile(...)' },
  { regex: /Files\.write\s*\(/, tag: 'FILES-WRITE', desc: 'Files.write(...)' },
  { regex: /Files\.exists\s*\(/, tag: 'FILES-EXISTS', desc: 'Files.exists(...)' },
  { regex: /Files\.createDirectories\s*\(/, tag: 'FILES-MKDIR', desc: 'Files.createDirectories(...)' },
  { regex: /Files\.deleteIfExists\s*\(/, tag: 'FILES-DELETE', desc: 'Files.deleteIfExists(...)' },
  { regex: /Files\.move\s*\(/, tag: 'FILES-MOVE', desc: 'Files.move(...)' },
  { regex: /Files\.copy\s*\(/, tag: 'FILES-COPY', desc: 'Files.copy(...)' },
  { regex: /Path\.of\s*\(/, tag: 'PATH-OF', desc: 'Path.of(...)' },
  { regex: /Paths\.get\s*\(/, tag: 'PATHS-GET', desc: 'Paths.get(...)' },
  { regex: /\.relativize\s*\(/, tag: 'PATH-RELATIVIZE', desc: 'path.relativize(...) → 字符串操作 / getStdPath()' },
  { regex: /new\s+FileInputStream\s*\(/, tag: 'FILE-STREAM', desc: 'new FileInputStream(...)' },
  { regex: /new\s+FileOutputStream\s*\(/, tag: 'FILE-STREAM', desc: 'new FileOutputStream(...)' },
  { regex: /new\s+FileReader\s*\(/, tag: 'FILE-STREAM', desc: 'new FileReader(...)' },
  { regex: /new\s+FileWriter\s*\(/, tag: 'FILE-STREAM', desc: 'new FileWriter(...)' },
];

const EXCLUDE = /\/_gen\/|\/test\/|Test\.java$|IT\.java$/;

const WHITELIST = {
  'nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java': {
    reason: 'ProcessBuilder.directory() requires java.io.File (Java API hard requirement)',
    tags: ['NEW-FILE'],
  },
  'nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java': {
    reason: 'validateLocalPath canonical path comparison + resolveVfsPath relative-to-absolute conversion',
    tags: ['NEW-FILE', 'TO-PATH'],
  },
  'nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java': {
    reason: 'ICheckpointStorage implementation whose purpose is local file operations',
    tags: '*',
  },
  'nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java': {
    reason: 'Savepoint path fallback uses local filesystem (delegates to LocalFileCheckpointStorage)',
    tags: ['PATHS-GET', 'FILES-EXISTS'],
  },
};

// 去除误报：.size() 是 Collection/List 方法调用，不是 Files.size
function isFalsePositive(tag, trimmed) {
  if (tag === 'FILES-SIZE') return !trimmed.includes('Files.size(');
  return false;
}

let totalFindings = 0;
const fileFindings = {};

for (const mod of modules) {
  const modDir = resolve(ROOT, mod);
  let files;
  try {
    files = globSync(resolve(modDir, '**', '*.java'));
  } catch {
    continue;
  }

  for (const file of files) {
    if (EXCLUDE.test(file)) continue;

    const content = readFileSync(file, 'utf-8');
    const lines = content.split('\n');
    const relPath = relative(ROOT, file);

    for (let i = 0; i < lines.length; i++) {
      const trimmed = lines[i].trim();
      if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) continue;

      for (const p of PATTERNS) {
        if (p.regex.test(trimmed) && !isFalsePositive(p.tag, trimmed)) {
          const wl = WHITELIST[relPath];
          if (wl && (wl.tags === '*' || wl.tags.includes(p.tag))) continue;
          if (!fileFindings[relPath]) fileFindings[relPath] = [];
          fileFindings[relPath].push({
            line: i + 1,
            tag: p.tag,
            desc: p.desc,
            code: trimmed.substring(0, 120)
          });
          totalFindings++;
        }
      }
    }
  }
}

console.log(`\n=== VFS 违规检查报告 ===`);
console.log(`检查模块: ${modules.join(', ')}`);
console.log(`排除: 测试文件、_gen/ 目录`);
console.log(`总发现数: ${totalFindings}\n`);

for (const [file, findings] of Object.entries(fileFindings).sort()) {
  console.log(`\n  ${file}`);

  const byTag = {};
  for (const f of findings) {
    if (!byTag[f.tag]) byTag[f.tag] = [];
    byTag[f.tag].push(f);
  }

  for (const [tag, items] of Object.entries(byTag)) {
    console.log(`    [${tag}] ${items[0].desc} (${items.length} 处)`);
    for (const item of items.slice(0, 3)) {
      console.log(`      L${item.line}: ${item.code}`);
    }
    if (items.length > 3) {
      console.log(`      ... +${items.length - 3} more`);
    }
  }
}

const tagCounts = {};
for (const findings of Object.values(fileFindings)) {
  for (const f of findings) {
    tagCounts[f.tag] = (tagCounts[f.tag] || 0) + 1;
  }
}
console.log(`\n=== 汇总 ===`);
for (const [tag, count] of Object.entries(tagCounts).sort((a, b) => b[1] - a[1])) {
  console.log(`  ${tag}: ${count}`);
}
console.log(`\n共 ${Object.keys(fileFindings).length} 个文件有违规，共 ${totalFindings} 处`);
process.exit(totalFindings > 0 ? 1 : 0);
