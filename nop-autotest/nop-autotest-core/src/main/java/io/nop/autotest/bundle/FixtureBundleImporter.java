package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static io.nop.autotest.bundle.FixtureBundleImportErrors.*;
import static io.nop.autotest.bundle.FixtureBundleConstants.LAYER_BASE;

/**
 * Layered fixture bundle importer (M1.2).
 * <p>
 * Pass 1 (base, one transaction): business-key reconciliation — rows whose business
 * keys already exist in the target are registered in the mapping and NOT overwritten;
 * missing rows are inserted with the PK stripped (platform generates). Pass 2
 * (payload, one transaction; base failure never reaches it): every row has its PK
 * stripped for platform generation, to-one FK columns are rewritten through the
 * oldId→newId mapping (values not in the mapping must already exist in the target or
 * the import is rejected — dangling refs are silently accepted by direct inserts, so
 * this check is the importer's mandatory integrity backstop), version/delVersion are
 * forced to initial values and never read from the package, and tables are loaded in
 * manifest loadOrder (subset topo order).
 * <p>
 * Scope guards: system/sequence tables (nop_sys_* shaped) are rejected at the entry;
 * packages declaring includeLogicalDeleted=true are rejected (deleted rows are never
 * rebuilt); composite-PK payload tables are rejected (PK stripping relies on the
 * platform single-column generation semantic). Idempotency is in-run only (base
 * reconciliation is naturally idempotent; payload skips rows already imported by this
 * importer instance) — cross-process re-import duplication is a documented consumer
 * discipline. No persistence artifacts are created (M1.2 approval condition).
 * <p>
 * Sequence alignment is intentionally NOT built in (Decision E): new payload ids come
 * from the target platform's sequence generation; the result report carries per-table
 * max(newId) for consumer-side no-conflict assertions.
 */
public class FixtureBundleImporter {
    private static final Set<String> VERSION_CODES = Set.of("VERSION", "DEL_VERSION");

    private final Set<String> importedPayloadKeys = new HashSet<>();

    /**
     * Column-fingerprint normalization — SAME source for export and import sides
     * (approval condition B3): sorted column names, joined, SHA-256.
     */
    public static String fingerprint(Iterable<String> columnNames) {
        List<String> sorted = new ArrayList<>();
        columnNames.forEach(sorted::add);
        sorted.sort(String::compareTo);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.join(",", sorted).getBytes(StandardCharsets.UTF_8));
            return StringHelper.bytesToHex(bytes);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public FixtureBundleImportResult importBundle(File bundleDir, IOrmTemplate orm) {
        return importBundle(bundleDir, orm, null, false);
    }

