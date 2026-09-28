package io.nop.code.lang.csharp;

import java.util.Arrays;
import java.util.List;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;

public class CSharpLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.CSHARP;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new CSharpCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".cs");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/bin/**",
                "**/obj/**",
                "**/.git/**"
        );
    }
}
