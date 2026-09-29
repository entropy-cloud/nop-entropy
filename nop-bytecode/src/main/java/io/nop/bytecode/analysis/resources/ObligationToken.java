package io.nop.bytecode.analysis.resources;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Obligation token carried in frame slots for tracked resource references (plan 06 algorithm
 * spec): immutable pair (nullState, openSites). openSites = acquire-site ids (instruction
 * indices) still obligated on this reference; nullState ∈ {DEF (fresh, provably non-null),
 * MAYBE, NULL} — the DEF/MAYBE dimension is required for guard-form exemption (a DEF token
 * compared against null makes the null branch unreachable, dropped via edgeFrame).
 *
 * <p>Values are immutable and merge by component-union (monotone, bounded → fixpoint
 * terminates). Tokens travel with the value through DUP/ASTORE/ALOAD (frame copy semantics).
 */
public final class ObligationToken {
    enum NullState { DEF, MAYBE, NULL }

    enum Kind { TRACKED, PLAIN, TOP }

    final Kind kind;
    final NullState nullState;
    final SortedSet<Integer> openSites;

    private ObligationToken(Kind kind, NullState nullState, SortedSet<Integer> openSites) {
        this.kind = kind;
        this.nullState = nullState;
        this.openSites = openSites;
    }

    static final ObligationToken TOP = new ObligationToken(Kind.TOP, NullState.MAYBE, Collections.emptySortedSet());
    static final ObligationToken PLAIN_NONNULL = new ObligationToken(Kind.PLAIN, NullState.DEF, Collections.emptySortedSet());
    static final ObligationToken PLAIN_NULL = new ObligationToken(Kind.PLAIN, NullState.NULL, Collections.emptySortedSet());
    static final ObligationToken PLAIN_MAYBE = new ObligationToken(Kind.PLAIN, NullState.MAYBE, Collections.emptySortedSet());
    static final ObligationToken NEW_MARKER = new ObligationToken(Kind.TOP, NullState.DEF, Collections.emptySortedSet());

    static ObligationToken tracked(NullState ns, int site) {
        SortedSet<Integer> sites = new TreeSet<>();
        sites.add(site);
        return new ObligationToken(Kind.TRACKED, ns, sites);
    }

    static ObligationToken tracked(NullState ns, SortedSet<Integer> sites) {
        return new ObligationToken(Kind.TRACKED, ns, sites);
    }

    boolean isOpen() {
        return kind == Kind.TRACKED && !openSites.isEmpty();
    }

    boolean isDefTracked() {
        return kind == Kind.TRACKED && nullState == NullState.DEF;
    }

    /** Component-union merge; plain values join by nullness (distinct → MAYBE). */
    static ObligationToken merge(ObligationToken a, ObligationToken b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.kind == Kind.TOP || b.kind == Kind.TOP) return TOP;
        if (a.kind == Kind.TRACKED || b.kind == Kind.TRACKED) {
            // token x token: nullness joins (distinct -> MAYBE); token x plain: nullness joins
            // conservatively (any null involvement -> MAYBE) — sites survive both sides
            NullState ans = a.kind == Kind.TRACKED ? a.nullState : b.nullState;
            NullState ns = (a.kind == Kind.TRACKED && b.kind == Kind.TRACKED)
                    ? (a.nullState == b.nullState ? a.nullState : NullState.MAYBE)
                    : ans;
            SortedSet<Integer> sites = new TreeSet<>(a.openSites);
            sites.addAll(b.openSites);
            return tracked(ns, sites);
        }
        // both plain
        if (a == b) return a;
        if (a.nullState == NullState.NULL && b.nullState == NullState.NULL) return PLAIN_NULL;
        return PLAIN_MAYBE;
    }

    /** Drop the given sites from this token (after a full-frame discharge sweep). */
    ObligationToken without(SortedSet<Integer> sites) {
        SortedSet<Integer> remaining = new TreeSet<>(openSites);
        remaining.removeAll(sites);
        return new ObligationToken(kind, nullState, remaining);
    }

    boolean containsAnyOf(SortedSet<Integer> sites) {
        for (int s : sites) {
            if (openSites.contains(s)) return true;
        }
        return false;
    }

    SortedSet<Integer> openSites() {
        return openSites;
    }

    @Override
    public String toString() {
        return kind + "/" + nullState + "/" + openSites;
    }
}
