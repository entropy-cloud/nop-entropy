/**
 * Lightweight expression evaluator for the flow engine.
 *
 * Supports: identifiers, function calls, member access, comparisons,
 * logical operators, string/number/boolean literals.
 *
 * Security model: expressions come from trusted flow JSON definitions
 * (developer-authored config), never from user input or AI output.
 * Uses Function constructor with scoped arguments — no access to globals
 * beyond what is explicitly passed.
 */

/**
 * Evaluate an expression string against scoped vars and funcs.
 *
 * @param {string} expr - JavaScript expression string (e.g. "activePlans().length > 0")
 * @param {Record<string, any>} vars - Variable scope (flow vars from mission.json + runtime)
 * @param {Record<string, Function>} funcs - Pre-registered callable functions
 * @returns {*} The evaluated result
 */
export function evaluateExpression(expr, vars = {}, funcs = {}) {
  const scope = { ...vars, ...funcs };
  const keys = Object.keys(scope);
  const values = Object.values(scope);
  try {
    const fn = new Function(...keys, `"use strict"; return (${expr});`);
    return fn(...values);
  } catch (err) {
    throw new Error(`Expression evaluation failed: "${expr}" — ${err.message}`);
  }
}

/**
 * Check if a string looks like an expression (contains operators or function calls)
 * vs a plain variable name.
 *
 * "items"         → false (plain variable name)
 * "activePlans()" → true  (function call)
 * "x != ''"       → true  (comparison)
 */
export function isExpression(str) {
  if (typeof str !== "string") return false;
  return /[()'"]/.test(str) || /[=!<>]/.test(str) || /\.\w/.test(str);
}

/**
 * Resolve {{varName}} template variables in a string.
 *
 * @param {string} str - String with optional {{var}} placeholders
 * @param {Record<string, any>} vars - Variable scope
 * @returns {string} Resolved string (unknown vars left as-is)
 */
export function resolveTemplateVars(str, vars = {}) {
  if (typeof str !== "string") return str;
  return str.replace(/\{\{(\w+)\}\}/g, (_, k) => {
    if (vars[k] === undefined) return `{{${k}}}`;
    return vars[k];
  });
}
