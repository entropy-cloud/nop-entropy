package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityCheckpoint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single checkpoint decision test case (design {@code guardrail-contract.md}
 * §增量 4, 裁定 A). Immutable value object: declares a tool call + a static
 * security context + an expected {@link SecurityCheckpoint.Decision} (and
 * optionally the {@code matchedRule} of the checkpoint layer expected to
 * decide it).
 *
 * <p><b>No message history / {@code prior} field</b>: the 7-checkpoint chain
 * evaluates a single tool call against static security configuration; it does
 * not consume message history (design §增量 4 constraint 3). The
 * {@code write-intent-conflict} scenario is modeled declaratively via
 * {@link #prePopConflictPath} / {@link #prePopConflictSession} — the harness
 * seeds the {@code WriteIntentRegistry} with another session's intent before
 * building the consultation, rather than replaying a multi-turn history.
 *
 * <p>Use {@link #builder()} for the common case; the public constructor accepts
 * the full field set.
 */
public final class CheckpointTestCase {

    private final String id;
    private final String category;
    private final String toolName;
    private final Map<String, Object> args;
    private final ChannelKind channelKind;
    private final Principal principal;
    private final String workDir;
    private final String sessionId;
    private final SecurityCheckpoint.Decision expectedDecision;
    private final String expectedMatchedRule;
    private final String description;

    // Write-intent-conflict seeding (裁定 C). When prePopConflictPath is non-null,
    // the harness seeds the registry with another session's WriteIntent on the
    // normalized form of this path before building the consultation.
    private final String prePopConflictPath;
    private final String prePopConflictSession;

    public CheckpointTestCase(String id, String category, String toolName, Map<String, Object> args,
                              ChannelKind channelKind, Principal principal, String workDir, String sessionId,
                              SecurityCheckpoint.Decision expectedDecision, String expectedMatchedRule,
                              String description, String prePopConflictPath, String prePopConflictSession) {
        this.id = id;
        this.category = category;
        this.toolName = toolName;
        this.args = args == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(args));
        this.channelKind = channelKind;
        this.principal = principal;
        this.workDir = workDir;
        this.sessionId = sessionId;
        this.expectedDecision = expectedDecision;
        this.expectedMatchedRule = expectedMatchedRule;
        this.description = description;
        this.prePopConflictPath = prePopConflictPath;
        this.prePopConflictSession = prePopConflictSession;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public ChannelKind getChannelKind() {
        return channelKind;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public String getWorkDir() {
        return workDir;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SecurityCheckpoint.Decision getExpectedDecision() {
        return expectedDecision;
    }

    public String getExpectedMatchedRule() {
        return expectedMatchedRule;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @return the raw path to seed into the {@code WriteIntentRegistry} as
     *         another session's intent before running the case, or {@code null}
     *         when no conflict seeding is needed
     */
    public String getPrePopConflictPath() {
        return prePopConflictPath;
    }

    /**
     * @return the "other" session id for the seeded intent (defaults to
     *         {@code "other-session"} at harness time when null)
     */
    public String getPrePopConflictSession() {
        return prePopConflictSession;
    }

    public boolean hasConflictSeed() {
        return prePopConflictPath != null && !prePopConflictPath.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointTestCase that = (CheckpointTestCase) o;
        return Objects.equals(id, that.id)
                && Objects.equals(category, that.category)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(args, that.args)
                && channelKind == that.channelKind
                && Objects.equals(principal, that.principal)
                && Objects.equals(workDir, that.workDir)
                && Objects.equals(sessionId, that.sessionId)
                && expectedDecision == that.expectedDecision
                && Objects.equals(expectedMatchedRule, that.expectedMatchedRule)
                && Objects.equals(description, that.description)
                && Objects.equals(prePopConflictPath, that.prePopConflictPath)
                && Objects.equals(prePopConflictSession, that.prePopConflictSession);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, category, toolName, args, channelKind, principal, workDir, sessionId,
                expectedDecision, expectedMatchedRule, description, prePopConflictPath, prePopConflictSession);
    }

    @Override
    public String toString() {
        return "CheckpointTestCase{id='" + id + "', category='" + category + "', tool='" + toolName
                + "', expected=" + expectedDecision
                + (expectedMatchedRule != null ? ", rule='" + expectedMatchedRule + "'" : "")
                + (hasConflictSeed() ? ", conflictSeed='" + prePopConflictPath + "'" : "") + "}";
    }

    /**
     * Fluent builder. Required fields: {@code id}, {@code category},
     * {@code toolName}, {@code expectedDecision}, {@code sessionId}.
     */
    public static final class Builder {
        private String id;
        private String category;
        private String toolName;
        private Map<String, Object> args;
        private ChannelKind channelKind;
        private Principal principal;
        private String workDir;
        private String sessionId;
        private SecurityCheckpoint.Decision expectedDecision;
        private String expectedMatchedRule;
        private String description;
        private String prePopConflictPath;
        private String prePopConflictSession;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder args(Map<String, Object> args) {
            this.args = args;
            return this;
        }

        public Builder arg(String key, Object value) {
            Map<String, Object> m = this.args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(this.args);
            m.put(key, value);
            this.args = m;
            return this;
        }

        public Builder channelKind(ChannelKind channelKind) {
            this.channelKind = channelKind;
            return this;
        }

        public Builder principal(Principal principal) {
            this.principal = principal;
            return this;
        }

        public Builder workDir(String workDir) {
            this.workDir = workDir;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder expectedDecision(SecurityCheckpoint.Decision expectedDecision) {
            this.expectedDecision = expectedDecision;
            return this;
        }

        public Builder expectedMatchedRule(String expectedMatchedRule) {
            this.expectedMatchedRule = expectedMatchedRule;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Seed the {@code WriteIntentRegistry} with another session's intent on
         * the given raw path before running the case (write-intent-conflict
         * scenario).
         */
        public Builder prePopConflict(String path, String otherSessionId) {
            this.prePopConflictPath = path;
            this.prePopConflictSession = otherSessionId;
            return this;
        }

        public CheckpointTestCase build() {
            return new CheckpointTestCase(id, category, toolName, args, channelKind, principal,
                    workDir, sessionId, expectedDecision, expectedMatchedRule, description,
                    prePopConflictPath, prePopConflictSession);
        }
    }
}
