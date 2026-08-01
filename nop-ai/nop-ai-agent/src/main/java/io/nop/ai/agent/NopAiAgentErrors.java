package io.nop.ai.agent;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the nop-ai-agent module.
 *
 * <p>Created by plan 2026-07-31-2248-2 (scan-hollow baseline clearance):
 * interface default methods and NoOp/pass-through classes that historically
 * failed fast with {@code UnsupportedOperationException} now fail fast with
 * {@link io.nop.ai.agent.engine.NopAiAgentException} carrying these codes.
 * All descriptions are English (AGENTS.md error-message convention; the
 * historical UOE messages were English, so {@code getMessage()} semantics are
 * unchanged).
 */
public interface NopAiAgentErrors {

    String ARG_MODE = "mode";
    String ARG_MSG = "msg";
    String ARG_FILTER_ID = "filterId";
    String ARG_POINT = "point";
    String ARG_IMPL = "impl";
    String ARG_RECIPE_REF = "recipeRef";
    String ARG_PARAM_NAME = "paramName";
    String ARG_HOOK_ID = "hookId";

    // ========================================================================
    // P3-MA3-1: bare IllegalArgumentException validation guards (plan
    // 2026-08-01-0936-1). All 62 historical IAE throw sites converted to
    // NopAiAgentException carrying this code; the verbatim English message is
    // carried via {msg} so getMessage() semantics are unchanged.
    // ========================================================================

    ErrorCode ERR_AI_AGENT_INVALID_ARG =
            define("nop.err.ai.agent.invalid-arg", "invalid argument: {msg}", ARG_MSG);

    // ========================================================================
    // IAgentEngine default methods (Phase 2 capabilities, fail-fast)
    // ========================================================================

    ErrorCode ERR_AGENT_FORK_SESSION_NOT_SUPPORTED =
            define("nop.err.ai.agent.fork-session-not-supported", "forkSession requires Phase 2 ISessionStore");

    ErrorCode ERR_AGENT_GET_SESSION_STATUS_NOT_SUPPORTED =
            define("nop.err.ai.agent.get-session-status-not-supported", "getSessionStatus requires Phase 2");

    ErrorCode ERR_AGENT_CANCEL_SESSION_NOT_SUPPORTED =
            define("nop.err.ai.agent.cancel-session-not-supported", "cancelSession requires Phase 2");

    ErrorCode ERR_AGENT_RESUME_SESSION_NOT_SUPPORTED =
            define("nop.err.ai.agent.resume-session-not-supported",
                    "resumeSession requires a registered denial ledger and a paused session");

    ErrorCode ERR_AGENT_RESTORE_SESSION_NOT_SUPPORTED =
            define("nop.err.ai.agent.restore-session-not-supported",
                    "restoreSession requires a FileBackedSessionStore-backed engine");

    ErrorCode ERR_AGENT_RESTORE_PENDING_SESSIONS_NOT_SUPPORTED =
            define("nop.err.ai.agent.restore-pending-sessions-not-supported",
                    "restorePendingSessions requires a DefaultAgentEngine with a discovery-capable session store");

    // ========================================================================
    // IAiMemoryStore default methods (Phase 2 capabilities, fail-fast)
    // ========================================================================

    ErrorCode ERR_AI_MEMORY_READ_BUDGETED_NOT_SUPPORTED =
            define("nop.err.ai.agent.memory-read-budgeted-not-supported", "readBudgeted requires Phase 2");

    ErrorCode ERR_AI_MEMORY_UPDATE_NOT_SUPPORTED =
            define("nop.err.ai.agent.memory-update-not-supported", "update requires Phase 2");

    ErrorCode ERR_AI_MEMORY_REMOVE_NOT_SUPPORTED =
            define("nop.err.ai.agent.memory-remove-not-supported", "remove requires Phase 2");

    ErrorCode ERR_AI_MEMORY_BATCH_ADD_NOT_SUPPORTED =
            define("nop.err.ai.agent.memory-batch-add-not-supported", "batchAdd requires Phase 2");

    // ========================================================================
    // ISessionStore default methods (capability fail-fast)
    // ========================================================================

