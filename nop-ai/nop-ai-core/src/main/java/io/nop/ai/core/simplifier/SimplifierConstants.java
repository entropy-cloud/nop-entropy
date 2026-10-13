package io.nop.ai.core.simplifier;

import java.util.Set;

/**
 * Positioning keys kept by default when simplifying JSON/XNode structures
 * (relocated from io.nop.ai.coder.AiCoderConstants by WI15/plan 2452).
 */
public final class SimplifierConstants {
    public static final Set<String> DEFAULT_POSITIONING_KEYS = Set.of("id", "name", "type");

    private SimplifierConstants() {
    }
}
