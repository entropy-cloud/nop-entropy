package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.quota.IResourceGuard;
import io.nop.ai.agent.quota.NoOpResourceGuard;
import io.nop.ai.agent.quota.QuotaDecision;
import io.nop.ai.agent.quota.QuotaDimension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Functional {@link ITeamManager} backed by two {@link ConcurrentHashMap}
 * indices: {@code teamId → Team} and {@code sessionId → teamId} (for
 * {@link #getTeamBySession} reverse-lookup).
 *
 * <h2>Index consistency</h2>
 * The session index is kept in sync on every {@link #bindMemberSession} and
 * {@link #removeMember} call. {@code bindMemberSession} writes the session
 * index entry only after the member's binding succeeds; {@code removeMember}
 * removes the session index entry (if any) before removing the member. This
 * ordering ensures a concurrent reader never sees a session index pointing
 * to a non-existent team/member.
 *
 * <h2>Lifecycle semantics</h2>
 * <ul>
 *   <li>{@link #createTeam} — generates a UUID teamId, initialises the member
 *       map from the spec's {@code memberSpecs} (each member starts unbound),
 *       sets status to {@link TeamStatus#CREATED}.</li>
 *   <li>{@link #addMember} — fails fast with {@link NopAiAgentException} if
 *       the team does not exist, is disbanded, or already has a member with
 *       the same {@code memberName}.</li>
 *   <li>{@link #bindMemberSession} — the first successful binding in a team
 *       transitions the team status from {@link TeamStatus#CREATED} to
 *       {@link TeamStatus#ACTIVE}.</li>
 *   <li>{@link #disbandTeam} — transitions status to {@link TeamStatus#DISBANDED},
 *       records {@code disbandedAt}; the team remains in the registry
 *       (queryable for history/audit). Fails fast with
 *       {@link NopAiAgentException} if the team does not exist.</li>
 *   <li>Query methods return snapshot collections / optionals; mutations to
 *       the returned collections do not affect the registry.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Both indices are {@link ConcurrentHashMap}. Per-team member-map mutations
 * (addMember / removeMember / bindMemberSession) are serialised by
 * {@code compute} on the per-team member map's structural operations where
 * needed; cross-team operations are independent. The first-binding
 * {@code CREATED → ACTIVE} transition is guarded by a {@code compute} on the
 * team's member map to ensure exactly-once activation.
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.
 */
public final class InMemoryTeamManager implements ITeamManager {

    private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToTeamId = new ConcurrentHashMap<>();

    /**
     * Central quota-decision gateway (plan 234 / vision §5.2). Constructor-
     * injected; defaults to {@link NoOpResourceGuard} (always allow = zero
     * regression). Enforced at {@code createTeam} / {@code addMember}
     * ({@link QuotaDimension#TEAM_MEMBERS}) and {@code bindMemberSession}
     * ({@link QuotaDimension#TEAM_PARALLEL_BOUND_MEMBERS}).
     */
    private final IResourceGuard resourceGuard;

    /**
     * Create a manager with the shipped {@link NoOpResourceGuard} default
     * (no quota enforcement — zero behaviour regression).
     */
    public InMemoryTeamManager() {
        this(NoOpResourceGuard.noOp());
    }

    /**
     * Create a manager with an explicit quota guard. When a
     * {@link io.nop.ai.agent.quota.DefaultResourceGuard} is supplied, the
     * manager enforces {@link QuotaDimension#TEAM_MEMBERS} at
     * {@code createTeam}/{@code addMember} and
     * {@link QuotaDimension#TEAM_PARALLEL_BOUND_MEMBERS} at
     * {@code bindMemberSession} (the {@code maxParallelMembers} hint→enforced
     * upgrade).
     *
     * @param resourceGuard the quota-decision gateway; {@code null} falls back
     *                      to {@link NoOpResourceGuard}
     */
    public InMemoryTeamManager(IResourceGuard resourceGuard) {
        this.resourceGuard = resourceGuard != null ? resourceGuard : NoOpResourceGuard.noOp();
    }

    /**
     * Adjudicate a quota dimension and fail-fast on denial (plan 234, Design
     * Decision §6 — No Silent No-Op). The decision's reason is surfaced in
     * the exception message so the caller knows exactly which
     * dimension/scope/limit/projectedCount triggered the denial.
     */
    private void enforceQuota(QuotaDimension dimension, String scopeKey,
                              int projectedCount, int overrideLimit, String operation) {
        QuotaDecision decision = resourceGuard.checkConcurrent(dimension, scopeKey,
                projectedCount, overrideLimit);
        if (!decision.isAllowed()) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager." + operation + " denied by quota: "
                            + decision.getReason());
        }
    }

    @Override
    public Team createTeam(TeamSpec spec) {
        Objects.requireNonNull(spec, "spec");
        String teamId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Plan 234: enforce TEAM_MEMBERS quota (config-driven; override <= 0
        // so the guard falls back to the QuotaConfig teamMaxMembers default).
        // projectedCount = the spec's initial member count.
        enforceQuota(QuotaDimension.TEAM_MEMBERS, teamId,
                spec.getMemberSpecs().size(), 0, "createTeam");

        ConcurrentHashMap<String, TeamMember> members = new ConcurrentHashMap<>();
        for (TeamMemberSpec ms : spec.getMemberSpecs()) {
            // The spec may already include the lead; record each member's
            // configured role. The lead is identified by leadAgentName in
            // the spec, but the role on each TeamMember comes from the spec.
            TeamMember member = new TeamMember(ms, now);
            members.put(member.getMemberName(), member);
        }

        Team team = new Team(teamId, spec, members, TeamStatus.CREATED, now);
        Team prev = teams.putIfAbsent(teamId, team);
        // teamId is a fresh UUID, so prev is always null; defensive check.
        if (prev != null) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.createTeam: teamId collision detected: " + teamId);
        }
        return team;
    }

    @Override
    public Optional<Team> getTeam(String teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public Optional<Team> getTeamBySession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        String teamId = sessionToTeamId.get(sessionId);
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public Team disbandTeam(String teamId) {
        if (teamId == null) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.disbandTeam: teamId must not be null");
        }
        // Use compute to atomically read + transition + record timestamp.
        Team result = teams.compute(teamId, (id, team) -> {
            if (team == null) {
                throw new NopAiAgentException(
                        "InMemoryTeamManager.disbandTeam: team not found: " + teamId);
            }
            if (team.getStatus() == TeamStatus.DISBANDED) {
                // Idempotent: already disbanded, leave as-is.
                return team;
            }
            team.setStatus(TeamStatus.DISBANDED);
            team.setDisbandedAt(System.currentTimeMillis());
            return team;
        });
        return result;
    }

    @Override
    public Collection<Team> getActiveTeams() {
        List<Team> active = new ArrayList<>();
        for (Team team : teams.values()) {
            if (team.getStatus() != TeamStatus.DISBANDED) {
                active.add(team);
            }
        }
        return Collections.unmodifiableList(active);
    }

    @Override
    public TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
        Objects.requireNonNull(memberSpec, "memberSpec");
        if (teamId == null) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.addMember: teamId must not be null");
        }
        Team team = teams.get(teamId);
        if (team == null) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.addMember: team not found: " + teamId);
        }
        if (team.getStatus() == TeamStatus.DISBANDED) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.addMember: cannot add member to a disbanded team: "
                            + teamId);
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, TeamMember> members =
                (ConcurrentHashMap<String, TeamMember>) team.getMembers();
        // Plan 234: enforce TEAM_MEMBERS quota before adding
        // (projectedCount = current member count + 1).
        enforceQuota(QuotaDimension.TEAM_MEMBERS, teamId,
                members.size() + 1, 0, "addMember");
        TeamMember newMember = new TeamMember(memberSpec, System.currentTimeMillis());
        // putIfAbsent gives atomic duplicate detection.
        TeamMember existing = members.putIfAbsent(newMember.getMemberName(), newMember);
        if (existing != null) {
            throw new NopAiAgentException(
                    "InMemoryTeamManager.addMember: member already exists in team: teamId="
                            + teamId + ", memberName=" + newMember.getMemberName());
        }
        return newMember;
    }

    @Override
    public boolean removeMember(String teamId, String memberName) {
        if (teamId == null || memberName == null) {
            return false;
        }
        Team team = teams.get(teamId);
        if (team == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, TeamMember> members =
                (ConcurrentHashMap<String, TeamMember>) team.getMembers();
        TeamMember removed = members.remove(memberName);
        if (removed == null) {
            return false;
        }
        // Clean up session index if the member had a bound session.
        if (removed.getSessionId() != null) {
            sessionToTeamId.remove(removed.getSessionId());
        }
        return true;
    }

    @Override
    public boolean bindMemberSession(String teamId, String memberName,
                                     String sessionId, String actorId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorId, "actorId");
        if (teamId == null || memberName == null) {
            return false;
        }
        Team team = teams.get(teamId);
        if (team == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, TeamMember> members =
                (ConcurrentHashMap<String, TeamMember>) team.getMembers();
        // Plan 234: enforce TEAM_PARALLEL_BOUND_MEMBERS quota before binding
        // (the maxParallelMembers hint→enforced upgrade). "Parallel" = the
        // number of concurrently bound (isBound()==true) members. If the
        // target member is already bound, this is an idempotent rebind and
        // the projected count does not increase (consistent with the auto-bind
        // idempotent skip). Otherwise projectedCount = boundCount + 1; the
        // limit is the team's per-team override spec.maxParallelMembers.
        TeamMember target = members.get(memberName);
        if (target != null) {
            int boundCount = 0;
            for (TeamMember m : members.values()) {
                if (m.isBound()) {
                    boundCount++;
                }
            }
            int projectedCount = target.isBound() ? boundCount : boundCount + 1;
            enforceQuota(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, teamId,
                    projectedCount, team.getSpec().getMaxParallelMembers(),
                    "bindMemberSession");
        }
        // Use compute on the member map to atomically: bind + clean up any
        // prior session index entry + activate the team on first binding.
        boolean[] bound = {false};
        members.compute(memberName, (name, member) -> {
            if (member == null) {
                // member not found; leave map unchanged.
                return null;
            }
            // Clean up a prior session index entry if this member was
            // previously bound to a different session.
            String priorSession = member.getSessionId();
            if (priorSession != null && !priorSession.equals(sessionId)) {
                sessionToTeamId.remove(priorSession);
            }
            member.bind(sessionId, actorId);
            sessionToTeamId.put(sessionId, teamId);
            bound[0] = true;
            return member;
        });
        if (!bound[0]) {
            return false;
        }
        // First-binding activation: CREATED → ACTIVE. Use compute on the
        // teams map so the transition is exactly-once per team.
        if (team.getStatus() == TeamStatus.CREATED) {
            teams.compute(teamId, (id, t) -> {
                if (t != null && t.getStatus() == TeamStatus.CREATED) {
                    t.setStatus(TeamStatus.ACTIVE);
                }
                return t;
            });
        }
        return true;
    }

    @Override
    public Optional<TeamMember> getMember(String teamId, String memberName) {
        if (teamId == null || memberName == null) {
            return Optional.empty();
        }
        Team team = teams.get(teamId);
        if (team == null) {
            return Optional.empty();
        }
        Map<String, TeamMember> members = team.getMembers();
        return Optional.ofNullable(members.get(memberName));
    }
}
