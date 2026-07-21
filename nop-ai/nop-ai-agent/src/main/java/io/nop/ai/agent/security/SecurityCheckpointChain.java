package io.nop.ai.agent.security;

import java.util.ArrayList;
import java.util.List;

public class SecurityCheckpointChain {
    private final List<SecurityCheckpoint> checkpoints;

    private SecurityCheckpointChain(List<SecurityCheckpoint> checkpoints) {
        this.checkpoints = checkpoints;
    }

    public SecurityCheckpoint.Decision evaluate(SecurityCheckpoint.CheckContext ctx) {
        for (SecurityCheckpoint checkpoint : checkpoints) {
            SecurityCheckpoint.Decision decision = checkpoint.check(ctx);
            if (decision != SecurityCheckpoint.Decision.ALLOW) {
                return decision;
            }
        }
        return SecurityCheckpoint.Decision.ALLOW;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<SecurityCheckpoint> checkpoints = new ArrayList<>();

        public Builder add(SecurityCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
            return this;
        }

        public SecurityCheckpointChain build() {
            if (checkpoints.isEmpty()) {
                throw new IllegalStateException("SecurityCheckpointChain must have at least one checkpoint");
            }
            return new SecurityCheckpointChain(List.copyOf(checkpoints));
        }
    }
}
