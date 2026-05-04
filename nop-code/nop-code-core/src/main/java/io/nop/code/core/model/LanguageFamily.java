package io.nop.code.core.model;

/**
 * Language family for cross-language call detection
 */
public enum LanguageFamily {
    JVM,        // Java, Kotlin, Scala
    JS_FAMILY,  // TypeScript, JavaScript
    PYTHON,     // Python
    UNKNOWN;    // Unknown

    public static LanguageFamily fromLanguage(CodeLanguage language) {
        if (language == null) return UNKNOWN;
        switch (language) {
            case JAVA: return JVM;
            case TYPESCRIPT:
            case JAVASCRIPT: return JS_FAMILY;
            case PYTHON: return PYTHON;
            default: return UNKNOWN;
        }
    }

    public boolean isSameFamily(LanguageFamily other) {
        return this == other && this != UNKNOWN;
    }
}
