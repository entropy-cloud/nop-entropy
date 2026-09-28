package io.nop.code.service.impl;

import io.nop.search.api.SearchType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * N4.3（plan nop-code/24）Phase 1：引擎路径 searchType → SearchType 映射的全值域断言。
 */
class TestCodeSearchTypeMapping {

    @Test
    void engineValuesMapDirectly() {
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("TEXT"));
        assertEquals(SearchType.VECTOR, CodeSearchService.mapSearchType("VECTOR"));
        assertEquals(SearchType.HYBRID, CodeSearchService.mapSearchType("HYBRID"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(SearchType.VECTOR, CodeSearchService.mapSearchType("vector"));
        assertEquals(SearchType.HYBRID, CodeSearchService.mapSearchType("Hybrid"));
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("text"));
    }

    @Test
    void nullAndLegacyValuesNormalizeToText() {
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType(null));
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("SYMBOL_NAME"));
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("FULL_TEXT"));
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("COMBINED"));
        assertEquals(SearchType.TEXT, CodeSearchService.mapSearchType("UNKNOWN_VALUE"));
    }
}
