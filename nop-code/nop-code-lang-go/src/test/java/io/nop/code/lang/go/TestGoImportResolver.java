package io.nop.code.lang.go;

import io.nop.code.core.model.CodeFileDependency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGoImportResolver {

    @Test
    void languageKeyIsGo() {
        assertEquals("GO", new GoImportResolver().getLanguage());
    }

    @Test
    void quotedImportPathExtractedAndResolved() {
        GoImportResolver resolver = new GoImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "demo/animal.go",
                List.of("import \"demo/base\""),
                Set.of("demo/base/base.go", "demo/animal.go"));

        assertEquals(1, deps.size());
        CodeFileDependency dep = deps.get(0);
        assertEquals("demo/base", dep.getTargetFilePath());
        assertTrue(dep.isResolved());
    }

    @Test
    void barePathAccepted() {
        GoImportResolver resolver = new GoImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "demo/animal.go",
                List.of("fmt"),
                Set.of("demo/animal.go"));
        assertEquals(1, deps.size());
        assertFalse(deps.get(0).isResolved(), "stdlib path must not resolve inside the project");
    }

    @Test
    void unmatchedPathUnresolved() {
        GoImportResolver resolver = new GoImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "demo/animal.go",
                List.of("import \"github.com/x/y\""),
                Set.of("demo/animal.go"));
        assertEquals(1, deps.size());
        assertFalse(deps.get(0).isResolved());
    }
}
