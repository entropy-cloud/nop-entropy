export const STEP_NAMES = {
  DETECT_START: "detect-start",
  HEALTH_CHECK: "health-check",
  FIX_BUILD: "fix-build",
  ROADMAP_CHECK: "roadmap-check",
  PLAN_DRAFT: "plan-draft",
  PLAN_AUDIT: "plan-audit",
  EXECUTE: "execute",
  CLOSURE_AUDIT: "closure-audit",
  BUILD_VERIFY: "build-verify",
  DEEP_AUDIT: "deep-audit",
  ADVERSARIAL: "adversarial-review",
};

export function createTestStepConfigs() {
  const make = (tag, value) => ({
    command: `output <${tag}>${value}</${tag}>`,
    system: "",
    resultTag: tag,
    markerValues: { DEFAULT: value },
  });

  return {
    [STEP_NAMES.DETECT_START]:  { ...make("START_PHASE", "audit"), markerValues: { roadmap: "roadmap", plan: "plan", execute: "execute", audit: "audit" } },
    [STEP_NAMES.HEALTH_CHECK]:  { command: "echo ok", system: "", type: "tool" },
    [STEP_NAMES.FIX_BUILD]:     { ...make("HEALTH_STATUS", "fixed"), markerValues: { fixed: "fixed", failed: "failed" } },
    [STEP_NAMES.ROADMAP_CHECK]: { ...make("ROADMAP_RESULT", "complete"), markerValues: { pending: "pending", complete: "complete" } },
    [STEP_NAMES.PLAN_DRAFT]:    { ...make("PLAN_RESULT", "created"), markerValues: { created: "created", none: "none" } },
    [STEP_NAMES.PLAN_AUDIT]:    { ...make("AUDIT_RESULT", "approved"), markerValues: { approved: "approved", issues: "issues" } },
    [STEP_NAMES.EXECUTE]:       { ...make("EXECUTE_RESULT", "success"), markerValues: { success: "success", failed: "failed" } },
    [STEP_NAMES.CLOSURE_AUDIT]: { ...make("CLOSURE_RESULT", "complete"), markerValues: { complete: "complete", incomplete: "incomplete" } },
    [STEP_NAMES.BUILD_VERIFY]:  { command: "echo ok", system: "", type: "tool" },
    [STEP_NAMES.DEEP_AUDIT]:    { ...make("AUDIT_RESULT", "clean"), markerValues: { clean: "clean", issues: "issues" } },
    [STEP_NAMES.ADVERSARIAL]:   { ...make("ADVERSARIAL_RESULT", "clean"), markerValues: { clean: "clean", issues: "issues" } },
  };
}
