package io.nop.code.core.analyzer;

import io.nop.code.core.model.CodeLanguage;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 项目分析器接口
 */
public interface IProjectAnalyzer {
    Object analyzeProject(Path projectRoot);

    Object analyzeProject(Path projectRoot, Set<CodeLanguage> languages);

    Object analyzeIncremental(Path projectRoot, List<String> changedFilePaths);
}
