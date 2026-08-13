package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.dao.entity.NopCodeUsage;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * WP-4 AR-30/66/149/150: deleting a file must not leave CROSS-FILE orphans — calls in other files
 * that call the deleted symbols, inheritance rows where the deleted symbols are the supertype, and
 * usages in other files that reference the deleted symbols. The ORM cascadeDelete on
 * NopCodeSymbol.callers/callees/superTypes/subTypes/usages only fires through ORM navigation; the
 * bulk delete path must mirror it explicitly.
 *
 * <p>To isolate the delete-path logic from analyzer representation quirks, this test seeds
 * controlled cross-file reference rows and asserts they are purged when file A is deleted.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestDeletePathIntegrity extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    private static String absoluteVfsPath(String relPath) {
        String abs = new java.io.File(relPath).getAbsolutePath().replace('\\', '/');
        if (abs.length() >= 2 && abs.charAt(1) == ':') {
            abs = "/" + abs;
        }
        return abs;
    }

    private String filePathOf(String fragment) {
        IEntityDao<NopCodeFile> dao = daoProvider.daoFor(NopCodeFile.class);
        return dao.findAll().stream()
                .map(NopCodeFile::getFilePath)
                .filter(p -> p.contains(fragment))
                .findFirst().orElseThrow(() -> new AssertionError("No file matching " + fragment));
    }

    private String fileIdOf(String fragment) {
        IEntityDao<NopCodeFile> dao = daoProvider.daoFor(NopCodeFile.class);
        return dao.findAll().stream()
                .filter(f -> f.getFilePath().contains(fragment))
                .map(NopCodeFile::getId)
                .findFirst().orElseThrow(() -> new AssertionError("No file matching " + fragment));
    }

    private List<String> symbolIdsForFile(String filePathFragment) {
        IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean q = new QueryBean();
        q.addFilter(eq("filePath", filePathOf(filePathFragment)));
        return dao.findAllByQuery(q).stream().map(NopCodeSymbol::getId).collect(Collectors.toList());
    }

    private long countByIn(Class<?> entityClass, String field, List<String> ids) {
        if (ids.isEmpty()) return 0;
        IEntityDao<?> dao = daoProvider.dao(entityClass.getName());
        QueryBean q = new QueryBean();
        q.addFilter(in(field, ids));
        return dao.findAllByQuery(q).size();
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void testDeleteFileClearsCrossFileOrphans() {
        String indexId = "delete-integrity";
        int count = codeIndexService.indexDirectory(indexId,
                "file:" + absoluteVfsPath("src/test/resources/test-project/src/main/java"), "**/*.java");
        assertFalse(count < 6, "Test project should index at least 6 files, got " + count);

        List<String> symbolsA = symbolIdsForFile("BaseEntity");
        assertFalse(symbolsA.isEmpty(), "BaseEntity should have symbols");
        String fileAPath = filePathOf("BaseEntity");
        String symbolA = symbolsA.get(0);
        String fileIdB = fileIdOf("User");

        // Seed controlled cross-file references located in file B but pointing at A's symbol.
        IEntityDao<NopCodeCall> callDao = daoProvider.daoFor(NopCodeCall.class);
        NopCodeCall xCall = callDao.newEntity();
        xCall.setId(newId());
        xCall.setIndexId(indexId);
        xCall.setCallerId(symbolA);
        xCall.setCalleeId(symbolA);
        xCall.setFileId(fileIdB);
        xCall.setLine(1);
        callDao.saveEntity(xCall);
        callDao.flushSession();

        IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
        NopCodeInheritance xInh = inhDao.newEntity();
        xInh.setId(newId());
        xInh.setIndexId(indexId);
        xInh.setSubTypeId(symbolA);
        xInh.setSuperTypeId(symbolA);
        xInh.setRelationType("EXTENDS");
        inhDao.saveEntity(xInh);
        inhDao.flushSession();

        IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
        NopCodeUsage xUsage = usageDao.newEntity();
        xUsage.setId(newId());
        xUsage.setIndexId(indexId);
        xUsage.setSymbolId(symbolA);
        xUsage.setFileId(fileIdB);
        xUsage.setKind("READ");
        xUsage.setLine(1);
        usageDao.saveEntity(xUsage);
        usageDao.flushSession();

        assertEquals(1, countByIn(NopCodeCall.class, "calleeId", List.of(symbolA)),
                "seeded cross-file call must exist before delete");
        assertEquals(1, countByIn(NopCodeInheritance.class, "superTypeId", List.of(symbolA)),
                "seeded cross-file inheritance must exist before delete");
        assertEquals(1, countByIn(NopCodeUsage.class, "symbolId", List.of(symbolA)),
                "seeded cross-file usage must exist before delete");

        // Delete file A — exercises deleteFileRecords cross-file cleanup.
        codeIndexService.batchDeleteFileRecords(indexId, List.of(fileAPath));

        assertEquals(0, countByIn(NopCodeCall.class, "calleeId", List.of(symbolA)),
                "cross-file call referencing deleted symbol must be purged");
        assertEquals(0, countByIn(NopCodeCall.class, "callerId", List.of(symbolA)),
                "cross-file call (as caller) referencing deleted symbol must be purged");
        assertEquals(0, countByIn(NopCodeInheritance.class, "superTypeId", List.of(symbolA)),
                "inheritance row with deleted symbol as supertype must be purged");
        assertEquals(0, countByIn(NopCodeInheritance.class, "subTypeId", List.of(symbolA)),
                "inheritance row with deleted symbol as subtype must be purged");
        assertEquals(0, countByIn(NopCodeUsage.class, "symbolId", List.of(symbolA)),
                "cross-file usage referencing deleted symbol must be purged");
    }
}
