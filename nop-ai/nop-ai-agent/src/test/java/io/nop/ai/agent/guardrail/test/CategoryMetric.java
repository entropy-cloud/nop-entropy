package io.nop.ai.agent.guardrail.test;

import java.util.Objects;

/**
 * Per-category (or per-threatClass) metric slice inside a
 * {@link GuardrailTestReport} (design {@code guardrail-contract.md} §增量 1,
 * Decision D). Immutable value object.
 *
 * <p>Headline rates are computed over attack (BLOCK-expected) cases unless the
 * category is a benign category (only PASS-expected cases), in which case only
 * the false-positive rate is meaningful.
 */
public final class CategoryMetric {

    private final String category;
    private final int totalAttacks;
    private final int blockedAttacks;
    private final int leakedAttacks;
    private final int modifiedAttacks;
    private final int totalBenign;
    private final int falselyBlockedBenign;

    public CategoryMetric(String category, int totalAttacks, int blockedAttacks, int leakedAttacks,
                          int modifiedAttacks, int totalBenign, int falselyBlockedBenign) {
        this.category = category;
        this.totalAttacks = totalAttacks;
        this.blockedAttacks = blockedAttacks;
        this.leakedAttacks = leakedAttacks;
        this.modifiedAttacks = modifiedAttacks;
        this.totalBenign = totalBenign;
        this.falselyBlockedBenign = falselyBlockedBenign;
    }

    public String getCategory() {
        return category;
    }

    public int getTotalAttacks() {
        return totalAttacks;
    }

    public int getBlockedAttacks() {
        return blockedAttacks;
    }

    public int getLeakedAttacks() {
        return leakedAttacks;
    }

    public int getModifiedAttacks() {
        return modifiedAttacks;
    }

    public int getTotalBenign() {
        return totalBenign;
    }

    public int getFalselyBlockedBenign() {
        return falselyBlockedBenign;
    }

    public double getBlockRate() {
        return totalAttacks == 0 ? 0.0 : (double) blockedAttacks / totalAttacks;
    }

    public double getLeakRate() {
        return totalAttacks == 0 ? 0.0 : (double) leakedAttacks / totalAttacks;
    }

    public double getFalsePositiveRate() {
        return totalBenign == 0 ? 0.0 : (double) falselyBlockedBenign / totalBenign;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryMetric that = (CategoryMetric) o;
        return totalAttacks == that.totalAttacks
                && blockedAttacks == that.blockedAttacks
                && leakedAttacks == that.leakedAttacks
                && modifiedAttacks == that.modifiedAttacks
                && totalBenign == that.totalBenign
                && falselyBlockedBenign == that.falselyBlockedBenign
                && Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, totalAttacks, blockedAttacks, leakedAttacks, modifiedAttacks,
                totalBenign, falselyBlockedBenign);
    }
}
