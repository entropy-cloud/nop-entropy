package io.nop.ai.agent.guardrail.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Simple {@link AttackPlugin} backed by an in-memory case list. Useful for
 * unit tests and programmatic suite construction.
 */
public class ListAttackPlugin implements AttackPlugin {

    private final String name;
    private final List<AttackCase> cases;

    public ListAttackPlugin(String name, List<AttackCase> cases) {
        this.name = Objects.requireNonNull(name, "name");
        // defensive copy so later mutation of the source list cannot affect
        // this plugin's corpus
        this.cases = Collections.unmodifiableList(new ArrayList<>(cases));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<AttackCase> cases() {
        return cases;
    }
}