    /**
     * @param requiresResolver custom predicate deciding whether a required bundle name
     *                         is satisfiable; null = default (sibling directory with a
     *                         manifest.json5)
     * @param tolerantDrift    true = schema-drift registers a warning and continues;
     *                         false (default) = fail-fast
     */
    public FixtureBundleImportResult importBundle(File bundleDir, IOrmTemplate orm,
                                                  Predicate<String> requiresResolver, boolean tolerantDrift) {
        FixtureBundleManifest manifest = FixtureBundleManifest.read(bundleDir);
        FixtureBundleImportResult result = new FixtureBundleImportResult();

        List<FixtureBundleTableEntry> allEntries = collectEntries(manifest);
        if (allEntries.isEmpty())
            throw new NopException(ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT).param(ARG_TABLE_NAME, "<manifest has no tables>");

        // entry-point guards: sys/sequence tables and logical-deleted declarations
        for (FixtureBundleTableEntry entry : allEntries) {
            if (isSysTable(entry.getTable()))
                throw new NopException(ERR_FIXTURE_BUNDLE_SYS_TABLE_FORBIDDEN).param(ARG_TABLE_NAME, entry.getTable());
            if (Boolean.TRUE.equals(entry.getIncludeLogicalDeleted()))
                throw new NopException(ERR_FIXTURE_BUNDLE_LOGICAL_DELETED).param(ARG_TABLE_NAME, entry.getTable());
        }

        // requires check
        Predicate<String> resolver = requiresResolver != null ? requiresResolver : defaultRequiresResolver(bundleDir);
        if (manifest.getRequires() != null) {
            for (String required : manifest.getRequires()) {
                if (!resolver.test(required))
                    throw new NopException(ERR_FIXTURE_BUNDLE_REQUIRES_MISSING).param(ARG_BUNDLE_NAME, required);
            }
        }

        IEntityModelResolver models = name -> orm.getSessionFactory().getOrmModel().getEntityModel(name);

        // drift check: manifest fingerprint vs target EntityModel
        for (FixtureBundleTableEntry entry : allEntries) {
            if (entry.getColumnFingerprint() == null) {
                result.addWarning(entry.getTable(), "manifest has no columnFingerprint — drift check skipped");
                continue;
            }
            IEntityModel model = models.resolve(entry.getTable());
            if (model == null)
                throw new NopException(ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT).param(ARG_TABLE_NAME, entry.getTable());
            List<String> targetCols = new ArrayList<>();
            for (IColumnModel col : model.getColumns())
                targetCols.add(col.getCode());
            String targetFingerprint = fingerprint(targetCols);
            if (!targetFingerprint.equals(entry.getColumnFingerprint())) {
                if (tolerantDrift) {
                    result.addWarning(entry.getTable(), "schema drift detected in tolerant mode");
                } else {
                    throw new NopException(ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT).param(ARG_TABLE_NAME, entry.getTable());
                }
            }
        }

        IDaoProvider daoProvider = (IDaoProvider) BeanContainer.tryGetBean("nopDaoProvider");
        IJdbcTemplate jdbc = (IJdbcTemplate) BeanContainer.tryGetBean("nopJdbcTemplate");
        ITransactionTemplate txn = jdbc.txn();

        // pass 1: base business-key reconciliation (one transaction)
        Map<String, Map<String, Object>> idMapping = new LinkedHashMap<>();
        if (manifest.getBaseTables() != null) {
            List<FixtureBundleTableEntry> baseEntries = new ArrayList<>(manifest.getBaseTables());
            baseEntries.sort(java.util.Comparator.comparingInt(FixtureBundleTableEntry::getLoadOrder));
            try {
                txn.runInTransaction(null, io.nop.api.core.annotations.txn.TransactionPropagation.REQUIRED, t -> {
                    for (FixtureBundleTableEntry entry : baseEntries)
                        importBaseEntry(entry, bundleDir, orm, models, idMapping, result);
                    return null;
                });
            } catch (NopException e) {
                // deferred constraint enforcement (flush at txn commit) surfaces duplicate
                // business keys past the reconciliation window — rewrap as the named UK code
                if (e.getErrorCode() != null
                        && "nop.err.dao.sql.duplicate-key".equals(e.getErrorCode())) {
                    throw new NopException(ERR_FIXTURE_BUNDLE_UK_CONFLICT).param(ARG_TABLE_NAME, "<base pass>")
                            .cause(e);
                }
                throw e;
            }
        }

        // pass 2: payload remap (one transaction; never reached if base failed)
        if (manifest.getSnapshots() != null) {
            List<FixtureBundleTableEntry> payloadEntries = new ArrayList<>();
            for (FixtureBundleSnapshotEntry snapshot : manifest.getSnapshots()) {
                if (snapshot.getTables() != null)
                    payloadEntries.addAll(snapshot.getTables());
            }
            payloadEntries.sort(java.util.Comparator.comparingInt(FixtureBundleTableEntry::getLoadOrder));
            txn.runInTransaction(null, io.nop.api.core.annotations.txn.TransactionPropagation.REQUIRED, t -> {
                for (FixtureBundleTableEntry entry : payloadEntries)
                    importPayloadEntry(entry, bundleDir, orm, models, idMapping, result);
                return null;
            });
        }
        return result;
    }

    private List<FixtureBundleTableEntry> collectEntries(FixtureBundleManifest manifest) {
        List<FixtureBundleTableEntry> all = new ArrayList<>();
        if (manifest.getBaseTables() != null)
            all.addAll(manifest.getBaseTables());
        if (manifest.getSnapshots() != null) {
            for (FixtureBundleSnapshotEntry snapshot : manifest.getSnapshots()) {
                if (snapshot.getTables() != null)
                    all.addAll(snapshot.getTables());
            }
        }
        return all;
    }

