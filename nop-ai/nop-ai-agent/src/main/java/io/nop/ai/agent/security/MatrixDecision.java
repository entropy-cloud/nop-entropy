package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * The allow/deny decision returned by {@link IPermissionMatrix#check}. Follows
 * the same deny-with-reason pattern as {@link ToolAccessResult} and
 * {@link PathAccessResult}: a denial carries an auditable {@code reason}
 * identifying the channel/level restriction that triggered it, plus the
 * structured {@code channel} and {@code level} context for audit categorization.
 */
public final class MatrixDecision {

    private final boolean allowed;
    private final String reason;
    private final ChannelKind channel;
    private final SecurityLevel level;

    private MatrixDecision(boolean allowed, String reason, ChannelKind channel, SecurityLevel level) {
        this.allowed = allowed;
        this.reason = reason;
        this.channel = channel;
        this.level = level;
    }

    /**
     * An allow decision — no restrictions apply.
     */
    public static MatrixDecision allow() {
        return new MatrixDecision(true, null, null, null);
    }

    /**
     * A simple denial carrying only a human-readable reason.
     */
    public static MatrixDecision deny(String reason) {
        return new MatrixDecision(false, reason, null, null);
    }

    /**
     * A denial carrying an auditable reason together with the structured
     * channel and level context that triggered the restriction. Used by
     * restrictive matrix implementations to categorize denials for audit
     * logging.
     */
    public static MatrixDecision deny(ChannelKind channel, SecurityLevel level, String reason) {
        return new MatrixDecision(false, reason, channel, level);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isDenied() {
        return !allowed;
    }

    public String getReason() {
        return reason;
    }

    public ChannelKind getChannel() {
        return channel;
    }

    public SecurityLevel getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatrixDecision that = (MatrixDecision) o;
        return allowed == that.allowed
                && Objects.equals(reason, that.reason)
                && channel == that.channel
                && level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason, channel, level);
    }

    @Override
    public String toString() {
        return "MatrixDecision{" +
                "allowed=" + allowed +
                ", reason='" + reason + '\'' +
                ", channel=" + channel +
                ", level=" + level +
                '}';
    }
}
