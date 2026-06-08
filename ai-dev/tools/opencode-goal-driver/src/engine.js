import { appendFileSync } from "node:fs";

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

export class FlowEngine {
  constructor(flowDef, delegates) {
    this.flow = flowDef;
    this.delegates = delegates;
    this.context = new Map();
    this.visitCounts = new Map();
    this.retryCounts = new Map();
    this.appendBuffers = new Map();
    this.logEntries = [];
    this.startTime = null;
  }

  _log(msg) {
    const line = `[${localTimeStr()}] ${msg}`;
    this.logEntries.push(line);
    console.log(line);
    const logFile = this.delegates.logFile;
    if (logFile) {
      try { appendFileSync(logFile, line + "\n"); } catch {}
    }
  }

  _result(status, stepCount) {
    return {
      status,
      stepCount,
      elapsed: this.startTime ? durationStr(Date.now() - this.startTime) : "N/A",
      history: this.logEntries,
    };
  }

  _templateVar(str, vars) {
    if (typeof str !== "string") return str;
    return str.replace(/\{(\w+)\}/g, (_, k) => vars[k] ?? `{${k}}`);
  }

  _buildPrompt(stepName, stepDef) {
    let prompt = stepDef.prompt || "";
    prompt = this._templateVar(prompt, this.delegates.vars || {});

    const buf = this.appendBuffers.get(stepName);
    if (buf) {
      prompt += "\n" + buf;
    }
    return prompt;
  }

  async _executeAgentStep(stepName, stepDef) {
    const prompt = this._buildPrompt(stepName, stepDef);
    return await this.delegates.runAgent(stepName, prompt, stepDef.system || "");
  }

  async _executeToolStep(stepName, stepDef) {
    const command = this._templateVar(stepDef.command || "", this.delegates.vars || {});
    const timeout = stepDef.timeout || 0;
    return await this.delegates.runTool(stepName, command, { timeout });
  }

  async _executeScriptStep(stepName, stepDef) {
    const marker = await stepDef.run(this.delegates);
    return { ok: true, marker, text: String(marker) };
  }

  async _resolveMarker(result, stepDef) {
    if (stepDef.type === "tool") {
      return result.ok ? "pass" : "fail";
    }
    if (stepDef.type === "script") {
      return result.marker;
    }
    if (!result.text) return null;

    const tag = extractTag(result.text, stepDef.resultTag);
    if (tag) return tag;

    if (this.delegates.runParseAgent) {
      const parsePrompt = [
        `输出中未找到 <${stepDef.resultTag}> 标签，请阅读以下 AI 输出并推断结果。`,
        `预期取值: ${Object.keys(stepDef.transitions).join(", ")}`,
        `只输出 <${stepDef.resultTag}>值</${stepDef.resultTag}> 格式，不要其他内容。`,
        ``,
        `AI 输出内容：`,
        result.text,
      ].join("\n");
      const retry = await this.delegates.runParseAgent(
        `parse-${stepDef.resultTag}`, parsePrompt, stepDef.system || "",
      );
      return extractTag(retry.text, stepDef.resultTag);
    }
    return null;
  }

  _formatAppend(append, fromStep, result) {
    if (!append) return "";
    if (append === true) {
      return "\n\n" + (result.text || "");
    }
    if (typeof append === "string") {
      return "\n\n" + append;
    }
    if (typeof append === "object") {
      let content = "";
      if (append.extract) {
        content = extractXmlBlock(result.text || "", append.extract) || result.text || "";
      } else {
        content = result.text || "";
      }
      const template = append.template || "${output}";
      return "\n\n" + template.replace(/\$\{output\}/g, content)
        .replace(/\$\{logFile\}/g, result.logFile || "N/A");
    }
    return "";
  }

  _handleRetry(fromStep, transition, stepDef, result) {
    const targetStep = transition.retry;
    const maxRetries = transition.maxRetries || stepDef.maxRetries || 3;
    const retryKey = `${fromStep}→${targetStep}`;
    const count = (this.retryCounts.get(retryKey) || 0) + 1;
    this.retryCounts.set(retryKey, count);

    this._log(`  retry ${retryKey} (${count}/${maxRetries})`);

    if (count > maxRetries) {
      const onMax = stepDef.onMaxRetries || transition.onMaxRetries || { done: "max_retries" };
      this._log(`  maxRetries exceeded for ${retryKey} → ${JSON.stringify(onMax)}`);
      return onMax;
    }

    const appendText = this._formatAppend(transition.append, fromStep, result);
    if (appendText) {
      const existing = this.appendBuffers.get(targetStep) || "";
      if (count > 1) {
        this.appendBuffers.set(targetStep, existing + "\n───────────────\n" + appendText);
      } else {
        this.appendBuffers.set(targetStep, appendText);
      }
    }

    return { goto: targetStep };
  }

