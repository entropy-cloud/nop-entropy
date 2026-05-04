package io.nop.code.lang.python;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;

import java.util.Arrays;
import java.util.List;

public class PythonLanguageAdapter implements ILanguageAdapter {

    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.PYTHON;
    }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() {
        return new PythonCodeFileAnalyzer();
    }

    @Override
    public List<String> getFileExtensions() {
        return Arrays.asList(".py");
    }

    @Override
    public List<String> getExcludePatterns() {
        return Arrays.asList(
                "**/__pycache__/**",
                "**/venv/**",
                "**/.venv/**"
        );
    }
}
