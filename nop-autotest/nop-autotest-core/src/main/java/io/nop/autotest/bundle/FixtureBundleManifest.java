package io.nop.autotest.bundle;

import io.nop.api.core.annotations.data.DataBean;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.List;

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

    public static FixtureBundleManifest read(File dir) {
        IResource resource = new FileResource(new File(dir, FixtureBundleConstants.MANIFEST_FILE));
        return JsonTool.parseBeanFromResource(resource, FixtureBundleManifest.class);
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