    static boolean isSysTable(String entityName) {
        return entityName.startsWith("io.nop.sys.") || entityName.startsWith("nop_sys_");
    }

    private Predicate<String> defaultRequiresResolver(File bundleDir) {
        return name -> {
            File sibling = new File(bundleDir.getParentFile(), name + "/" + FixtureBundleConstants.MANIFEST_FILE);
            return sibling.exists();
        };
    }

    private void importBaseEntry(FixtureBundleTableEntry entry, File bundleDir, IOrmTemplate orm,
                                 IEntityModelResolver models, Map<String, Map<String, Object>> idMapping,
                                 FixtureBundleImportResult result) {
        IEntityModel model = requireModel(models, entry.getTable());
        if (model.getPkColumns().size() > 1)
            throw new NopException(ERR_FIXTURE_BUNDLE_COMPOSITE_PK_PAYLOAD).param(ARG_TABLE_NAME, entry.getTable());

        List<Map<String, Object>> rows = readRows(bundleDir, entry);
        Map<String, IColumnModel> byCode = columnMap(model);
        Map<String, Object> mapping = idMapping.computeIfAbsent(entry.getTable(), k -> new LinkedHashMap<>());

        // duplicate business keys WITHIN one package would be silently merged by the
        // row-by-row reconciliation (each insert is visible to the next row's lookup) —
        // that is a package defect, rejected here instead
        Set<String> seenBusinessKeys = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String businessKey = entry.getBusinessKeys().stream()
                    .map(k -> String.valueOf(row.get(k))).collect(java.util.stream.Collectors.joining("\u0001"));
            if (!seenBusinessKeys.add(businessKey))
                throw new NopException(ERR_FIXTURE_BUNDLE_UK_CONFLICT).param(ARG_TABLE_NAME, entry.getTable());
        }

