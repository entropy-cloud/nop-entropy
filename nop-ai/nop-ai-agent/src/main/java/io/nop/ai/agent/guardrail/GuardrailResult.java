package io.nop.ai.agent.guardrail;

import java.util.Objects;

public abstract class GuardrailResult {

    GuardrailResult() {
    }

    public boolean isPass() {
        return this instanceof PassResult;
    }

    public boolean isBlock() {
        return this instanceof BlockResult;
    }

    public boolean isModify() {
        return this instanceof ModifyResult;
    }

    public static final class PassResult extends GuardrailResult {
        private static final PassResult INSTANCE = new PassResult();

        private PassResult() {
        }

        public static PassResult instance() {
            return INSTANCE;
        }
    }

    public static final class BlockResult extends GuardrailResult {
        private final String reason;

        public BlockResult(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockResult that = (BlockResult) o;
            return Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason);
        }

        @Override
        public String toString() {
            return "BlockResult{reason='" + reason + "'}";
        }
    }

    public static final class ModifyResult extends GuardrailResult {
        private final String content;

        public ModifyResult(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModifyResult that = (ModifyResult) o;
            return Objects.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(content);
        }

        @Override
        public String toString() {
            return "ModifyResult{content='" + content + "'}";
        }
    }
}
