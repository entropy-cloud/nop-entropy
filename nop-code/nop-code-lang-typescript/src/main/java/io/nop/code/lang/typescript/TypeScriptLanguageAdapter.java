package io.nop.code.lang.typescript;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;

import java.util.Arrays;
import java.util.List;

public class TypeScriptLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.TYPESCRIPT;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new TypeScriptCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".ts", ".tsx");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/node_modules/**",
                "**/dist/**",
                "**/build/**",
                "**/.git/**"
        );
    }
}
