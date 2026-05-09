package io.nop.code.core.resolver;

import io.nop.code.core.model.CodeFileDependency;

import java.util.List;
import java.util.Set;

public interface IImportResolver {
    String getLanguage();

    List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                            Set<String> projectFiles);
}
