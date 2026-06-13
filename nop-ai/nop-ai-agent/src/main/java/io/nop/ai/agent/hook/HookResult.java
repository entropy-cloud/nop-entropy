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
}
