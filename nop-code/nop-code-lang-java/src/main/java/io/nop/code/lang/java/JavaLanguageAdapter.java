package io.nop.code.lang.java;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;

import java.util.Arrays;
import java.util.List;

/**
 * Java language adapter for the multi-language code index
 */
public class JavaLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.JAVA;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new JavaCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".java");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/target/**",
                "**/build/**",
                "**/.git/**",
                "**/node_modules/**",
                "**/.idea/**"
        );
    }
}
