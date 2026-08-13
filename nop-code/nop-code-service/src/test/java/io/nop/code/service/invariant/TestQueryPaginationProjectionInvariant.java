package io.nop.code.service.invariant;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.service.incremental.OrmFingerprintStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused verification that paginated read/delete paths exhaust ALL rows
 * (INV-04 / INV-01 fixes): no silent truncation when result set exceeds batch size.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestQueryPaginationProjectionInvariant extends JunitAutoTestCase {

    private static final int ROW_COUNT = 1250; // exceeds BATCH_SIZE=1000
    private static final String INDEX_ID = "pagination-invariant";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private OrmFingerprintStore store;

    private void seedFiles() throws Exception {
        store = new OrmFingerprintStore(daoProvider, ormTemplate);
        ormTemplate.runInSession(session -> {
            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            // Clean slate
            QueryBean clear = new QueryBean();
            clear.addFilter(FilterBeans.eq("indexId", INDEX_ID));
            fileDao.deleteByQuery(clear);
            return null;
        });

        List<FileFingerprint> fingerprints = new ArrayList<>();
        for (int i = 0; i < ROW_COUNT; i++) {
            String path = "dir/file" + i + ".java";
            fingerprints.add(new FileFingerprint(path, "hash" + i, (long) i * 100, (long) i * 10));
        }
        store.saveFingerprints(INDEX_ID, fingerprints);

        // sanity: DB actually has all rows
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean countQuery = new QueryBean();
        countQuery.addFilter(FilterBeans.eq("indexId", INDEX_ID));
        assertEquals(ROW_COUNT, fileDao.countByQuery(countQuery),
                "seed must insert all rows before testing pagination");
    }

    @Test
    void loadFingerprints_exhaustsAllRowsBeyondBatchSize() throws Exception {
        seedFiles();

        List<FileFingerprint> loaded = store.loadFingerprints(INDEX_ID);

        assertEquals(ROW_COUNT, loaded.size(),
                "paginated projection must return ALL rows, not truncate at batch boundary");
        // projection fields must equal consumed fields (no CLOB needed)
        FileFingerprint sample = loaded.stream()
                .filter(f -> f.getFilePath().endsWith("file0.java"))
                .findFirst().orElseThrow();
        assertEquals("hash0", sample.getContentHash());
        assertEquals(0L, sample.getLastModified());
        assertEquals(0L, sample.getFileSize());
    }

    @Test
    void deleteByIndex_removesAllRowsBeyondBatchSize() throws Exception {
        seedFiles();

        ormTemplate.runInSession(session -> {
            try {
                store.deleteByIndex(INDEX_ID);
            } catch (java.io.IOException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean after = new QueryBean();
        after.addFilter(FilterBeans.eq("indexId", INDEX_ID));
        assertEquals(0, fileDao.countByQuery(after),
                "paginated delete must remove ALL rows, leaving zero orphans");
    }

    @Test
    void deleteByIndex_doesNotDeleteOtherIndexRows() throws Exception {
        seedFiles();
        // seed a second index with one row
        ormTemplate.runInSession(session -> {
            IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
            NopCodeFile other = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
            other.setId(DigestHelper.sha256Hex(("other:other.java").getBytes(StandardCharsets.UTF_8)).substring(0, 36));
            other.setIndexId("other-index");
            other.setFilePath("other.java");
            fileDao.saveEntity(other);
            return null;
        });

        ormTemplate.runInSession(session -> {
            try {
                store.deleteByIndex(INDEX_ID);
            } catch (java.io.IOException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean otherQ = new QueryBean();
        otherQ.addFilter(FilterBeans.eq("indexId", "other-index"));
        assertEquals(1, fileDao.countByQuery(otherQ),
                "paginated delete must scope to the requested index only");
    }
}
