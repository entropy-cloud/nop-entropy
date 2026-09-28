package io.nop.code.lang.rust;

import io.nop.code.core.model.CodeFileDependency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRustImportResolver {

    @Test
    void languageKeyIsRust() {
        assertEquals("RUST", new RustImportResolver().getLanguage());
    }

    @Test
    void cratePathResolvedAgainstProjectRoot() {
        RustImportResolver resolver = new RustImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "src/animal.rs",
                List.of("use crate::utils::helper;"),
                Set.of("src/utils/helper.rs", "src/animal.rs"));
        assertEquals(1, deps.size());
        assertEquals("src/utils", deps.get(0).getTargetFilePath());
        assertTrue(deps.get(0).isResolved());
    }

    @Test
    void externalCrateUnresolved() {
        RustImportResolver resolver = new RustImportResolver();
        List<CodeFileDependency> deps = resolver.resolveImports(
                "src/animal.rs",
                List.of("use std::fmt;"),
                Set.of("src/animal.rs"));
        assertEquals(1, deps.size());
        assertFalse(deps.get(0).isResolved());
    }
}