    ErrorCode ERR_AGENT_SESSION_LIST_ALL_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-list-all-not-supported",
                    "listAllSessions requires a session store that supports discovery (e.g. FileBackedSessionStore or InMemorySessionStore)");

    ErrorCode ERR_AGENT_SESSION_SAVE_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-save-not-supported",
                    "save requires a persistent session store (e.g. FileBackedSessionStore)");

    ErrorCode ERR_AGENT_SESSION_FORK_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-fork-not-supported", "forkSession requires VfsSessionStore");

    ErrorCode ERR_AGENT_SESSION_FORK_WITH_FILTER_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-fork-with-filter-not-supported",
                    "forkSession with message filter requires store support (FileBackedSessionStore/InMemorySessionStore/DBSessionStore)");

    ErrorCode ERR_AGENT_SESSION_APPEND_EVENT_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-append-event-not-supported", "appendEvent requires VfsSessionStore");

    ErrorCode ERR_AGENT_SESSION_COMPACT_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-compact-not-supported", "compact requires VfsSessionStore");

    ErrorCode ERR_AGENT_SESSION_LOAD_SNAPSHOT_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-load-snapshot-not-supported", "loadSnapshot requires VfsSessionStore");

    ErrorCode ERR_AGENT_SESSION_SET_PLAN_REF_NOT_SUPPORTED =
            define("nop.err.ai.agent.session-set-plan-ref-not-supported", "setPlanRef requires VfsSessionStore");

    // ========================================================================
    // NoOpHookRegistry registration rejections (fail-fast)
    // ========================================================================

    ErrorCode ERR_AGENT_HOOK_REGISTRY_REGISTER_NOT_SUPPORTED =
            define("nop.err.ai.agent.hook-registry-register-not-supported",
                    "NoOpHookRegistry does not support hook registration");

    ErrorCode ERR_AGENT_HOOK_REGISTRY_MIDDLEWARE_NOT_SUPPORTED =
            define("nop.err.ai.agent.hook-registry-middleware-not-supported",
                    "NoOpHookRegistry does not support middleware registration");

    // ========================================================================
    // DefaultAgentEngine plan-mode dispatch (fail-fast)
    // ========================================================================

    ErrorCode ERR_AGENT_PLAN_MODE_NOT_IMPLEMENTED =
            define("nop.err.ai.agent.plan-mode-not-implemented",
                    "Plan execution mode is not yet implemented: mode={mode}", ARG_MODE);

    // ========================================================================
    // W3-2: declarative filter chain assembly (fail-fast, no silent skip)
    // ========================================================================

    ErrorCode ERR_AGENT_FILTER_UNKNOWN_REF =
            define("nop.err.ai.agent.filter-unknown-ref",
                    "filter-chain references unknown filter id '{filterId}': "
                            + "no <filter-def> with this id exists in <filter-definitions>",
                    ARG_FILTER_ID);

    ErrorCode ERR_AGENT_FILTER_DEF_MISSING_IMPL =
            define("nop.err.ai.agent.filter-def-missing-impl",
                    "filter-def '{filterId}' has no impl class declared", ARG_FILTER_ID);

    ErrorCode ERR_AGENT_FILTER_DEF_NOT_MIDDLEWARE =
            define("nop.err.ai.agent.filter-def-not-middleware",
                    "filter-def '{filterId}' impl '{impl}' does not implement IAgentMiddleware",
                    ARG_FILTER_ID, ARG_IMPL);

    ErrorCode ERR_AGENT_FILTER_DUPLICATE_DECLARATION =
            define("nop.err.ai.agent.filter-duplicate-declaration",
                    "filter '{filterId}' is declared both in <filter-chain> and in <middlewares> "
                            + "for lifecycle point '{point}': remove one declaration",
                    ARG_FILTER_ID, ARG_POINT);

    ErrorCode ERR_AGENT_FILTER_UNKNOWN_POINT =
            define("nop.err.ai.agent.filter-unknown-point",
                    "filter '{filterId}' references unknown lifecycle point '{point}'",
                    ARG_FILTER_ID, ARG_POINT);

    // ========================================================================
    // W6-1: recipe composition layer (fail-fast, no silent skip)
    // ========================================================================

    ErrorCode ERR_AGENT_RECIPE_NOT_FOUND =
            define("nop.err.ai.agent.recipe-not-found",
                    "recipe '{recipeRef}' referenced in <recipes> could not be loaded "
                            + "(no *.recipe.xml with this name exists in VFS)",
                    ARG_RECIPE_REF);

    ErrorCode ERR_AGENT_RECIPE_INVALID_REF =
            define("nop.err.ai.agent.recipe-invalid-ref",
                    "recipe ref '{recipeRef}' is not a valid identifier "
                            + "(only [A-Za-z0-9_-] are allowed)",
                    ARG_RECIPE_REF);

    ErrorCode ERR_AGENT_RECIPE_MISSING_PARAM =
            define("nop.err.ai.agent.recipe-missing-param",
                    "recipe '{recipeRef}' prompt-template references parameter '{paramName}' "
                            + "but no <param> with this name was declared in the <recipe> ref",
                    ARG_RECIPE_REF, ARG_PARAM_NAME);

    ErrorCode ERR_AGENT_RECIPE_DUPLICATE_HOOK_ID =
            define("nop.err.ai.agent.recipe-duplicate-hook-id",
                    "hook id '{hookId}' is declared more than once across recipes and the agent "
                            + "(recipe hooks are merged as a union; duplicate ids are rejected)",
                    ARG_HOOK_ID);
}
