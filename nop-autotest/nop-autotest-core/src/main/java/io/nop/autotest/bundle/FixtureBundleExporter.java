package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.core.execute.EntityRow;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.autotest.bundle.FixtureBundleConstants.BASE_DIR;
import static io.nop.autotest.bundle.FixtureBundleConstants.LAYER_BASE;
import static io.nop.autotest.bundle.FixtureBundleConstants.LAYER_PAYLOAD;
import static io.nop.autotest.bundle.FixtureBundleConstants.SOURCE_OBSERVED;
import static io.nop.autotest.bundle.FixtureBundleConstants.SNAPSHOTS_DIR;
import static io.nop.autotest.bundle.FixtureBundleErrors.ERR_FIXTURE_BUNDLE_BASE_KEYS_REQUIRED;
import static io.nop.autotest.bundle.FixtureBundleErrors.ERR_FIXTURE_BUNDLE_TABLE_NOT_CONFIGURED;

/**
 * Observed full-row fixture bundle exporter (M1.1, export-side closed loop).
 * <p>
 * Flow: recording session collects touched rows → session window closes → each row is
 * reloaded in a fresh ORM session for its FULL column image (onSave only carries set
 * columns; DB-default columns only exist after reload) → rows are layered per the
 * export config into base/ (business-key reconciled on import) or snapshots/<name>/
 * (payload layer, PK stripped on import) → masked columns replaced by placeholder →
 * CSV via CsvHelper + SHA-256 → manifest written as the unique load-order source
 * (topo order of the bundle's table subset).
 * <p>
 * Scope: rows touched by the construction session — never a full-table dump.
 */
public class FixtureBundleExporter {

    /**
     * Export one bundle (or add one named snapshot to an existing bundle directory).
     * Returns the written manifest.
     */
    public FixtureBundleManifest export(File bundleDir, FixtureBundleExportConfig config,
                                        FixtureBundleRecordingSession session, IOrmTemplate orm) {
        Map<IEntityModel, Map<String, EntityRow>> collected = session.data();
        if (collected.isEmpty())
            throw new NopException(ERR_FIXTURE_BUNDLE_TABLE_NOT_CONFIGURED).param("tableName", "<none: nothing collected>");

        List<FixtureBundleTableEntry> baseEntries = new ArrayList<>();
        List<FixtureBundleTableEntry> payloadEntries = new ArrayList<>();

        // topo order over the bundle's table subset (Decision ⑤: sortEntityModelInTopoOrder(Collection))
        List<IEntityModel> topoOrder = orm.getSessionFactory().getOrmModel()
                .sortEntityModelInTopoOrder(new ArrayList<>(collected.keySet()));
        int loadOrder = 1;
        for (IEntityModel entityModel : topoOrder) {
            FixtureBundleExportConfig.TableConfig tableConfig = config.getTableConfig(entityModel.getName());
            if (tableConfig == null)
                throw new NopException(ERR_FIXTURE_BUNDLE_TABLE_NOT_CONFIGURED).param("tableName", entityModel.getName());
            if (LAYER_BASE.equals(tableConfig.getLayer()) && tableConfig.getBusinessKeys().isEmpty())
                throw new NopException(ERR_FIXTURE_BUNDLE_BASE_KEYS_REQUIRED).param("tableName", entityModel.getName());

            Map<String, EntityRow> rows = collected.get(entityModel);
            Map<String, Object> gaps = new LinkedHashMap<>();
            List<Map<String, Object>> fullRows = new ArrayList<>();
            for (String id : rows.keySet()) {
                Map<String, Object> fullRow = session.reloadFullRow(orm, entityModel, id);
                if (fullRow == null) {
                    gaps.put(id, id); // deleted within the construction session
                } else {
                    applyMasking(fullRow, tableConfig);
                    fullRows.add(fullRow);
                }
            }

            FixtureBundleTableEntry entry = new FixtureBundleTableEntry();
            entry.setTable(entityModel.getName());
            entry.setLayer(tableConfig.getLayer());
            entry.setBusinessKeys(tableConfig.getBusinessKeys());
            entry.setLoadOrder(loadOrder++);
            entry.setRowCount(fullRows.size());
            entry.setMaskedColumns(tableConfig.getMaskedColumns());
            entry.setSource(SOURCE_OBSERVED);
            entry.setCaptureGaps(gaps.size());

            String relativeCsv = writeCsv(bundleDir, config, entityModel, fullRows);
            entry.setCsv(relativeCsv);
            entry.setSha256(sha256(new File(bundleDir, relativeCsv)));

            if (LAYER_BASE.equals(tableConfig.getLayer())) {
                baseEntries.add(entry);
            } else if (LAYER_PAYLOAD.equals(tableConfig.getLayer())) {
                payloadEntries.add(entry);
            } else {
                throw new NopException(ERR_FIXTURE_BUNDLE_TABLE_NOT_CONFIGURED)
                        .param("tableName", entityModel.getName() + " [unknown layer " + tableConfig.getLayer() + "]");
            }
        }

        FixtureBundleManifest manifest = existingManifest(bundleDir);
        manifest.setFormatVersion(FixtureBundleConstants.FORMAT_VERSION);
        manifest.setBundleName(config.getBundleName());
        if (config.getRequires() != null)
            manifest.setRequires(config.getRequires());
        mergeBaseTables(manifest, baseEntries);
        mergeSnapshot(manifest, config.getSnapshotName(), payloadEntries);
        manifest.write(bundleDir);
        return manifest;
    }