  async run(entryOverride) {
    this.startTime = Date.now();
    let currentStep = entryOverride || this.flow.entry;
    const maxTotalSteps = this.flow.maxTotalSteps || 100;
    const maxCycleVisits = this.flow.maxCycleVisits || 10;
    let totalSteps = 0;

    while (totalSteps < maxTotalSteps) {
      const stepDef = this.flow.steps[currentStep];
      if (!stepDef) {
        this._log(`Unknown step: ${currentStep}`);
        return this._result("unknown_step", totalSteps);
      }

      const visits = (this.visitCounts.get(currentStep) || 0) + 1;
      this.visitCounts.set(currentStep, visits);
      if (visits > maxCycleVisits) {
        this._log(`maxCycleVisits (${maxCycleVisits}) exceeded for step ${currentStep}`);
        return this._result("max_cycles", totalSteps);
      }

      totalSteps++;
      this._log(`[step ${totalSteps}] ${currentStep} (visit #${visits})`);

      let result;
      try {
        if (stepDef.type === "agent") {
          result = await this._executeAgentStep(currentStep, stepDef);
        } else if (stepDef.type === "tool") {
          result = await this._executeToolStep(currentStep, stepDef);
        } else if (stepDef.type === "script") {
          result = await this._executeScriptStep(currentStep, stepDef);
        } else {
          return this._result("unknown_type", totalSteps);
        }
      } catch (err) {
        this._log(`  error: ${err.message}`);
        const onError = stepDef.onError || { done: "failed" };
        if (onError.done) return this._result(onError.done, totalSteps);
        if (onError.goto) { currentStep = onError.goto; continue; }
        return this._result("failed", totalSteps);
      }

      this.context.set(currentStep, result);

      // For tool steps, ok=false is a normal "fail" marker, not an error
      // For agent steps, ok=false means subprocess was killed → onError
      if (!result.ok && stepDef.type !== "tool") {
        const onError = stepDef.onError || { done: "failed" };
        this._log(`  subprocess failed → ${JSON.stringify(onError)}`);
        if (onError.done) return this._result(onError.done, totalSteps);
        if (onError.goto) {
          if (onError.append) {
            const appendText = this._formatAppend(onError.append, currentStep, result);
            const existing = this.appendBuffers.get(onError.goto) || "";
            this.appendBuffers.set(onError.goto, existing + appendText);
          }
          currentStep = onError.goto;
          continue;
        }
        if (onError.retry) {
          const action = this._handleRetry(currentStep, onError, stepDef, result);
          if (action.done) return this._result(action.done, totalSteps);
          if (action.goto) {
            if (action.append) {
              const appendText = this._formatAppend(action.append, currentStep, result);
              const existing = this.appendBuffers.get(action.goto) || "";
              this.appendBuffers.set(action.goto, existing + appendText);
            }
            currentStep = action.goto;
            continue;
          }
        }
        return this._result("failed", totalSteps);
      }

      const marker = await this._resolveMarker(result, stepDef);
      if (!marker) {
        this._log(`  marker not found in output`);
        const onUnknown = stepDef.onUnknown || { done: "failed" };
        if (onUnknown.done) return this._result(onUnknown.done, totalSteps);
        if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
        return this._result("failed", totalSteps);
      }

      this._log(`  marker: ${marker}`);

      const transition = stepDef.transitions[marker];
      if (!transition) {
        this._log(`  no transition for marker "${marker}"`);
        return this._result("no_transition", totalSteps);
      }

      if (transition.done) {
        this._log(`  → done: ${transition.done}`);
        return this._result(transition.done, totalSteps);
      }

      if (transition.retry) {
        const action = this._handleRetry(currentStep, transition, stepDef, result);
        if (action.done) return this._result(action.done, totalSteps);
        if (action.goto) {
          if (action.append) {
            const appendText = this._formatAppend(action.append, currentStep, result);
            const existing = this.appendBuffers.get(action.goto) || "";
            this.appendBuffers.set(action.goto, existing + appendText);
          }
          currentStep = action.goto;
          continue;
        }
      }

      if (transition.goto) {
        if (transition.append) {
          const appendText = this._formatAppend(transition.append, currentStep, result);
          const existing = this.appendBuffers.get(transition.goto) || "";
          this.appendBuffers.set(transition.goto, existing + appendText);
        }
        if (transition.evidence) {
          this._log(`  recording evidence for ${currentStep}`);
        }
        currentStep = transition.goto;
        continue;
      }

      this._log(`  invalid transition: ${JSON.stringify(transition)}`);
      return this._result("invalid_transition", totalSteps);
    }

    return this._result("max_total_steps", totalSteps);
  }
}
