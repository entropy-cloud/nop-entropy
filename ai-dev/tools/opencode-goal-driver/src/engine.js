import { appendFileSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

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
    this.lastSessionId = null;
    this.markerCorrectionCounts = new Map();
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
    return str.replace(/\{(\w[\w.]*)\}/g, (_, k) => {
      if (vars[k] !== undefined) return String(vars[k]);
      const parts = k.split(".");
      let val = vars;
      for (const p of parts) {
        if (val == null || typeof val !== "object") return `{${k}}`;
        val = val[p];
      }
      return val !== undefined ? String(val) : `{${k}}`;
    });
  }

  _buildVars() {
    const vars = { ...(this.delegates.vars || {}) };
    for (const [name, result] of this.context) {
      const prefix = `steps.${name}`;
      vars[`${prefix}.text`] = result.text || "";
      vars[`${prefix}.ok`] = String(result.ok ?? "");
      vars[`${prefix}.logFile`] = result.logFile || "";
      vars[`${prefix}.marker`] = result.marker || "";
      if (result.vars) {
        for (const [vk, vv] of Object.entries(result.vars)) {
          if (Array.isArray(vv)) {
            vars[vk] = vv;
          } else {
            vars[vk] = String(vv);
          }
        }
      }
    }
    return vars;
  }

  _loadPromptFile(promptFile) {
    if (!promptFile) return "";
    const vars = this._buildVars();
    const resolved = this._templateVar(promptFile, vars);
    const candidates = [
      resolved,
      resolve(this.delegates.config?.projectRoot || ".", resolved),
    ];
    for (const p of candidates) {
      try { return readFileSync(p, "utf8"); } catch {}
    }
    this._log(`  WARNING: promptFile not found: ${promptFile}`);
    return "";
  }

  _buildPrompt(stepName, stepDef) {
    let prompt;
    if (stepDef.promptFile) {
      prompt = this._loadPromptFile(stepDef.promptFile);
    } else {
      prompt = stepDef.prompt || "";
    }
    prompt = this._templateVar(prompt, this._buildVars());

    const buf = this.appendBuffers.get(stepName);
    if (buf) {
      prompt += "\n" + buf;
    }
    return prompt;
  }

  _tryAliasMarker(marker, transitions) {
    if (transitions[marker]) return marker;
    const aliases = this.flow.markerAliases || {};
    if (aliases[marker] && transitions[aliases[marker]]) return aliases[marker];
    if (marker && typeof marker === "string") {
      const lower = marker.toLowerCase();
      for (const key of Object.keys(transitions)) {
        if (key.toLowerCase() === lower) return key;
      }
    }
    return null;
  }

  async _executeAgentStep(stepName, stepDef, sessionId) {
    const prompt = this._buildPrompt(stepName, stepDef);
    const result = await this.delegates.runAgent(stepName, prompt, stepDef.system || "", sessionId);
    if (result && result.sessionId) this.lastSessionId = result.sessionId;
    return result;
  }

  async _executeToolStep(stepName, stepDef) {
    const command = this._templateVar(stepDef.command || "", this._buildVars());
    const timeout = stepDef.timeout || 0;
    return await this.delegates.runTool(stepName, command, { timeout });
  }

  async _executeScriptStep(stepName, stepDef) {
    let scriptFn;
    if (typeof stepDef.run === "function") {
      scriptFn = stepDef.run;
    } else if (typeof stepDef.run === "string") {
      const scripts = this.delegates.scripts || {};
      scriptFn = scripts[stepDef.run];
      if (!scriptFn) throw new Error(`script "${stepDef.run}" not found in delegates.scripts`);
    } else {
      throw new Error(`step "${stepName}" has invalid "run" field`);
    }
    const args = stepDef.scriptArgs ? this._templateVarsObj(stepDef.scriptArgs) : undefined;
    const ret = await scriptFn(this.delegates, args);
    if (typeof ret === "object" && ret.marker !== undefined) {
      return { ok: true, marker: ret.marker, text: String(ret.marker), vars: ret.vars || {} };
    }
    return { ok: true, marker: ret, text: String(ret) };
  }

  _templateVarsObj(obj) {
    const vars = this._buildVars();
    const result = {};
    for (const [k, v] of Object.entries(obj)) {
      result[k] = typeof v === "string" ? this._templateVar(v, vars) : v;
    }
    return result;
  }

  async _executeSubFlowStep(stepName, stepDef) {
    const subFlowDef = this._resolveSubFlowDef(stepDef.flow);
    if (!subFlowDef) throw new Error(`sub-flow "${stepDef.flow}" not found`);

    const flowArgs = stepDef.flowArgs ? this._templateVarsObj(stepDef.flowArgs) : {};
    const forEachKey = stepDef.forEach;

    if (forEachKey) {
      const vars = this._buildVars();
      const items = vars[forEachKey];
      if (!items || !Array.isArray(items) || items.length === 0) {
        return { ok: true, marker: "all_complete", text: "no items to process" };
      }

      this._log(`  subflow "${stepDef.flow}" iterating over ${items.length} items`);

      let failedCount = 0;
      let successCount = 0;
      const summaries = [];

      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        const itemLabel = typeof item === "string" ? item : JSON.stringify(item);
        this._log(`  subflow item ${i + 1}/${items.length}: ${itemLabel}`);

        const childDelegates = this._buildChildDelegates(flowArgs, { currentItem: item, itemIndex: i });
        const childEngine = new FlowEngine(subFlowDef, childDelegates);
        const childResult = await childEngine.run();

        if (childResult.status === "completed") {
          successCount++;
        } else {
          failedCount++;
          this._log(`  subflow item ${itemLabel} failed: ${childResult.status}`);
        }
        summaries.push({ item, status: childResult.status, steps: childResult.stepCount });

        if (childResult.status !== "completed" && stepDef.onItemError?.stopOnError) break;
      }

      const marker = failedCount === 0 ? "all_complete" : (successCount > 0 ? "some_failed" : "all_failed");
      return {
        ok: true,
        marker,
        text: summaries.map(s => `${typeof s.item === "string" ? s.item : JSON.stringify(s.item)}: ${s.status}`).join("\n"),
        vars: { successCount: String(successCount), failedCount: String(failedCount) },
      };
    }

    this._log(`  subflow "${stepDef.flow}" single execution`);
    const childDelegates = this._buildChildDelegates(flowArgs, {});
    const childEngine = new FlowEngine(subFlowDef, childDelegates);
    const childResult = await childEngine.run();

    const marker = childResult.status === "completed" ? "complete" : "failed";
    return { ok: childResult.status === "completed", marker, text: childResult.status };
  }

  _resolveSubFlowDef(flowName) {
    if (typeof flowName === "object") return flowName;
    if (!this.delegates.subFlows) return null;
    return this.delegates.subFlows[flowName] || null;
  }

  _buildChildDelegates(flowArgs, itemContext) {
    const vars = { ...this.delegates.vars, ...flowArgs };
    if (itemContext.currentItem !== undefined) {
      vars.currentItem = itemContext.currentItem;
      vars.itemIndex = String(itemContext.itemIndex);
      if (typeof itemContext.currentItem === "string") {
        vars.currentItemName = itemContext.currentItem;
        vars.planFile = itemContext.currentItem;
      }
    }
    return {
      ...this.delegates,
      vars,
      logFile: this.delegates.logFile,
    };
  }

  async _resolveMarker(result, stepDef) {
    if (stepDef.type === "tool") {
      return result.ok ? "pass" : "fail";
    }
    if (stepDef.type === "script") {
      return result.marker;
    }
    if (stepDef.type === "subflow") {
      return result.marker;
    }
    if (!result.text) return null;

    const tag = extractTag(result.text, stepDef.resultTag);
    if (tag) return tag;

    if (this.delegates.runParseAgent) {
      const parsePrompt = this.delegates.buildParsePrompt
        ? this.delegates.buildParsePrompt(stepDef.resultTag, Object.keys(stepDef.transitions).join(", "), result.text)
        : `No <${stepDef.resultTag}> tag found. Valid values: ${Object.keys(stepDef.transitions).join(", ")}. Only output <${stepDef.resultTag}>value</${stepDef.resultTag}>.\n\nAI output:\n${result.text}`;
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
          result = await this._executeAgentStep(currentStep, stepDef, null);
        } else if (stepDef.type === "tool") {
          result = await this._executeToolStep(currentStep, stepDef);
        } else if (stepDef.type === "script") {
          result = await this._executeScriptStep(currentStep, stepDef);
        } else if (stepDef.type === "subflow") {
          result = await this._executeSubFlowStep(currentStep, stepDef);
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

      let marker = await this._resolveMarker(result, stepDef);
      if (marker) result.marker = marker;
      if (!marker) {
        this._log(`  marker not found in output`);
        const onUnknown = stepDef.onUnknown || { done: "failed" };
        if (onUnknown.done) return this._result(onUnknown.done, totalSteps);
        if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
        return this._result("failed", totalSteps);
      }

      this._log(`  marker: ${marker}`);

      let transition = stepDef.transitions[marker];

      if (!transition) {
        const aliased = this._tryAliasMarker(marker, stepDef.transitions);
        if (aliased) {
          this._log(`  marker alias: ${marker} → ${aliased}`);
          marker = aliased;
          transition = stepDef.transitions[marker];
        }
      }

      if (!transition) {
        const maxCorrect = stepDef.onUnknownMaxRetries ?? 2;
        const correctKey = `${currentStep}:marker-correct`;
        const correctCount = (this.markerCorrectionCounts.get(correctKey) || 0) + 1;
        this.markerCorrectionCounts.set(correctKey, correctCount);

        if (correctCount <= maxCorrect) {
          const validValues = Object.keys(stepDef.transitions).join(", ");
          const sessionId = this.lastSessionId;
          this._log(`  marker "${marker}" not in transitions, correction retry ${correctCount}/${maxCorrect} (session=${sessionId ? sessionId.slice(0, 20) + "..." : "none"})`);

          const correctionPrompt = this.delegates.buildCorrectionPrompt
            ? this.delegates.buildCorrectionPrompt(stepDef.resultTag, marker, validValues)
            : `The value "${marker}" in <${stepDef.resultTag}> is not valid. Valid values: ${validValues}. Only output <${stepDef.resultTag}>valid_value</${stepDef.resultTag}>.`;

          try {
            const corrected = await this._executeAgentStep(
              `${currentStep}:correct-${correctCount}`, { prompt: correctionPrompt }, sessionId,
            );
            if (corrected && corrected.text) {
              const newMarker = extractTag(corrected.text, stepDef.resultTag);
              if (newMarker) {
                const aliasedNew = this._tryAliasMarker(newMarker, stepDef.transitions);
                if (aliasedNew) {
                  this._log(`  corrected marker: ${newMarker} → ${aliasedNew}`);
                  marker = aliasedNew;
                  transition = stepDef.transitions[marker];
                }
              }
            }
          } catch (e) {
            this._log(`  correction retry failed: ${e.message}`);
          }
        }

        if (!transition) {
          this._log(`  no transition for marker "${marker}" after correction attempts`);
          const onUnknown = stepDef.onUnknown || { done: "no_transition" };
          if (onUnknown.done) return this._result(onUnknown.done, totalSteps);
          if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
          return this._result("no_transition", totalSteps);
        }
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
