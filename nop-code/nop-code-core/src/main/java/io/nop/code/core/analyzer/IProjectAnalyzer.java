package io.nop.code.core.analyzer;

import io.nop.code.core.model.CodeLanguage;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface IProjectAnalyzer {
    ProjectAnalysisResult analyzeProject(Path projectRoot);

    ProjectAnalysisResult analyzeProject(Path projectRoot, Set<CodeLanguage> languages);

    ProjectAnalysisResult analyzeIncremental(Path projectRoot, List<String> changedFilePaths);
}
