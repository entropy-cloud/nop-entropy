package io.nop.ai.agent.completion;

public abstract class CompletionDecision {

    CompletionDecision() {
    }

    public boolean isComplete() {
        return this instanceof Complete;
    }

    public boolean isContinue() {
        return this instanceof Continue;
    }

    public boolean isEscalate() {
        return this instanceof Escalate;
    }

    public static final class Continue extends CompletionDecision {
        private final String message;

        public Continue(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class Complete extends CompletionDecision {
        private static final Complete INSTANCE = new Complete();

        private Complete() {
        }

        public static Complete instance() {
            return INSTANCE;
        }
    }

    public static final class Escalate extends CompletionDecision {
        private final String reason;

        public Escalate(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
