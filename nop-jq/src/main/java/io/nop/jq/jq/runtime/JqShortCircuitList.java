package io.nop.jq.jq.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An output sink that stops evaluation by throwing {@link JqStopException}
 * once a condition on the collected outputs is met. Used to give jq's lazy
 * semantics to limit/first/any/all/isempty: producers append incrementally and
 * unwind as soon as the consumer is satisfied.
 */
final class JqShortCircuitList extends ArrayList<JqValue> {
    private final Predicate<List<JqValue>> stopWhen;

    JqShortCircuitList(Predicate<List<JqValue>> stopWhen) {
        this.stopWhen = stopWhen;
    }

    private void checkStop() {
        if (stopWhen.test(this))
            throw JqStopException.INSTANCE;
    }

    /** Whether the stop condition already holds (checked before evaluating children). */
    boolean stopRequested() {
        return stopWhen.test(this);
    }

    @Override
    public boolean add(JqValue v) {
        super.add(v);
        checkStop();
        return true;
    }

    @Override
    public void add(int index, JqValue v) {
        super.add(index, v);
        checkStop();
    }

    @Override
    public boolean addAll(java.util.Collection<? extends JqValue> c) {
        for (JqValue v : c) {
            add(v);
        }
        return true;
    }
}
