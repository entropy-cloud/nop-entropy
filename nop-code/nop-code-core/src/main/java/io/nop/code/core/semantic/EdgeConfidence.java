package io.nop.code.core.semantic;

/**
 * Semantic edge confidence levels.
 */
public enum EdgeConfidence {
    EXTRACTED(10),
    INFERRED(20),
    AMBIGUOUS(30);

    private final int value;

    EdgeConfidence(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static EdgeConfidence fromValue(int value) {
        for (EdgeConfidence c : values()) {
            if (c.value == value) return c;
        }
        return EXTRACTED;
    }
}
