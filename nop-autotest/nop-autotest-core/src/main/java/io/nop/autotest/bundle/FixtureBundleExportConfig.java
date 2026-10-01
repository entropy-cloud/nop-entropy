package io.nop.autotest.bundle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer-supplied export configuration: which tables participate, their layer
 * attribution, business keys (mandatory for base layer — the M1.2 base-reconciliation
 * contract) and masked columns (sensitive discipline, roadmap cross-cutting 5).
 */
public class FixtureBundleExportConfig {
    private String bundleName;
    private String snapshotName;
    private List<String> requires;
    private final Map<String, TableConfig> tables = new LinkedHashMap<>();

    public FixtureBundleExportConfig(String bundleName, String snapshotName) {
        this.bundleName = bundleName;
        this.snapshotName = snapshotName;
    }

    public FixtureBundleExportConfig addBaseTable(String entityName, List<String> businessKeys) {
        tables.put(entityName, new TableConfig(FixtureBundleConstants.LAYER_BASE, businessKeys));
        return this;
    }

    public FixtureBundleExportConfig addBaseTable(String entityName, List<String> businessKeys, List<String> maskedColumns) {
        tables.put(entityName, new TableConfig(FixtureBundleConstants.LAYER_BASE, businessKeys, maskedColumns));
        return this;
    }

    public FixtureBundleExportConfig addPayloadTable(String entityName) {
        tables.put(entityName, new TableConfig(FixtureBundleConstants.LAYER_PAYLOAD, null));
        return this;
    }

    public FixtureBundleExportConfig addPayloadTable(String entityName, List<String> maskedColumns) {
        tables.put(entityName, new TableConfig(FixtureBundleConstants.LAYER_PAYLOAD, null, maskedColumns));
        return this;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public List<String> getRequires() {
        return requires;
    }

    public void setRequires(List<String> requires) {
        this.requires = requires;
    }

    public Map<String, TableConfig> getTables() {
        return tables;
    }

    public TableConfig getTableConfig(String entityName) {
        return tables.get(entityName);
    }

    public static class TableConfig {
        private final String layer;
        private final List<String> businessKeys;
        private final List<String> maskedColumns;

        public TableConfig(String layer, List<String> businessKeys) {
            this(layer, businessKeys, null);
        }

        public TableConfig(String layer, List<String> businessKeys, List<String> maskedColumns) {
            this.layer = layer;
            this.businessKeys = businessKeys;
            this.maskedColumns = maskedColumns;
        }

        public String getLayer() {
            return layer;
        }

        public List<String> getBusinessKeys() {
            return businessKeys;
        }

        public List<String> getMaskedColumns() {
            return maskedColumns;
        }
    }
}
