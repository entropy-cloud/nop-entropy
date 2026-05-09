package io.nop.code.core.analyzer;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeLanguage;

import java.util.List;

/**
 * 代码文件分析器接口
 */
public interface ICodeFileAnalyzer {
    CodeLanguage getLanguage();

    CodeFileAnalysisResult analyze(String filePath, String sourceCode);

    List<String> getFileExtensions();

    default int countLines(String source) {
        if (source == null || source.isBlank()) {
            return 0;
        }
        return source.split("\r?\n").length;
    }
}
