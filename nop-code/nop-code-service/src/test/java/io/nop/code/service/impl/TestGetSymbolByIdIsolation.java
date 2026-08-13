package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-7 AR-41: getSymbolById must scope by indexId so a symbol belonging to index B is never
 * returned when queried via index A (cross-index isolation). Previously it called getEntityById
 * alone, ignoring indexId.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestGetSymbolByIdIsolation extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private static String absoluteVfsPath(String relPath) {
        String abs = new java.io.File(relPath).getAbsolutePath().replace('\\', '/');
        if (abs.length() >= 2 && abs.charAt(1) == ':') {
            abs = "/" + abs;
        }
        return abs;
    }

    @Test
    void getSymbolByIdIsScopedByIndexId() {
        String indexId = "sym-isolation";
        int count = codeIndexService.indexDirectory(indexId,
                "file:" + absoluteVfsPath("src/test/resources/test-project/src/main/java"), "**/*.java");
        assertTrue(count >= 6);

        QueryBean q = new QueryBean();
        q.addFilter(eq("indexId", indexId));
        q.setLimit(1);
        NopCodeSymbol any = daoProvider.daoFor(NopCodeSymbol.class).findAllByQuery(q).get(0);
        String realSymbolId = any.getId();

        CodeQueryService queryService = new CodeQueryService(daoProvider, new CodeCacheManager(), ormTemplate);

        assertNotNull(queryService.getSymbolById(indexId, realSymbolId),
                "symbol belonging to the index must be found");
        assertNull(queryService.getSymbolById("a-different-index", realSymbolId),
                "symbol must NOT leak across indexes");
    }
}
