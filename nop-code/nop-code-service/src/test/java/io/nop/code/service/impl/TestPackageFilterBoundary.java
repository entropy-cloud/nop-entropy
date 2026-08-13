package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-8 AR-160/162/165: package filtering must respect package boundaries. Querying package
 * "com.example" must NOT return symbols of the sibling package "com.example*" (e.g. a class whose
 * qualified name starts with "com.example" but is actually in "com.exampleOther").
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestPackageFilterBoundary extends JunitAutoTestCase {

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
    void packageFilterExcludesSiblingPackages() {
        String indexId = "pkg-boundary";
        codeIndexService.indexDirectory(indexId,
                "file:" + absoluteVfsPath("src/test/resources/test-project/src/main/java"), "**/*.java");

        CodeQueryService queryService = new CodeQueryService(daoProvider, new CodeCacheManager(), ormTemplate);

        // The test project has package "com.example.domain". Querying it must return its symbols…
        List<CodeSymbol> domain = queryService.findSymbols(indexId, null, null, "com.example.domain", 0);
        assertFalse(domain.isEmpty(), "com.example.domain symbols should be found");
        assertTrue(domain.stream().allMatch(s -> s.getQualifiedName().startsWith("com.example.domain.")),
                "all results must belong to com.example.domain");

        // …and querying the non-existent sibling "com.exampleBad" must return nothing, even though
        // every QN starts with the raw prefix "com.example".
        List<CodeSymbol> sibling = queryService.findSymbols(indexId, null, null, "com.exampleBad", 0);
        assertTrue(sibling.isEmpty(),
                "sibling package com.exampleBad must not match com.example.* (boundary respected)");
    }
}
