package io.nop.code.core.model;

/**
 * 编程语言枚举
 */
public enum CodeLanguage {
    JAVA("java", ".java"),
    PYTHON("python", ".py"),
    TYPESCRIPT("typescript", ".ts", ".tsx"),
    JAVASCRIPT("javascript", ".js", ".jsx");

    private final String code;
    private final String[] extensions;

    CodeLanguage(String code, String... extensions) {
        this.code = code;
        this.extensions = extensions;
    }

    public String getCode() {
        return code;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public boolean hasExtension(String ext) {
        for (String extension : extensions) {
            if (extension.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    public static CodeLanguage fromExtension(String ext) {
        for (CodeLanguage lang : values()) {
            if (lang.hasExtension(ext)) {
                return lang;
            }
        }
        return null;
    }
}
