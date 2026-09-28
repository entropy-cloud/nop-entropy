package io.nop.code.lang.rust;

import java.util.Arrays;
import java.util.List;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;

public class RustLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.RUST;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new RustCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".rs");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/target/**",
                "**/.git/**"
        );
    }
}
