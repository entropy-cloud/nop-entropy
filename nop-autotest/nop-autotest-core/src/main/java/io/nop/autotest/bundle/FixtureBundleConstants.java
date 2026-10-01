package io.nop.autotest.bundle;

/**
 * Fixture bundle format constants (M1.1, nop-app-erp plan 2026-10-01-2049-1).
 */
public interface FixtureBundleConstants {
    int FORMAT_VERSION = 1;

    String MANIFEST_FILE = "manifest.json5";

    String BASE_DIR = "base";
    String SNAPSHOTS_DIR = "snapshots";

    String LAYER_BASE = "base";
    String LAYER_PAYLOAD = "payload";

    /**
     * Placeholder written for columns marked masked in the export config. Repo-level gate
     * (preventing plaintext credential columns entering git) is a consumer-side duty
     * handed over per plan M1.1 closure.
     */
    String MASKED_PLACEHOLDER = "MASKED-BUNDLE-SEED";

    /**
     * Source mark for rows captured by the observed construction session.
     */
    String SOURCE_OBSERVED = "observed-session";
}
