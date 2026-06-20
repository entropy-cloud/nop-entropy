package io.nop.ai.agent.team;

import java.util.List;
import java.util.Optional;

/**
 * Shipped no-op default for {@link ITeamTaskStore}.
 *
 * <p>The {@link #createTask} write operation and the four state-transition
 * operations ({@link #claimTask} / {@link #completeTask} / {@link #abandonTask}
 * / {@link #reclaimTask}) throw {@link UnsupportedOperationException} — a
 * fast failure that signals "team task store is not enabled", not a silent
 * success. This honours Minimum Rules #24 (No Silent No-Op): a caller that
 * depends on shared task mutation would otherwise mistake a silent
 * null/empty for a real effect.
 *
 * <p>All <strong>read</strong> operations ({@link #getTask},
 * {@link #getTasksByTeam}, {@link #getTasksByCreator}) return empty results,
 * consistent with the "no tasks exist" semantics.
 *
 * <p>The engine uses this default out-of-the-box (via
 * {@code DefaultAgentEngine.teamTaskStore}), so integrators see zero
 * behaviour regression unless they explicitly wire a functional store
 * (e.g. {@link InMemoryTeamTaskStore}) via
 * {@code DefaultAgentEngine.setTeamTaskStore(...)}.
 *
 * <p>See plan 225 (L4-8-team-tools) and Minimum Rules #24.
 */
public final class NoOpTeamTaskStore implements ITeamTaskStore {

    private static final NoOpTeamTaskStore INSTANCE = new NoOpTeamTaskStore();

    private NoOpTeamTaskStore() {
    }

    public static NoOpTeamTaskStore noOp() {
        return INSTANCE;
    }

    private static UnsupportedOperationException notEnabled() {
        return new UnsupportedOperationException(
                "NoOpTeamTaskStore: team task store is not enabled "
                        + "(wire InMemoryTeamTaskStore via DefaultAgentEngine.setTeamTaskStore to enable)");
    }

    @Override
    public TeamTask createTask(String teamId, String subject, String description,
                               List<String> blockedBy, String createdBy) {
        throw notEnabled();
    }

    @Override
    public Optional<TeamTask> getTask(String taskId) {
        return Optional.empty();
    }

    @Override
    public List<TeamTask> getTasksByTeam(String teamId) {
        return List.of();
    }

    @Override
    public List<TeamTask> getTasksByCreator(String createdBy) {
        return List.of();
    }

    @Override
    public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
        throw notEnabled();
    }

    @Override
    public Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
        throw notEnabled();
    }

    @Override
    public Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
        throw notEnabled();
    }

    @Override
    public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
        throw notEnabled();
    }
}
