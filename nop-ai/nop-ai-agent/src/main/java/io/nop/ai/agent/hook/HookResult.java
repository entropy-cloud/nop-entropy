package io.nop.ai.agent.hook;

public abstract class HookResult {

    HookResult() {
    }

    public boolean isPass() {
        return this instanceof PassResult;
    }

    public boolean isVeto() {
        return this instanceof VetoResult;
    }

    public boolean isReenter() {
        return this instanceof ReenterResult;
    }

    /**
     * W5-3: whether this is a {@link BailResult} (hard-block return state).
     * Returns {@code false} for the other three states (Pass/Veto/Reenter),
     * preserving backward compatibility.
     */
    public boolean isBail() {
        return this instanceof BailResult;
    }

    public static final class PassResult extends HookResult {
        private static final PassResult INSTANCE = new PassResult();

        private PassResult() {
        }

        public static PassResult instance() {
            return INSTANCE;
        }
    }

    public static final class VetoResult extends HookResult {
        private final String reason;

        public VetoResult(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static final class ReenterResult extends HookResult {
        private final String message;

        public ReenterResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * W5-3: hard-block return state. Semantics: abort and do NOT apply this
     * response — the agent loop skips this round's response (POST_REASONING:
     * tool_calls not executed + not treated as final answer, then re-prompt)
     * or marks the final result as guardrail-blocked (POST_CALL). Only valid
     * at {@code POST_REASONING} / {@code POST_CALL}; returning it at any other
     * lifecycle point fails loud (throws {@code NopAiAgentException}).
     *
     * <p>Distinct from {@link VetoResult} (PRE-side rejection, core not
     * executed — POST-side core has already executed, Veto semantics do not
     * apply) and from "rewrite" interception ({@code GuardrailResult.ModifyResult}
     * rewrites content and passes through — BAIL does not apply the response).
     *
     * <p>Nested inside {@code HookResult} because the abstract base's
     * package-private constructor confines concrete subclasses to this file.
     */
    public static final class BailResult extends HookResult {
        private final String reason;

        public BailResult(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
