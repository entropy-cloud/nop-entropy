package io.nop.code.core.analyzer;

import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeLanguage;
public interface IProjectAnalyzer {
    ProjectAnalysisResult analyzeProject(String projectRoot);

    ProjectAnalysisResult analyzeProject(String projectRoot, Set<CodeLanguage> languages);

    ProjectAnalysisResult analyzeIncremental(String projectRoot, List<String> changedFilePaths);
}
