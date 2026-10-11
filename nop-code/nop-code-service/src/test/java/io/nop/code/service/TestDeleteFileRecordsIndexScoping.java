package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeDependency;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-6.7-011 (plan 2401): {@code deleteFileRecords} must scope the
 * NopCodeDependency delete by indexId. Two indexes may cover the same
 * repository/files; the old unscoped delete removed the other index's edges.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestDeleteFileRecordsIndexScoping extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Test
    void dependencyDeleteIsScopedToTheTargetIndex() {
        IEntityDao<NopCodeDependency> depDao = daoProvider.daoFor(NopCodeDependency.class);
        String sharedFilePath = "demo/Shared.java";

        NopCodeDependency depA = depDao.newEntity();
        depA.setId("dep-idx-a");
        depA.setIndexId("idx-a");
        depA.setSourceFilePath(sharedFilePath);
        depA.setTargetFilePath("demo/X.java");

        NopCodeDependency depB = depDao.newEntity();
        depB.setId("dep-idx-b");
        depB.setIndexId("idx-b");
        depB.setSourceFilePath(sharedFilePath);
        depB.setTargetFilePath("demo/Y.java");

        depDao.saveEntity(depA);
        depDao.saveEntity(depB);
        depDao.flushSession();

        codeIndexService.batchDeleteFileRecords("idx-a", List.of(sharedFilePath));

        assertNull(depDao.getEntityById("dep-idx-a"), "index A's dependency row must be deleted");
        assertNotNull(depDao.getEntityById("dep-idx-b"),
                "index B's dependency row must survive (indexId scoping)");
    }
}
