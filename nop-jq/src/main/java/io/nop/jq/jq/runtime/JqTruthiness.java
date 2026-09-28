package io.nop.jq.jq.runtime;

/**
 * jq truthiness: only null and false are falsy; everything else is truthy.
 */
public final class JqTruthiness {
    private JqTruthiness() {
    }

    public static boolean of(JqValue v) {
        if (v instanceof JqNull)
            return false;
        if (v instanceof JqBoolean b)
            return b.value();
        return true;
    }
}