    private FixtureBundleManifest existingManifest(File bundleDir) {
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        if (manifestFile.exists())
            return FixtureBundleManifest.read(bundleDir);
        FixtureBundleManifest manifest = new FixtureBundleManifest();
        manifest.setBaseTables(new ArrayList<>());
        manifest.setSnapshots(new ArrayList<>());
        return manifest;
    }

    private void mergeBaseTables(FixtureBundleManifest manifest, List<FixtureBundleTableEntry> baseEntries) {
        Map<String, FixtureBundleTableEntry> byName = new LinkedHashMap<>();
        if (manifest.getBaseTables() != null) {
            for (FixtureBundleTableEntry e : manifest.getBaseTables())
                byName.put(e.getTable(), e);
        }
        for (FixtureBundleTableEntry e : baseEntries)
            byName.put(e.getTable(), e);
        manifest.setBaseTables(new ArrayList<>(byName.values()));
    }

    private void mergeSnapshot(FixtureBundleManifest manifest, String snapshotName, List<FixtureBundleTableEntry> entries) {
        List<FixtureBundleSnapshotEntry> snapshots = manifest.getSnapshots();
        if (snapshots == null)
            snapshots = new ArrayList<>();
        FixtureBundleSnapshotEntry target = null;
        for (FixtureBundleSnapshotEntry s : snapshots) {
            if (s.getName().equals(snapshotName))
                target = s;
        }
        if (target == null) {
            target = new FixtureBundleSnapshotEntry();
            target.setName(snapshotName);
            snapshots.add(target);
        }
        target.setTables(entries);
        manifest.setSnapshots(snapshots);
    }

    private void applyMasking(Map<String, Object> row, FixtureBundleExportConfig.TableConfig config) {
        List<String> masked = config.getMaskedColumns();
        if (masked == null)
            return;
        for (String col : masked) {
            if (row.containsKey(col))
                row.put(col, FixtureBundleConstants.MASKED_PLACEHOLDER);
        }
    }

    private String writeCsv(File bundleDir, FixtureBundleExportConfig config, IEntityModel entityModel,
                            List<Map<String, Object>> rows) {
        boolean base = LAYER_BASE.equals(config.getTableConfig(entityModel.getName()).getLayer());
        File csvFile = base
                ? new File(bundleDir, BASE_DIR + "/" + entityModel.getName() + ".csv")
                : new File(bundleDir, SNAPSHOTS_DIR + "/" + config.getSnapshotName() + "/" + entityModel.getName() + ".csv");
        // FileResource requires parent dirs to exist
        csvFile.getParentFile().mkdirs();

        List<String> headers = new ArrayList<>();
        for (io.nop.orm.model.IColumnModel col : entityModel.getColumns()) {
            headers.add(col.getCode());
        }
        CsvHelper.writeCsv(new FileResource(csvFile),
                org.apache.commons.csv.CSVFormat.DEFAULT, headers, rows);
        return base
                ? BASE_DIR + "/" + entityModel.getName() + ".csv"
                : SNAPSHOTS_DIR + "/" + config.getSnapshotName() + "/" + entityModel.getName() + ".csv";
    }

    static String sha256(File file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw NopException.adapt(e);
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return StringHelper.bytesToHex(digest.digest());
    }
}
