package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.dao.entity.NopCodeUsage;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AR-149/150 ORM-level cascadeDelete verification: when a NopCodeFile or NopCodeSymbol is deleted via
 * ORM navigation ({@code session.delete(entity)} + {@code session.flush()}), the declarative
 * {@code cascadeDelete="true"} on their {@code usages} to-many relation must automatically purge the
 * associated NopCodeUsage rows.
 *
 * <p>This complements {@link TestOrmRelationNavigation#testCascadeDeleteCompleteness()} which
 * verifies index-level cascade. Here we verify file-level and symbol-level cascade independently.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestCascadeDeleteOrmLevel extends JunitAutoTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long countByField(Class<?> entityClass, String field, String value) {
        IEntityDao<?> dao = daoProvider.dao(entityClass.getName());
        QueryBean q = new QueryBean();
        q.addFilter(eq(field, value));
        return dao.findAllByQuery(q).size();
    }

    /**
     * File-level cascade: deleting a NopCodeFile via ORM session must cascade-delete NopCodeUsage
     * rows whose fileId matches.
     *
     * <p>The deleted file has no symbols/calls (those relations lack cascadeDelete), so no FK
     * constraint is violated. The usage's symbolId references a symbol on a different host file.
     */
    @Test
    void testFileLevelCascadeDeleteUsages() {
        String indexId = "fcdx" + System.nanoTime();
        String fileIdToDelete = newId();
        String fileHostId = newId();
        String symbolId = newId();
        String usageId = newId();

        IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
        NopCodeIndex index = indexDao.newEntity();
        index.setId(indexId);
        index.setName(indexId);
        index.setRootPath("/test-file-cdx");
        indexDao.saveEntity(index);
        indexDao.flushSession();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        NopCodeFile fileToDelete = fileDao.newEntity();
        fileToDelete.setId(fileIdToDelete);
        fileToDelete.setIndexId(indexId);
        fileToDelete.setFilePath("/test/FileToDelete.java");
        fileDao.saveEntity(fileToDelete);

        NopCodeFile fileHost = fileDao.newEntity();
        fileHost.setId(fileHostId);
        fileHost.setIndexId(indexId);
        fileHost.setFilePath("/test/FileHost.java");
        fileDao.saveEntity(fileHost);
        fileDao.flushSession();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        NopCodeSymbol symbol = symbolDao.newEntity();
        symbol.setId(symbolId);
        symbol.setIndexId(indexId);
        symbol.setFileId(fileHostId);
        symbol.setKind("class");
        symbol.setName("HostSymbol");
        symbolDao.saveEntity(symbol);
        symbolDao.flushSession();

        IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
        NopCodeUsage usage = usageDao.newEntity();
        usage.setId(usageId);
        usage.setIndexId(indexId);
        usage.setSymbolId(symbolId);
        usage.setFileId(fileIdToDelete);
        usage.setKind("READ");
        usage.setLine(1);
        usageDao.saveEntity(usage);
        usageDao.flushSession();

        assertEquals(1, countByField(NopCodeUsage.class, "fileId", fileIdToDelete),
                "Usage should exist before file delete");

        ormTemplate.runInSession(session -> {
            NopCodeFile file = (NopCodeFile) session.get(NopCodeFile.class.getName(), fileIdToDelete);
            assertNotNull(file, "File entity should exist before delete");
            session.delete(file);
            session.flush();
            return null;
        });

        assertEquals(0, countByField(NopCodeUsage.class, "fileId", fileIdToDelete),
                "Usage should be cascade-deleted when file is deleted via ORM navigation");
    }

    /**
     * Symbol-level cascade: deleting a NopCodeSymbol via ORM session must cascade-delete
     * NopCodeUsage rows whose symbolId matches.
     *
     * <p>The deleted symbol has no children/members (those relations lack cascadeDelete), so no FK
     * constraint is violated.
     */
    @Test
    void testSymbolLevelCascadeDeleteUsages() {
        String indexId = "scdx" + System.nanoTime();
        String fileId = newId();
        String symbolIdToDelete = newId();
        String usageId = newId();

        IEntityDao<NopCodeIndex> indexDao = daoProvider.daoFor(NopCodeIndex.class);
        NopCodeIndex index = indexDao.newEntity();
        index.setId(indexId);
        index.setName(indexId);
        index.setRootPath("/test-sym-cdx");
        indexDao.saveEntity(index);
        indexDao.flushSession();

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        NopCodeFile file = fileDao.newEntity();
        file.setId(fileId);
        file.setIndexId(indexId);
        file.setFilePath("/test/SymbolCascade.java");
        fileDao.saveEntity(file);
        fileDao.flushSession();

        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        NopCodeSymbol symbol = symbolDao.newEntity();
        symbol.setId(symbolIdToDelete);
        symbol.setIndexId(indexId);
        symbol.setFileId(fileId);
        symbol.setKind("class");
        symbol.setName("ToDelete");
        symbolDao.saveEntity(symbol);
        symbolDao.flushSession();

        IEntityDao<NopCodeUsage> usageDao = daoProvider.daoFor(NopCodeUsage.class);
        NopCodeUsage usage = usageDao.newEntity();
        usage.setId(usageId);
        usage.setIndexId(indexId);
        usage.setSymbolId(symbolIdToDelete);
        usage.setFileId(fileId);
        usage.setKind("READ");
        usage.setLine(1);
        usageDao.saveEntity(usage);
        usageDao.flushSession();

        assertEquals(1, countByField(NopCodeUsage.class, "symbolId", symbolIdToDelete),
                "Usage should exist before symbol delete");

        ormTemplate.runInSession(session -> {
            NopCodeSymbol sym = (NopCodeSymbol) session.get(NopCodeSymbol.class.getName(), symbolIdToDelete);
            assertNotNull(sym, "Symbol entity should exist before delete");
            session.delete(sym);
            session.flush();
            return null;
        });

        assertEquals(0, countByField(NopCodeUsage.class, "symbolId", symbolIdToDelete),
                "Usage should be cascade-deleted when symbol is deleted via ORM navigation");
    }
}
