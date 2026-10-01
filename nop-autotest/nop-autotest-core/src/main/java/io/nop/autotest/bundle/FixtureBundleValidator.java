package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.autotest.bundle.FixtureBundleConstants.FORMAT_VERSION;
import static io.nop.autotest.bundle.FixtureBundleConstants.LAYER_BASE;
import static io.nop.autotest.bundle.FixtureBundleErrors.ARG_ERRORS;
import static io.nop.autotest.bundle.FixtureBundleErrors.ERR_FIXTURE_BUNDLE_VALIDATION_FAIL;

/**
 * Independent bundle validator (M1.1 export-side acceptance). Reads the bundle from
 * disk — never the exporter's in-memory state — and checks: manifest completeness,
 * declared rowCount vs actual CSV rows, SHA-256 integrity (purpose: detect manual CSV
 * edits/truncation — NOT an anti-tampering security guarantee), captureGaps
 * bookkeeping, base-layer business-key declarations and the sensitive-column rules.
 */
public class FixtureBundleValidator {

    public void validate(File bundleDir) {
        validate(bundleDir, new FixtureBundleSensitiveRules());
    }

    public void validate(File bundleDir, FixtureBundleSensitiveRules rules) {
        List<String> errors = new ArrayList<>();
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        if (!manifestFile.exists()) {
            throw new NopException(ERR_FIXTURE_BUNDLE_VALIDATION_FAIL).param(ARG_ERRORS, "manifest.json5 missing");
        }
        FixtureBundleManifest manifest = FixtureBundleManifest.read(bundleDir);

        if (manifest.getFormatVersion() != FORMAT_VERSION)
            errors.add("formatVersion must be " + FORMAT_VERSION + " but was " + manifest.getFormatVersion());
        if (manifest.getBundleName() == null || manifest.getBundleName().isEmpty())
            errors.add("bundleName is required");
        if ((manifest.getBaseTables() == null || manifest.getBaseTables().isEmpty())
                && (manifest.getSnapshots() == null || manifest.getSnapshots().isEmpty()))
            errors.add("manifest must declare baseTables or snapshots");

        if (manifest.getBaseTables() != null) {
            for (FixtureBundleTableEntry entry : manifest.getBaseTables()) {
                validateTable(bundleDir, entry, true, errors, rules);
            }
        }
        if (manifest.getSnapshots() != null) {
            for (FixtureBundleSnapshotEntry snapshot : manifest.getSnapshots()) {
                if (snapshot.getName() == null || snapshot.getName().isEmpty()) {
                    errors.add("snapshot entry with empty name");
                    continue;
                }
                if (snapshot.getTables() != null) {
                    for (FixtureBundleTableEntry entry : snapshot.getTables()) {
                        validateTable(bundleDir, entry, false, errors, rules);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new NopException(ERR_FIXTURE_BUNDLE_VALIDATION_FAIL).param(ARG_ERRORS, String.join("; ", errors));
        }
    }

    private void validateTable(File bundleDir, FixtureBundleTableEntry entry, boolean base, List<String> errors,
                               FixtureBundleSensitiveRules rules) {
        String label = (base ? "base:" : "payload:") + entry.getTable();
        if (entry.getLayer() == null || entry.getTable() == null || entry.getCsv() == null) {
            errors.add(label + " entry missing table/layer/csv");
            return;
        }
        if (base) {
            if (!LAYER_BASE.equals(entry.getLayer()))
                errors.add(label + " declared under baseTables but layer=" + entry.getLayer());
            if (entry.getBusinessKeys() == null || entry.getBusinessKeys().isEmpty())
                errors.add(label + " base layer requires businessKeys declaration");
        }
        if (entry.getLoadOrder() <= 0)
            errors.add(label + " loadOrder must be positive");

        File csvFile = new File(bundleDir, entry.getCsv());
        if (!csvFile.exists()) {
            errors.add(label + " csv missing: " + entry.getCsv());
            return;
        }

        String actualSha = FixtureBundleExporter.sha256(csvFile);
        if (!actualSha.equals(entry.getSha256()))
            errors.add(label + " sha256 mismatch (csv manually edited or truncated)");

        List<Map<String, Object>> rows = CsvHelper.readCsv(new FileResource(csvFile));
        if (rows.size() != entry.getRowCount())
            errors.add(label + " rowCount declared " + entry.getRowCount() + " but csv has " + rows.size());
        if (entry.getCaptureGaps() < 0)
            errors.add(label + " captureGaps must be >= 0");

        String violation = rules.findViolation(entry.getTable(), new ArrayList<>(rows.isEmpty()
                ? List.of() : rows.get(0).keySet()), entry.getMaskedColumns());
        if (violation != null)
            errors.add(label + " sensitive column [" + violation + "] not marked masked");
    }
}
