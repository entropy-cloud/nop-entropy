package io.nop.code.service.impl;

import io.nop.code.api.dto.CodeSearchResultDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeSearchServiceGlobFilter {

    @SuppressWarnings("unchecked")
    private List<CodeSearchResultDTO> invokeFilter(CodeSearchService service, List<CodeSearchResultDTO> results, String pattern) throws Exception {
        Method m = CodeSearchService.class.getDeclaredMethod("filterByFilePattern", List.class, String.class);
        m.setAccessible(true);
        return (List<CodeSearchResultDTO>) m.invoke(service, results, pattern);
    }

    @Test
    void testGlobFilter_matchesJavaFiles() throws Exception {
        CodeSearchService service = new CodeSearchService(null, null, null);

        CodeSearchResultDTO r1 = new CodeSearchResultDTO();
        r1.setFilePath("src/main/Foo.java");
        CodeSearchResultDTO r2 = new CodeSearchResultDTO();
        r2.setFilePath("src/main/Bar.py");
        CodeSearchResultDTO r3 = new CodeSearchResultDTO();
        r3.setFilePath("src/test/BazTest.java");

        List<CodeSearchResultDTO> filtered = invokeFilter(service, List.of(r1, r2, r3), "*.java");
        assertEquals(2, filtered.size(), "*.java should match Foo.java and BazTest.java");
        assertTrue(filtered.stream().allMatch(r -> r.getFilePath().endsWith(".java")));
    }

    @Test
    void testGlobFilter_exactExtensionNotPartial() throws Exception {
        CodeSearchService service = new CodeSearchService(null, null, null);

        CodeSearchResultDTO r1 = new CodeSearchResultDTO();
        r1.setFilePath("Foo.java");
        CodeSearchResultDTO r2 = new CodeSearchResultDTO();
        r2.setFilePath("Bar.javac");

        List<CodeSearchResultDTO> filtered = invokeFilter(service, List.of(r1, r2), "*.java");
        assertEquals(1, filtered.size());
        assertEquals("Foo.java", filtered.get(0).getFilePath());
    }

    @Test
    void testGlobFilter_nullPattern_returnsAll() throws Exception {
        CodeSearchService service = new CodeSearchService(null, null, null);

        CodeSearchResultDTO r1 = new CodeSearchResultDTO();
        r1.setFilePath("Foo.java");
        List<CodeSearchResultDTO> results = List.of(r1);

        assertEquals(1, invokeFilter(service, results, null).size());
        assertEquals(1, invokeFilter(service, results, "").size());
    }
}
