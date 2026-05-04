package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLanguageFamily {

    @Test
    void testFromLanguageJava() {
        assertEquals(LanguageFamily.JVM, LanguageFamily.fromLanguage(CodeLanguage.JAVA));
    }

    @Test
    void testFromLanguageTypescript() {
        assertEquals(LanguageFamily.JS_FAMILY, LanguageFamily.fromLanguage(CodeLanguage.TYPESCRIPT));
    }

    @Test
    void testFromLanguagePython() {
        assertEquals(LanguageFamily.PYTHON, LanguageFamily.fromLanguage(CodeLanguage.PYTHON));
    }

    @Test
    void testFromLanguageNull() {
        assertEquals(LanguageFamily.UNKNOWN, LanguageFamily.fromLanguage(null));
    }

    @Test
    void testFromLanguageJavascript() {
        assertEquals(LanguageFamily.JS_FAMILY, LanguageFamily.fromLanguage(CodeLanguage.JAVASCRIPT));
    }

    @Test
    void testIsSameFamilyTrue() {
        assertTrue(LanguageFamily.JVM.isSameFamily(LanguageFamily.JVM));
    }

    @Test
    void testIsSameFamilyDifferent() {
        assertFalse(LanguageFamily.JVM.isSameFamily(LanguageFamily.PYTHON));
    }

    @Test
    void testIsSameFamilyUnknown() {
        assertFalse(LanguageFamily.UNKNOWN.isSameFamily(LanguageFamily.UNKNOWN));
    }

    @Test
    void testIsSameFamilyNull() {
        assertFalse(LanguageFamily.PYTHON.isSameFamily(null));
    }
}
