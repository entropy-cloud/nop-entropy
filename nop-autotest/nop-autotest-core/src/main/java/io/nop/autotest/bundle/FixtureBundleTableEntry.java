package io.nop.autotest.bundle;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * Manifest entry for one exported table. Field set is the roadmap M1.1 mandatory list
 * (plan 2026-10-01-2049-1 Decision A); forward extension (e.g. M1.2 column fingerprints)
 * must be reviewed under M1.2's own dual-agent approval.
 */
@DataBean
public class FixtureBundleTableEntry {
    private String table;
    private String layer;
    private List<String> businessKeys;
    private int loadOrder;
    private int rowCount;
    private String csv;
    private String sha256;
    private List<String> maskedColumns;
    private String source;
    private int captureGaps;
    private String columnFingerprint;
    private Boolean includeLogicalDeleted;

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public List<String> getBusinessKeys() {
        return businessKeys;
    }

    public void setBusinessKeys(List<String> businessKeys) {
        this.businessKeys = businessKeys;
    }

    public int getLoadOrder() {
        return loadOrder;
    }

    public void setLoadOrder(int loadOrder) {
        this.loadOrder = loadOrder;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public String getCsv() {
        return csv;
    }

    public void setCsv(String csv) {
        this.csv = csv;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public List<String> getMaskedColumns() {
        return maskedColumns;
    }

    public void setMaskedColumns(List<String> maskedColumns) {
        this.maskedColumns = maskedColumns;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getCaptureGaps() {
        return captureGaps;
    }

    public void setCaptureGaps(int captureGaps) {
        this.captureGaps = captureGaps;
    }

    public String getColumnFingerprint() {
        return columnFingerprint;
    }

    public void setColumnFingerprint(String columnFingerprint) {
        this.columnFingerprint = columnFingerprint;
    }

    public Boolean getIncludeLogicalDeleted() {
        return includeLogicalDeleted;
    }

    public void setIncludeLogicalDeleted(Boolean includeLogicalDeleted) {
        this.includeLogicalDeleted = includeLogicalDeleted;
    }
}
