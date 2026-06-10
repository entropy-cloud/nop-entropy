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
    this.flowVars = new Map();
    this.visitCounts = new Map();
    this.retryCounts = new Map();
    this.appendBuffers = new Map();
    this.logEntries = [];
    this.startTime = null;
    this.lastSessionId = null;
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
    return str.replace(/\{\{(\w+)\}\}/g, (_, k) => {
      if (vars[k] === undefined) {
        this._log(`  WARNING: unresolved template variable {{${k}}}`);
        return `{{${k}}}`;
      }
      return vars[k];
    });
  }

  _buildPrompt(stepName, stepDef) {
    let prompt = stepDef.prompt || "";
    const allVars = { ...(this.delegates.vars || {}), ...Object.fromEntries(this.flowVars) };
    prompt = this._templateVar(prompt, allVars);

    const buf = this.appendBuffers.get(stepName);
    if (buf) {
      prompt += "\n" + buf;
    }
    return prompt;
  }

  _extractFlowVars(text) {
    const m = text.match(/<FLOW_VARS>([\s\S]*?)<\/FLOW_VARS>/);
    if (!m) return {};
    const vars = {};
    const re = /<(\w+)>([^<]*)<\/\1>/g;
    let match;
    while ((match = re.exec(m[1])) !== null) {
      vars[match[1]] = match[2].trim();
    }
    return vars;
  }

  _markerAliases() {
    return {};
  }

  _tryAliasMarker(marker, transitions) {
    if (transitions[marker]) return marker;
    const alias = this._markerAliases()[marker];
    if (alias && transitions[alias]) return alias;
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

    if (!result || !result.text) {
      return { marker: null, vars: {}, ok: !!result?.ok, text: result?.text || "" };
    }

    const vars = this._extractFlowVars(result.text);

    let marker = null;
    const rTag = stepDef.resultTag || "AI_STEP_RESULT";
    marker = extractTag(result.text, rTag);

    if (!marker && this.delegates.runParseAgent) {
      const parsePrompt = [
        `No <${rTag}> tag found in output. Read the AI output below and infer the result.`,
        `Expected values: ${Object.keys(stepDef.transitions || {}).join(", ")}`,
        `Output only <${rTag}>value</${rTag}> format, nothing else.`,
        ``,
        `AI output:`,
        result.text,
      ].join("\n");
      const retry = await this.delegates.runParseAgent(
        `parse-${rTag}`, parsePrompt, stepDef.system || "",
      );
      marker = extractTag(retry.text, rTag);
    }

    if (marker) {
      const aliased = this._tryAliasMarker(marker, stepDef.transitions || {});
      if (aliased) marker = aliased;
    }

    if (marker) {
      const transitions = stepDef.transitions || {};
      if (!transitions[marker]) {
        marker = await this._runCorrectionAgent(
          marker, result.text, rTag, transitions, stepDef, this.lastSessionId,
        );
      }
    }

    return { marker, vars, ok: result.ok, text: result.text };
  }

  async _runCorrectionAgent(marker, resultText, resultTag, transitions, stepDef, sessionId) {
    const maxRetries = stepDef.onUnknownMaxRetries ?? 2;
    let currentMarker = marker;

    for (let i = 0; i < maxRetries; i++) {
      const validValues = Object.keys(transitions).join(", ");
      this._log(`  marker "${currentMarker}" not in transitions, correction retry ${i + 1}/${maxRetries} (session=${sessionId ? sessionId.slice(0, 20) + "..." : "none"})`);

      const correctionPrompt = [
        `The value "${currentMarker}" in the <${resultTag}> tag from your last output is not valid.`,
        `Valid values are: ${validValues}`,
        `Output only <${resultTag}>valid_value</${resultTag}>, nothing else.`,
      ].join("\n");

      try {
        const corrected = await this.delegates.runAgent(
          `correct-${i + 1}`, correctionPrompt, stepDef.system || "", sessionId,
        );
        if (corrected && corrected.text) {
          const newMarker = extractTag(corrected.text, resultTag);
          if (newMarker) {
            const aliasedNew = this._tryAliasMarker(newMarker, transitions);
            if (aliasedNew) {
              this._log(`  corrected marker: ${newMarker} → ${aliasedNew}`);
              return aliasedNew;
            }
          }
        }
      } catch (e) {
        this._log(`  correction retry failed: ${e.message}`);
      }
    }

    return currentMarker;
  }

  async _executeToolStep(stepName, stepDef) {
    const command = this._templateVar(stepDef.command || "", this.delegates.vars || {});
    const timeout = stepDef.timeout || 0;
    const delegateResult = await this.delegates.runTool(stepName, command, { timeout });
    return {
      marker: delegateResult.ok ? "pass" : "fail",
      ok: true,
      vars: {},
      text: delegateResult.logFile || "",
    };
  }

  async _executeScriptStep(stepName, stepDef) {
    const ret = await stepDef.run(this.delegates, this.flowVars);
    if (ret && typeof ret === "object" && ret.marker !== undefined) {
      return {
        marker: ret.marker,
        ok: true,
        vars: ret.vars || {},
        text: ret.text || String(ret.marker),
      };
    }
    return { marker: ret, ok: true, vars: {}, text: String(ret) };
  }

  async _executeScriptStepWithOverride(stepName, stepDef) {
    if (this.delegates.runScript) {
      const result = await this.delegates.runScript(stepName, stepDef);
      if (result !== undefined) {
        if (typeof result === "string") {
          return { marker: result, ok: true, vars: {}, text: String(result) };
        }
        if (typeof result === "object") {
          return {
            marker: result.marker,
            ok: true,
            vars: result.vars || {},
            text: result.text || String(result.marker),
          };
        }
      }
    }
    return this._executeScriptStep(stepName, stepDef);
  }

  async _executeSubflowStep(stepName, stepDef) {
    const flowName = this._templateVar(stepDef.flow || "", this._allVars());
    const flowDef = await this.delegates.loadSubFlow(flowName);
    if (!flowDef) throw new Error(`Subflow not found: ${flowName}`);

    const baseArgs = {};
    if (stepDef.flowArgs) {
      const allVars = this._allVars();
      for (const [k, v] of Object.entries(stepDef.flowArgs)) {
        baseArgs[k] = this._templateVar(String(v), allVars);
      }
    }

    if (stepDef.forEach) {
      const listRaw = this._allVars()[stepDef.forEach];
      let items = [];
      if (Array.isArray(listRaw)) {
        items = listRaw;
      } else if (typeof listRaw === "string") {
        try { items = JSON.parse(listRaw); } catch { items = listRaw.split(",").map(s => s.trim()).filter(Boolean); }
      }
      if (items.length === 0) {
        this._log(`  subflow ${stepName}: forEach "${stepDef.forEach}" resolved to empty list → all_complete`);
        return { ok: true, marker: "all_complete", vars: {}, text: "all_complete" };
      }

      let completed = 0, failed = 0;
      const aggregatedVars = {};
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        this._log(`  subflow ${stepName}: forEach item ${i + 1}/${items.length}`);
        const childVars = { ...baseArgs, forEachItem: item, forEachIndex: i, forEachTotal: items.length };
        const { childResult, childFlowVars } = await this._runChildSubflow(flowDef, childVars);
        Object.assign(aggregatedVars, childFlowVars);
        this._log(`  subflow ${stepName}: forEach item ${i + 1} → ${childResult.status}`);
        if (childResult.status === "completed") {
          completed++;
        } else {
          failed++;
          if (stepDef.onItemError && stepDef.onItemError.stopOnError) break;
        }
      }

      let marker;
      if (failed === 0) marker = "all_complete";
      else if (completed === 0) marker = "all_failed";
      else marker = "some_failed";
      this._log(`  subflow ${stepName}: forEach done (${completed} completed, ${failed} failed) → ${marker}`);
      return { ok: true, marker, vars: aggregatedVars, text: marker };
    }

    const { childResult, childFlowVars } = await this._runChildSubflow(flowDef, baseArgs);
    const marker = childResult.status === "completed" ? "complete" : "failed";
    this._log(`  subflow ${stepName}: child ${childResult.status} → ${marker}`);
    return { ok: true, marker, vars: childFlowVars, text: marker };
  }

  _allVars() {
    return { ...(this.delegates.vars || {}), ...Object.fromEntries(this.flowVars) };
  }

  async _runChildSubflow(flowDef, extraVars) {
    const parentDelegates = this.delegates;
    const childVars = { ...(parentDelegates.vars || {}), ...extraVars };
    const childDelegates = {
      ...parentDelegates,
      vars: childVars,
      callLog: parentDelegates.callLog,
    };
    const childEngine = new FlowEngine(flowDef, childDelegates);
    const childResult = await childEngine.run();
    if (childResult.history) {
      for (const line of childResult.history) {
        this._log(`  [child] ${line}`);
      }
    }
    return {
      childResult,
      childFlowVars: Object.fromEntries(childEngine.flowVars),
    };
  }

  async _executeSubStep(stepName, stepDef) {
    if (stepDef.type === "agent") {
      return await this._executeAgentStep(stepName, stepDef, null);
    }
    if (stepDef.type === "tool") {
      return await this._executeToolStep(stepName, stepDef);
    }
    if (stepDef.type === "script") {
      return await this._executeScriptStepWithOverride(stepName, stepDef);
    }
    if (stepDef.type === "subflow") {
      return await this._executeSubflowStep(stepName, stepDef);
    }
    throw new Error(`Unknown sub-step type: ${stepDef.type}`);
  }

  async _executeGroupStep(groupName, groupDef) {
    const maxRounds = groupDef.maxRounds || 3;
    const onExhausted = groupDef.onExhausted || "fail";
    const subSteps = groupDef.steps;
    const firstStepName = Object.keys(subSteps)[0];
    const accumulatedVars = {};

    for (let round = 1; round <= maxRounds; round++) {
      this._log(`  group ${groupName} (round ${round}/${maxRounds})`);
      let currentSub = firstStepName;

      while (true) {
        const subDef = subSteps[currentSub];
        if (!subDef) {
          this._log(`  group ${groupName}: unknown sub-step ${currentSub}`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        let result;
        try {
          result = await this._executeSubStep(`${groupName}.${currentSub}`, subDef);
        } catch (err) {
          this._log(`  group ${groupName}.${currentSub} error: ${err.message}`);
          const onError = subDef.onError;
          if (onError && onError.exit) {
            return { ok: true, marker: onError.exit, vars: accumulatedVars, text: onError.exit };
          }
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (!result.ok) {
          this._log(`  group ${groupName}.${currentSub} subprocess failed`);
          const onError = subDef.onError;
          if (onError && onError.exit) {
            return { ok: true, marker: onError.exit, vars: accumulatedVars, text: result.text || onError.exit };
          }
          if (onError && onError.goto === "_retry") {
            break;
          }
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (result.vars) {
          for (const [k, v] of Object.entries(result.vars)) {
            this.flowVars.set(k, v);
          }
          Object.assign(accumulatedVars, result.vars);
        }

        let marker = result.marker;
        if (!marker) {
          this._log(`  group ${groupName}.${currentSub} marker not found`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (marker) {
          const aliased = this._tryAliasMarker(marker, subDef.transitions);
          if (aliased) marker = aliased;
        }

        this._log(`  group ${groupName}.${currentSub} → ${marker}`);

        const transition = subDef.transitions[marker];
        if (!transition) {
          this._log(`  group ${groupName}.${currentSub}: no transition for marker "${marker}"`);
          return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
        }

        if (transition.exit) {
          this._log(`  group ${groupName} exit: ${transition.exit}`);
          const exitText = result.text || transition.exit;
          return { ok: true, marker: transition.exit, vars: accumulatedVars, text: exitText };
        }

        if (transition.goto === "_retry") {
          break;
        }

        if (transition.goto && subSteps[transition.goto]) {
          currentSub = transition.goto;
          continue;
        }

        this._log(`  group ${groupName}.${currentSub}: invalid sub-transition ${JSON.stringify(transition)}`);
        return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
      }
    }

    this._log(`  group ${groupName} exhausted (${maxRounds} rounds) → ${onExhausted}`);
    return { ok: true, marker: onExhausted, vars: accumulatedVars, text: onExhausted };
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
          result = await this._executeScriptStepWithOverride(currentStep, stepDef);
        } else if (stepDef.type === "group") {
          result = await this._executeGroupStep(currentStep, stepDef);
        } else if (stepDef.type === "subflow") {
          result = await this._executeSubflowStep(currentStep, stepDef);
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

      if (result.vars) {
        for (const [k, v] of Object.entries(result.vars)) {
          this.flowVars.set(k, v);
        }
      }

      if (!result.ok) {
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

      let marker = result.marker;
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
        this._log(`  no transition for marker "${marker}"`);
        const onUnknown = stepDef.onUnknown || { done: "no_transition" };
        if (onUnknown.done) return this._result(onUnknown.done, totalSteps);
        if (onUnknown.goto) { currentStep = onUnknown.goto; continue; }
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
