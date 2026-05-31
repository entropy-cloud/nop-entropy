package io.nop.code.core.resolver;

import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
public interface IImportResolver {
    String getLanguage();

    List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                            Set<String> projectFiles);
}
