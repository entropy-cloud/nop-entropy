package io.nop.code.core.analyzer;

import io.nop.code.core.model.CodeLanguage;

import java.util.List;

/**
 * 语言适配器接口
 */
public interface ILanguageAdapter {
    CodeLanguage getLanguage();

    ICodeFileAnalyzer getFileAnalyzer();

    List<String> getFileExtensions();

    List<String> getExcludePatterns();
}
