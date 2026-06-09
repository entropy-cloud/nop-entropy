let _mockRoadmapCount = 0;
let _mockPlanAuditCount = 0;
let _mockClosureCount = 0;
let _mockDeepAuditCount = 0;
let _mockAdversarialCount = 0;

export function resetMockState() {
  _mockRoadmapCount = 0;
  _mockPlanAuditCount = 0;
  _mockClosureCount = 0;
  _mockDeepAuditCount = 0;
  _mockAdversarialCount = 0;
}

const STEP_KEY_MAP = {
  "FIX_TESTS": "fix-tests",
  "FIX_TESTS_RECOVERY": "fix-tests-recovery",
  "ROADMAP_CHECK": "roadmap-check",
  "PLAN_DRAFT": "plan-draft",
  "PLAN_AUDIT": "plan-audit",
  "EXECUTE_PLAN": "execute",
  "PLAN_CLOSURE": "closure-audit",
  "DEEP_AUDIT": "deep-audit",
  "ADVERSARIAL": "adversarial-review",
  "NEEDS_DEEP_AUDIT": "needs-deep-audit",
  "EXECUTE_PENDING_PLAN": "execute-pending",
  "VERIFY_PENDING_PLAN": "verify-pending",
  "AUDIT_PLAN_DRAFT": "audit-plan-draft",
  "AUDIT_PLAN_AUDIT": "audit-plan-audit",
  "AUDIT_EXECUTE": "audit-execute",
  "AUDIT_CLOSURE": "audit-closure",
};

function _normalizeStepName(stepName) {
  if (STEP_KEY_MAP[stepName]) return STEP_KEY_MAP[stepName];
  return stepName.toLowerCase().replace(/_/g, "-");
}

export function mockAgentResponse(stepName) {
  const n = _normalizeStepName(stepName);

  if (n === "fix-tests") return "<TEST_RESULT>no_errors</TEST_RESULT>";
  if (n === "fix-tests-recovery") return "<TEST_RESULT>fixed</TEST_RESULT>";

  if (n === "roadmap-check") {
    _mockRoadmapCount++;
    return _mockRoadmapCount <= 1
      ? "<ROADMAP_RESULT>pending</ROADMAP_RESULT>\n<ROADMAP_ITEMS><item priority=\"P1\">mock: unimplemented feature</item></ROADMAP_ITEMS>"
      : "<ROADMAP_RESULT>complete</ROADMAP_RESULT>";
  }

  if (n === "plan-draft" || n === "audit-plan-draft") return "<PLAN_RESULT>created</PLAN_RESULT>";

  if (n === "plan-audit" || n === "audit-plan-audit") {
    _mockPlanAuditCount++;
    return _mockPlanAuditCount <= 1
      ? "<AUDIT_RESULT>issues</AUDIT_RESULT>\n<ISSUES><item severity=\"Major\">mock: Exit Criteria not verifiable</item></ISSUES>"
      : "<AUDIT_RESULT>approved</AUDIT_RESULT>";
  }

  if (n === "execute" || n === "execute-pending" || n === "audit-execute") return "<EXECUTE_RESULT>success</EXECUTE_RESULT>";

  if (n === "verify-pending") return "<VERIFY_RESULT>complete</VERIFY_RESULT>";

  if (n === "closure-audit" || n === "audit-closure") {
    _mockClosureCount++;
    return _mockClosureCount === 1
      ? "<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>\n<REMAINING><item>mock: insufficient test coverage</item></REMAINING>"
      : "<CLOSURE_RESULT>complete</CLOSURE_RESULT>";
  }

  if (n === "needs-deep-audit") return "<DEEP_AUDIT_NEEDED>not_needed</DEEP_AUDIT_NEEDED>";

  if (n === "deep-audit") {
    _mockDeepAuditCount++;
    return _mockDeepAuditCount <= 1
      ? "<AUDIT_RESULT>issues</AUDIT_RESULT>"
      : "<AUDIT_RESULT>clean</AUDIT_RESULT>";
  }

  if (n === "adversarial-review") {
    _mockAdversarialCount++;
    return _mockAdversarialCount <= 1
      ? "<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>"
      : "<ADVERSARIAL_RESULT>clean</ADVERSARIAL_RESULT>";
  }

  return "##MOCK_OK";
}
