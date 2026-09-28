package io.nop.code.lang.go;

import java.util.Arrays;
import java.util.List;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;

public class GoLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.GO;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new GoCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".go");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/vendor/**",
                "**/.git/**"
        );
    }
}
