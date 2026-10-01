package io.nop.autotest.bundle;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * One named business snapshot of the bundle: the payload-layer tables captured in a
 * single export session. Directory counterpart is {@code snapshots/<name>/} — this is
 * the directory implementation of the roadmap's "payload layer" terminology
 * (plan 2026-10-01-2049-1 Decision B).
 */
@DataBean
public class FixtureBundleSnapshotEntry {
    private String name;
    private List<FixtureBundleTableEntry> tables;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FixtureBundleTableEntry> getTables() {
        return tables;
    }

    public void setTables(List<FixtureBundleTableEntry> tables) {
        this.tables = tables;
    }
}