        for (Map<String, Object> row : rows) {
            String oldId = String.valueOf(row.get(pkCode(model)));
            // business-key reconciliation
            IOrmEntity example = orm.runInSession(session -> session.newEntity(entry.getTable()));
            for (String key : entry.getBusinessKeys()) {
                IColumnModel col = byCode.get(key);
                example.orm_propValueByName(col.getName(), row.get(key));
            }
            IOrmEntity existing = orm.runInSession(session -> session.findFirstByExample(example));
            if (existing != null) {
                mapping.put(oldId, existing.orm_propValue(model.getPkColumns().get(0).getPropId()));
                result.incrBaseSkipped();
                continue;
            }

            IOrmEntity entity = orm.runInSession(session -> session.newEntity(entry.getTable()));
            for (Map.Entry<String, Object> en : row.entrySet()) {
                IColumnModel col = byCode.get(en.getKey());
                if (col == null || col.isPrimary())
                    continue; // unknown columns stripped; PK left for platform generation
                entity.orm_propValueByName(col.getName(), en.getValue());
            }
            Object newId;
            try {
                newId = orm.runInSession(session -> session.save(entity));
            } catch (RuntimeException e) {
                // business-key UK conflict outside the reconciliation window must surface
                // as a named error, not a raw SQL exception
                throw new NopException(ERR_FIXTURE_BUNDLE_UK_CONFLICT).param(ARG_TABLE_NAME, entry.getTable())
                        .cause(e);
            }
            mapping.put(oldId, newId);
            result.recordMaxNewId(entry.getTable(), newId);
            result.incrBaseImported();
        }
    }

    private void importPayloadEntry(FixtureBundleTableEntry entry, File bundleDir, IOrmTemplate orm,
                                    IEntityModelResolver models, Map<String, Map<String, Object>> idMapping,
                                    FixtureBundleImportResult result) {
        IEntityModel model = requireModel(models, entry.getTable());
        if (model.getPkColumns().size() > 1)
            throw new NopException(ERR_FIXTURE_BUNDLE_COMPOSITE_PK_PAYLOAD).param(ARG_TABLE_NAME, entry.getTable());

        List<Map<String, Object>> rows = readRows(bundleDir, entry);
        Map<String, IColumnModel> byCode = columnMap(model);
        Map<String, Object> mapping = idMapping.computeIfAbsent(entry.getTable(), k -> new LinkedHashMap<>());

        // to-one FK columns: single-join-column relations of this entity
        Map<String, String> fkTargetByCode = new LinkedHashMap<>();
        for (IEntityRelationModel rel : model.getRelations()) {
            IColumnModel joinCol = rel.getSingleJoinColumn();
            if (joinCol != null)
                fkTargetByCode.put(joinCol.getCode(), rel.getRefEntityName());
        }

        for (Map<String, Object> row : rows) {
            String oldId = String.valueOf(row.get(pkCode(model)));
            String payloadKey = entry.getTable() + "#" + oldId;
            if (!importedPayloadKeys.add(payloadKey)) {
                result.incrPayloadSkipped();
                continue;
            }

            IOrmEntity entity = orm.runInSession(session -> session.newEntity(entry.getTable()));
            Map<String, Object> normalized = normalizeRow(byCode, row);
            for (Map.Entry<String, Object> en : normalized.entrySet()) {
                String code = en.getKey();
                Object value = en.getValue();
                String fkTarget = fkTargetByCode.get(code);
                if (fkTarget != null && value != null) {
                    value = resolveRef(String.valueOf(value), fkTarget, idMapping, orm, entry.getTable(), code);
                }
                entity.orm_propValueByName(byCode.get(code).getName(), value);
            }
            Object newId = orm.runInSession(session -> session.save(entity));
            mapping.put(oldId, newId);
            result.recordMaxNewId(entry.getTable(), newId);
            result.incrPayloadImported();
        }
    }

    /**
     * Import row normalization (approval condition B1 assertion target): non-model
     * columns — including packaged VERSION/DEL_VERSION of richer target models — are
     * stripped (never carried from the package); model-owned version columns are
     * forced to initial value 0; the PK column is dropped (platform generates).
     */
    static Map<String, Object> normalizeRow(Map<String, IColumnModel> byCode, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> en : row.entrySet()) {
            IColumnModel col = byCode.get(en.getKey());
            if (col == null || col.isPrimary())
                continue;
            out.put(en.getKey(), VERSION_CODES.contains(en.getKey()) ? 0 : en.getValue());
        }
        return out;
    }

    private Object resolveRef(String rawValue, String fkTarget, Map<String, Map<String, Object>> idMapping,
                              IOrmTemplate orm, String table, String column) {
        Map<String, Object> targetMapping = idMapping.get(fkTarget);
        if (targetMapping != null && targetMapping.containsKey(rawValue))
            return targetMapping.get(rawValue);
        // not in the package mapping: must already exist in the target environment
        Object existing = orm.runInSession(session -> session.get(fkTarget, rawValue));
        if (existing == null)
            throw new NopException(ERR_FIXTURE_BUNDLE_DANGLING_REF).param(ARG_TABLE_NAME, table)
                    .param(ARG_COLUMN_NAME, column).param(ARG_ID, rawValue);
        return rawValue;
    }

    private List<Map<String, Object>> readRows(File bundleDir, FixtureBundleTableEntry entry) {
        File csv = new File(bundleDir, entry.getCsv());
        return CsvHelper.readCsv(new FileResource(csv));
    }

    private IEntityModel requireModel(IEntityModelResolver models, String name) {
        IEntityModel model = models.resolve(name);
        if (model == null)
            throw new NopException(ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT).param(ARG_TABLE_NAME, name);
        return model;
    }

    private Map<String, IColumnModel> columnMap(IEntityModel model) {
        Map<String, IColumnModel> byCode = new LinkedHashMap<>();
        for (IColumnModel col : model.getColumns())
            byCode.put(col.getCode(), col);
        return byCode;
    }

    private String pkCode(IEntityModel model) {
        return model.getPkColumns().get(0).getCode();
    }

    private interface IEntityModelResolver {
        IEntityModel resolve(String entityName);
    }
}
