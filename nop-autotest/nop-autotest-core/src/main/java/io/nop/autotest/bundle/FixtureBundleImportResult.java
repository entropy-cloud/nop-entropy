package io.nop.autotest.bundle;

import java.util.HashMap;
import java.util.Map;

/**
 * Import outcome report (M1.2): per-pass counts, per-table max new ids (Decision E:
 * consumer-side sequence-no-conflict assertion input) and warnings from tolerant
 * checks.
 */
public class FixtureBundleImportResult {
    private int baseImported;
    private int baseSkipped;
    private int payloadImported;
    private int payloadSkipped;
    private final Map<String, Object> maxNewIds = new HashMap<>();
    private final Map<String, String> warnings = new HashMap<>();

    public int getBaseImported() {
        return baseImported;
    }

    public void incrBaseImported() {
        this.baseImported++;
    }

    public int getBaseSkipped() {
        return baseSkipped;
    }

    public void incrBaseSkipped() {
        this.baseSkipped++;
    }

    public int getPayloadImported() {
        return payloadImported;
    }

    public void incrPayloadImported() {
        this.payloadImported++;
    }

    public int getPayloadSkipped() {
        return payloadSkipped;
    }

    public void incrPayloadSkipped() {
        this.payloadSkipped++;
    }

    public Map<String, Object> getMaxNewIds() {
        return maxNewIds;
    }

    public void recordMaxNewId(String table, Object value) {
        if (!(value instanceof Comparable)) {
            maxNewIds.putIfAbsent(table, value);
            return;
        }
        Comparable<Object> comparable = (Comparable<Object>) value;
        Object old = maxNewIds.get(table);
        if (old == null || comparable.compareTo(old) > 0)
            maxNewIds.put(table, value);
    }

    public Map<String, String> getWarnings() {
        return warnings;
    }

    public void addWarning(String key, String warning) {
        warnings.put(key, warning);
    }
}
