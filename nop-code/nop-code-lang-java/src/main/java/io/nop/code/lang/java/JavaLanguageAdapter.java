package io.nop.code.lang.java;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;

import java.util.Arrays;
import java.util.List;

public class JavaLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.JAVA;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new JavaFileAnalyzer();
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
