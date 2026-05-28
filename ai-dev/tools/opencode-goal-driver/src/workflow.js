import { appendFileSync } from "node:fs";
import { execute } from "./executor.js";
import { createStepConfigs, STEP_NAMES } from "./prompts.js";

function log(config, msg) {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${msg}`;
  console.log(line);
  if (config.logFile) appendFileSync(config.logFile, line + "\n");
}

function extractTag(text, tagName) {
  const re = new RegExp(`<${tagName}>([^<]+)</${tagName}>`);
  const m = text.match(re);
  return m ? m[1].toLowerCase().trim() : null;
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

export async function executeWorkflow(config, runner) {
  const stepCfgs = createStepConfigs(config);
  log(config, `=== OpenCode Goal Driver: ${config.moduleName} ===`);

  for (let cycle = 1; cycle <= config.maxCycles; cycle++) {
    log(config, `=== Cycle ${cycle} ===`);

    // ── 0. Health check ──
    log(config, "[健康检查] 构建模块 ...");
    const hc = await runMavenBuild(config, ["clean", "install"]);
    if (hc.ok) {
      log(config, `[健康检查] 构建通过 (${(hc.logFile ? "日志: " + hc.logFile : "mock")})`);
    } else {
      log(config, `[健康检查] 构建失败 (日志: ${hc.logFile})，尝试修复`);
      const p = stepCfgs[STEP_NAMES.FIX_BUILD];
      const out = await runner.runStep(STEP_NAMES.FIX_BUILD, p.command, p.system);
      const val = await extractTagOrAsk(runner, out.text, p.resultTag, p.markerValues, STEP_NAMES.FIX_BUILD, p.system);
      if (val !== p.markerValues.FIXED) {
        log(config, "[健康检查] 修复失败，退出");
        return { status: "failed", cycle };
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

    // ── 2. Plan ──
    if (hasIssues) {
      log(config, "[规划] 拟定修复计划 ...");
      const pp = stepCfgs[STEP_NAMES.PLAN];
      const planOut = await runner.runStep(STEP_NAMES.PLAN, pp.command, pp.system);
      const planVal = await extractTagOrAsk(runner, planOut.text, pp.resultTag, pp.markerValues, STEP_NAMES.PLAN, pp.system);

      if (planVal === pp.markerValues.CREATED) {
        log(config, "[执行] 执行修复计划 ...");
        const ep = stepCfgs[STEP_NAMES.EXECUTE];
        const execOut = await runner.runStep(STEP_NAMES.EXECUTE, ep.command, ep.system);
        const execVal = await extractTagOrAsk(runner, execOut.text, ep.resultTag, ep.markerValues, STEP_NAMES.EXECUTE, ep.system);
        if (execVal !== ep.markerValues.SUCCESS) {
          log(config, "[执行] 未完全成功");
        }
      }
    }

    // ── 4. Verify ──
    log(config, "[验证] 全量构建测试 ...");
    const vr = await runMavenBuild(config, ["clean", "install"]);
    if (!vr.ok) {
      log(config, "[验证] 失败，下一轮重试");
      continue;
    }

    // ── 5. Evaluate ──
    log(config, "[评估] 目标达成判断 ...");
    const evp = stepCfgs[STEP_NAMES.EVAL];
    const evalOut = await runner.runStep(STEP_NAMES.EVAL, evp.command, evp.system);
    const evalVal = await extractTagOrAsk(runner, evalOut.text, evp.resultTag, evp.markerValues, STEP_NAMES.EVAL, evp.system);

    if (evalVal === evp.markerValues.COMPLETE) {
      log(config, `=== ${config.moduleName} 完善完成 (cycle ${cycle}) ===`);
      return { status: "completed", cycle };
    }

    log(config, "[评估] 目标未完全达成，继续下一轮");
  }

  log(config, `=== FAIL: 超过最大循环次数 ${config.maxCycles} ===`);
  return { status: "max_cycles", cycle: config.maxCycles };
}
