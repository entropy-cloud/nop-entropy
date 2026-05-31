package io.nop.code.core.analyzer;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeLanguage;
public interface IProjectAnalyzer {
    ProjectAnalysisResult analyzeProject(Path projectRoot);

    ProjectAnalysisResult analyzeProject(Path projectRoot, Set<CodeLanguage> languages);

    ProjectAnalysisResult analyzeIncremental(Path projectRoot, List<String> changedFilePaths);
}
