package io.nop.autotest.bundle;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import io.nop.commons.util.FileHelper;

import java.util.List;
import java.util.Map;

/**
 * Fixture bundle manifest (M1.1). Serialized as JSON (a valid JSON5 subset) at
 * {@code manifest.json5}; the manifest is the bundle's discovery entry and its
 * table order is the unique load-order source (Decision C — app-erp-test-data's
 * load-order.txt is deprecated and scheduled for retirement registration in M2.3).
 */
@DataBean
public class FixtureBundleManifest {
    private int formatVersion;
    private String bundleName;
    private List<String> requires;
    private List<FixtureBundleTableEntry> baseTables;
    private List<FixtureBundleSnapshotEntry> snapshots;

    private static final java.util.Set<String> MANIFEST_KEYS = java.util.Set.of(
            "formatVersion", "bundleName", "requires", "baseTables", "snapshots");
    private static final java.util.Set<String> TABLE_KEYS = java.util.Set.of(
            "table", "layer", "businessKeys", "loadOrder", "rowCount", "csv", "sha256",
            "maskedColumns", "source", "captureGaps", "columnFingerprint", "includeLogicalDeleted");
    private static final java.util.Set<String> SNAPSHOT_KEYS = java.util.Set.of("name", "tables");

    @SuppressWarnings("unchecked")
    public static FixtureBundleManifest read(File dir) {
        File file = new File(dir, FixtureBundleConstants.MANIFEST_FILE);
        // forward compatibility (M1.2): unknown fields from future format versions are
        // stripped before the typed parse instead of failing bean construction
        Map<String, Object> json = (Map<String, Object>) JsonTool.parseNonStrict(FileHelper.readText(file, null));
        sanitize(json, MANIFEST_KEYS, "baseTables", TABLE_KEYS, "snapshots", SNAPSHOT_KEYS);
        return JsonTool.parseBeanFromText(JsonTool.stringify(json), FixtureBundleManifest.class);
    }

    @SuppressWarnings("unchecked")
    private static void sanitize(Map<String, Object> json, java.util.Set<String> allowed,
                                 String tableListKey, java.util.Set<String> tableKeys,
                                 String snapshotListKey, java.util.Set<String> snapshotKeys) {
        json.keySet().retainAll(allowed);
        Object tables = json.get(tableListKey);
        if (tables instanceof List)
            for (Object t : (List<Object>) tables)
                if (t instanceof Map)
                    ((Map<String, Object>) t).keySet().retainAll(tableKeys);
        Object snapshots = json.get(snapshotListKey);
        if (snapshots instanceof List)
            for (Object snapshot : (List<Object>) snapshots) {
                if (!(snapshot instanceof Map))
                    continue;
                Map<String, Object> snap = (Map<String, Object>) snapshot;
                snap.keySet().retainAll(snapshotKeys);
                Object snapTables = snap.get("tables");
                if (snapTables instanceof List)
                    for (Object t : (List<Object>) snapTables)
                        if (t instanceof Map)
                            ((Map<String, Object>) t).keySet().retainAll(tableKeys);
            }
    }

    public void write(File dir) {
        File file = new File(dir, FixtureBundleConstants.MANIFEST_FILE);
        String text = JsonTool.stringify(this, null, "  ");
        io.nop.commons.util.FileHelper.writeText(file, text, null);
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public List<String> getRequires() {
        return requires;
    }

    public void setRequires(List<String> requires) {
        this.requires = requires;
    }

    public List<FixtureBundleTableEntry> getBaseTables() {
        return baseTables;
    }

    public void setBaseTables(List<FixtureBundleTableEntry> baseTables) {
        this.baseTables = baseTables;
    }

    public List<FixtureBundleSnapshotEntry> getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(List<FixtureBundleSnapshotEntry> snapshots) {
        this.snapshots = snapshots;
    }
}
