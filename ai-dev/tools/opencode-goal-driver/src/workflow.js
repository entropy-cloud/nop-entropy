import { appendFileSync } from "node:fs";
import { execute } from "./executor.js";
import { createStepConfigs, STEP_NAMES } from "./prompts.js";

function localTimeStr(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function durationStr(ms) {
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return `${h}h${m}m${sec}s`;
  if (m > 0) return `${m}m${sec}s`;
  return `${sec}s`;
}

function log(config, msg) {
  const line = `[${localTimeStr()}] ${msg}`;
  console.log(line);
  if (config.logFile) appendFileSync(config.logFile, line + "\n");
}

function extractTag(text, tagName) {
  const re = new RegExp(`<${tagName}>([^<]+)</${tagName}>`, "g");
  const matches = [...text.matchAll(re)];
  if (matches.length === 0) return null;
  return matches[matches.length - 1][1].toLowerCase().trim();
}

function extractXmlBlock(text, tagName) {
  const re = new RegExp(`<${tagName}>[\\s\\S]*?<\\/${tagName}>`);
  const m = text.match(re);
  return m ? m[0] : null;
}

async function extractTagOrAsk(runner, output, tagName, validValues, stepName, system) {
  const val = extractTag(output, tagName);
  if (val) return val;

  const prompt = [
    `输出中未找到 <${tagName}> 标签，请阅读以下 AI 输出并推断结果。`,
    `预期取值: ${Object.values(validValues).join(", ")}`,
    `只输出 <${tagName}>值</${tagName}> 格式，不要其他内容。`,
    ``,
    `AI 输出内容：`,
    output,
  ].join("\n");

  const retry = await runner.runStep(`parse-${tagName}`, prompt, system);
  return extractTag(retry.text, tagName);
}

async function runMavenBuild(config, args) {
  if (config.dryRun) {
    log(config, `[MOCK] mvnw ${args.join(" ")}`);
    return { ok: true, logFile: null };
  }
  const fullArgs = [...args, "-pl", config.moduleName, "-am", "-T", "1C"];
  return execute(config, "mvnw", "./mvnw", fullArgs, { cwd: config.projectRoot, timeout: 1_800_000 });
}

/**
 * 双循环结构：
 *
 * 外循环（outer）：审计 → 发现问题？
 *   ├─ 无问题 → DONE
 *   └─ 有问题 → 拟制计划
 *       ↓
 *     内循环（inner）：执行 → 独立验证(closure audit)
 *       ├─ 未完成 → 提取 XML 摘要 → 继续执行
 *       └─ 完成 → break
 *       ↓
 *     验证构建 → 回到外循环（再审计）
 *
 * 再审计无新问题 → 彻底结束
 */
export async function executeWorkflow(config, runner) {
  const stepCfgs = createStepConfigs(config);
  const startTime = Date.now();
  log(config, `=== OpenCode Goal Driver: ${config.moduleName} ===`);
  log(config, `maxCycles=${config.maxCycles}  maxInnerCycles=${config.maxInnerCycles}`);

  for (let outer = 1; outer <= config.maxCycles; outer++) {
    log(config, `╔══ Outer Cycle ${outer}/${config.maxCycles} ══`);

    // ── 0. Health check ──
    log(config, "[健康检查] 构建模块 ...");
    const hc = await runMavenBuild(config, ["clean", "install"]);
    if (hc.ok) {
      log(config, `[健康检查] 构建通过`);
    } else {
      log(config, `[健康检查] 构建失败 (日志: ${hc.logFile})，尝试修复`);
      const p = stepCfgs[STEP_NAMES.FIX_BUILD];
      const out = await runner.runStep(STEP_NAMES.FIX_BUILD, p.command, p.system);
      const val = await extractTagOrAsk(runner, out.text, p.resultTag, p.markerValues, STEP_NAMES.FIX_BUILD, p.system);
      if (val !== p.markerValues.FIXED) {
        log(config, "[健康检查] 修复失败，退出");
        return { status: "failed", cycle: outer, elapsed: durationStr(Date.now() - startTime) };
      }
    }

    // ── 1. Deep audit ──
    log(config, "[深度审计] 执行多维度深度审计 ...");
    const dp = stepCfgs[STEP_NAMES.DEEP_AUDIT];
    const deepOut = await runner.runStep(STEP_NAMES.DEEP_AUDIT, dp.command, dp.system);
    const deepVal = await extractTagOrAsk(runner, deepOut.text, dp.resultTag, dp.markerValues, STEP_NAMES.DEEP_AUDIT, dp.system);
    let hasIssues = deepVal === dp.markerValues.ISSUES;

    // ── 1b. Adversarial review ──
    log(config, "[对抗审查] 执行对抗性审查 ...");
    const arp = stepCfgs[STEP_NAMES.ADVERSARIAL];
    const advOut = await runner.runStep(STEP_NAMES.ADVERSARIAL, arp.command, arp.system);
    const advVal = await extractTagOrAsk(runner, advOut.text, arp.resultTag, arp.markerValues, STEP_NAMES.ADVERSARIAL, arp.system);
    if (advVal === arp.markerValues.ISSUES) hasIssues = true;

    // ── 2. No issues → DONE ──
    if (!hasIssues) {
      const elapsed = durationStr(Date.now() - startTime);
      log(config, `╚══ ${config.moduleName} 审计无问题，完善完成 (outer cycle ${outer}) ══`);
      log(config, `══ 总耗时: ${elapsed} ══`);
      return { status: "completed", cycle: outer, elapsed };
    }

    // ── 3. Plan ──
    log(config, "[规划] 基于审计发现拟定修复计划 ...");
    const pp = stepCfgs[STEP_NAMES.PLAN];
    const planOut = await runner.runStep(STEP_NAMES.PLAN, pp.command, pp.system);
    const planVal = await extractTagOrAsk(runner, planOut.text, pp.resultTag, pp.markerValues, STEP_NAMES.PLAN, pp.system);

    if (planVal !== pp.markerValues.CREATED) {
      log(config, "[规划] 未创建计划，跳过执行，进入下一轮审计");
      continue;
    }

    // ── 4. Inner loop: Execute → Closure Audit → retry ──
    let planComplete = false;
    let remainingXml = "";

    for (let inner = 1; inner <= config.maxInnerCycles; inner++) {
      log(config, `║ Inner Cycle ${inner}/${config.maxInnerCycles}`);

      // ── 4a. Execute ──
      const ep = stepCfgs[STEP_NAMES.EXECUTE];
      let execCommand = ep.command;
      if (remainingXml) {
        execCommand += `\n\n以下是独立验证agent在上一轮发现的未完成项，请继续完成：\n${remainingXml}`;
        log(config, `[执行] 继续执行未完成项 (${inner}/${config.maxInnerCycles}) ...`);
      } else {
        log(config, `[执行] 执行修复计划 (${inner}/${config.maxInnerCycles}) ...`);
      }
      const execOut = await runner.runStep(STEP_NAMES.EXECUTE, execCommand, ep.system);
      await extractTagOrAsk(runner, execOut.text, ep.resultTag, ep.markerValues, STEP_NAMES.EXECUTE, ep.system);

      // ── 4b. Closure audit (independent sub-agent) ──
      log(config, "[验证] 独立子agent验证计划完成度 ...");
      const cap = stepCfgs[STEP_NAMES.CLOSURE_AUDIT];
      const caOut = await runner.runStep(STEP_NAMES.CLOSURE_AUDIT, cap.command, cap.system);
      const caVal = await extractTagOrAsk(runner, caOut.text, cap.resultTag, cap.markerValues, STEP_NAMES.CLOSURE_AUDIT, cap.system);

      if (caVal === cap.markerValues.COMPLETE) {
        log(config, "[验证] 独立验证通过，计划完成");
        planComplete = true;
        break;
      }

      // ── 4c. Not complete → extract remaining XML → retry ──
      log(config, "[验证] 计划未完成，提取未完成项 ...");
      const remaining = extractXmlBlock(caOut.text, "REMAINING");
      if (remaining) {
        remainingXml = remaining;
        const itemCount = (remaining.match(/<item>/g) || []).length;
        log(config, `[验证] 发现 ${itemCount} 个未完成项，进入下一轮内循环`);
      } else {
        remainingXml = "<REMAINING><item>未完成项未知（closure audit 未输出 REMAINING 块），请重新检查计划 Exit Criteria</item></REMAINING>";
        log(config, "[验证] 未找到 REMAINING XML 块，使用默认提示");
      }
    }

    if (!planComplete) {
      log(config, `[警告] 内循环 ${config.maxInnerCycles} 轮后计划仍未完成，进入下一轮外循环重新审计`);
    }

    // ── 5. Verify build ──
    log(config, "[构建验证] 全量构建测试 ...");
    const vr = await runMavenBuild(config, ["clean", "install"]);
    if (!vr.ok) {
      log(config, `[构建验证] 失败 (日志: ${vr.logFile})，下一轮外循环重试`);
      continue;
    }

    log(config, `╚══ Outer cycle ${outer} 完成，下一轮将重新审计 ══`);
  }

  const elapsed = durationStr(Date.now() - startTime);
  log(config, `=== FAIL: 超过最大外循环次数 ${config.maxCycles} ===`);
  log(config, `══ 总耗时: ${elapsed} ══`);
  return { status: "max_cycles", cycle: config.maxCycles, elapsed };
}
