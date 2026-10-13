#!/usr/bin/env node
/**
 * check-nop-ai-structure.mjs — nop-ai 结构重构 roadmap (plan 2459/WI21) 结构门禁固化。
 *
 * 断言（roadmap WI21 交付）：
 * 1. nop-rule-core 不在 nop-ai-core 依赖树（WI5 断言）
 * 2. 旧 FQN/废弃类零残留：
 *    - io.nop.ai.agent.engine.NopAiAgentException / SessionIds（WI10 上移）
 *    - io.nop.ai.api.beans / io.nop.ai.api.crud（WI14 删除）
 *    - io.nop.ai.coder.simplifier（WI15 迁 core）
 *    - io.nop.ai.mcp.server（WI15 解散）
 *    - io.nop.ai.maven（WI16 并入 toolkit）
 *    - NoOpTaskMemberRouter（WI12 正名）
 *    - guardrail/test 包在 src/main（WI13 迁出）
 *    - ERR_AI_TOOLS_ 前缀仍在 NopAiCoreErrors（WI9 迁 tools）
 *    - nop-ai-mcp-server / nop-ai-skills / nop-ai-maven 模块已解散
 * 3. 包环 import 计数不回升：security→engine / reliability→engine 保持 WI10 后水平（≤16 / 0）
 * 4. nop-ai 模块表与解散名单一致
 */
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
let failures = 0;

function grep(pattern, args, expectZero, label) {
  try {
    const out = execFileSync('grep', ['-rln', pattern, ...args], {
      cwd: ROOT, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'],
    });
    const hits = out.split('\n').filter(Boolean)
      .filter(f => !f.includes('/target/') && !f.includes('/.git/') && !f.includes('/_tmp/')
        && !f.includes('ai-dev/logs/') && !f.includes('ai-dev/audits/') && !f.includes('ai-dev/archived/')
        && !f.includes('check-nop-ai-structure.mjs'));
    if (expectZero && hits.length > 0) {
      console.error(`[FAIL] ${label}: ${hits.length} hits`);
      hits.slice(0, 5).forEach(h => console.error(`  ${h}`));
      failures++;
    } else if (!expectZero) {
      console.log(`[INFO] ${label}: ${hits.length} hits (allowed)`);
    } else {
      console.log(`[PASS] ${label}`);
    }
  } catch (e) {
    // grep exit 1 = no matches = pass for expectZero
    if (expectZero) console.log(`[PASS] ${label}`);
  }
}

// 1. core 无 nop-rule-core（WI5）
try {
  const out = execFileSync('./mvnw', ['-q', 'dependency:tree', '-pl', 'nop-ai/nop-ai-core', '-Dincludes=io.github.entropy-cloud:nop-rule-core'],
    { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
  if (out.includes('nop-rule-core')) {
    console.error('[FAIL] nop-ai-core depends on nop-rule-core');
    failures++;
  } else console.log('[PASS] nop-ai-core 无 nop-rule-core 依赖');
} catch (e) {
  if ((e.stdout || '').includes('nop-rule-core')) {
    console.error('[FAIL] nop-ai-core depends on nop-rule-core');
    failures++;
  } else console.log('[PASS] nop-ai-core 无 nop-rule-core 依赖');
}

// 2. 旧 FQN / 废弃类零残留
grep('io\\.nop\\.ai\\.agent\\.engine\\.NopAiAgentException', ['--include=*.java', 'nop-ai/'], true, '旧 FQN agent.engine.NopAiAgentException 零残留（WI10）');
grep('io\\.nop\\.ai\\.agent\\.engine\\.SessionIds', ['--include=*.java', 'nop-ai/'], true, '旧 FQN agent.engine.SessionIds 零残留（WI10）');
grep('io\\.nop\\.ai\\.api\\.beans\\.', ['--include=*.java', 'nop-ai/'], true, 'api.beans 零残留（WI14）');
grep('io\\.nop\\.ai\\.api\\.crud\\.', ['--include=*.java', 'nop-ai/'], true, 'api.crud 零残留（WI14）');
grep('io\\.nop\\.ai\\.coder\\.simplifier', ['--include=*.java', 'nop-ai/'], true, 'coder.simplifier 零残留（WI15）');
grep('io\\.nop\\.ai\\.mcp\\.server', ['--include=*.java', 'nop-ai/'], true, 'ai.mcp.server 零残留（WI15）');
grep('io\\.nop\\.ai\\.maven', ['--include=*.java', 'nop-ai/'], true, 'ai.maven 零残留（WI16）');
grep('NoOpTaskMemberRouter', ['--include=*.java', 'nop-ai/'], true, 'NoOpTaskMemberRouter 零残留（WI12）');
grep('package io\\.nop\\.ai\\.agent\\.guardrail\\.test', ['--include=*.java', 'nop-ai/nop-ai-agent/src/main'], true, 'guardrail.test 不在 src/main（WI13）');
grep('ERR_AI_TOOLS_', ['--include=*.java', 'nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java'], true, 'NopAiCoreErrors 无 tools 码（WI9）');

// 3. 包环计数不回升
try {
  const sec = execFileSync('grep', ['-rl', 'import io.nop.ai.agent.engine', '--include=*.java',
    'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security'], { cwd: ROOT, encoding: 'utf8' }).split('\n').filter(Boolean).length;
  if (sec > 16) { console.error(`[FAIL] security→engine ${sec} 文件 > 16`); failures++; }
  else console.log(`[PASS] security→engine ${sec} 文件（≤16）`);
  const rel = execFileSync('grep', ['-rl', 'import io.nop.ai.agent.engine', '--include=*.java',
    'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability'], { cwd: ROOT, encoding: 'utf8' }).split('\n').filter(Boolean).length;
  if (rel > 0) { console.error(`[FAIL] reliability→engine ${rel} 文件 > 0`); failures++; }
  else console.log('[PASS] reliability→engine 0 文件');
} catch (e) { console.log('[PASS] 包环计数（grep 无命中）'); }

// 4. 解散模块不在 nop-ai/pom 模块表
const pomContent = (() => { try { return require('fs').readFileSync(path.join(ROOT, 'nop-ai/pom.xml'), 'utf8'); } catch { return ''; } })();
for (const mod of ['nop-ai-mcp-server', 'nop-spring-mcp-server-support', 'nop-ai-maven', 'nop-ai-skills']) {
  if (pomContent.includes(`<module>${mod}</module>`)) {
    console.error(`[FAIL] 解散模块 ${mod} 仍在 nop-ai/pom.xml`);
    failures++;
  } else console.log(`[PASS] ${mod} 已解散`);
}

if (failures > 0) {
  console.error(`\n${failures} failure(s)`);
  process.exit(1);
}
console.log('\nAll nop-ai structure gates passed.');
