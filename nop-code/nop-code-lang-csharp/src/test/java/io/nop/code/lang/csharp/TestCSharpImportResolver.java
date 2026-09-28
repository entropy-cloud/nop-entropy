package io.nop.code.lang.csharp;

import io.nop.code.core.model.CodeFileDependency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCSharpImportResolver {

    @Test
    void languageKeyIsCsharp() {
        assertEquals("CSHARP", new CSharpImportResolver().getLanguage());
    }

    @Test
    void usingStatementResolvedByNamespaceSegments() {
        CSharpImportResolver resolver = new CSharpImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "Animals/Animal.cs",
                List.of("using Demo.Animals.Base;"),
                Set.of("Demo/Animals/Base/Base.cs", "Animals/Animal.cs"));
        assertEquals(1, deps.size());
        assertTrue(deps.get(0).isResolved(), "namespace segments must match project dirs");
        assertEquals("Demo/Animals/Base", deps.get(0).getTargetFilePath());
    }

    @Test
    void externalNamespaceUnresolved() {
        CSharpImportResolver resolver = new CSharpImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "Animals/Animal.cs",
                List.of("using System;"),
                Set.of("Animals/Animal.cs"));
        assertEquals(1, deps.size());
        assertFalse(deps.get(0).isResolved());
    }
}
