package io.nop.ai.agent.model;

import io.nop.ai.agent.model._gen._PathRuleModel;
import io.nop.ai.agent.security.PathAccessDecision;

public class PathRuleModel extends _PathRuleModel {
    public PathRuleModel() {

    }

    /**
     * Resolve the declared {@code access} string ("allow" / "deny") into a
     * {@link PathAccessDecision} enum. Defaults to {@link PathAccessDecision#DENY}
     * when the access value is null, blank, or unrecognized (fail-closed).
     */
    public PathAccessDecision getAccessDecision() {
        return PathAccessDecision.fromString(getAccess());
    }
}
