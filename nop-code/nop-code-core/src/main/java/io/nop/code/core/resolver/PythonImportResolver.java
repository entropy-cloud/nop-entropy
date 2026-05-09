package io.nop.code.core.resolver;

import io.nop.code.core.model.CodeFileDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PythonImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "PYTHON";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String moduleName = extractModuleName(imp);
            if (moduleName == null) continue;

            String modulePath = moduleName.replace('.', '/') + ".py";
            String packagePath = moduleName.replace('.', '/') + "/__init__.py";

            String resolvedPath = null;
            if (projectFiles.contains(modulePath)) {
                resolvedPath = modulePath;
            } else if (projectFiles.contains(packagePath)) {
                resolvedPath = packagePath;
            }

            CodeFileDependency dep = new CodeFileDependency();
            dep.setSourceFilePath(sourceFilePath);
            dep.setTargetFilePath(resolvedPath);
            dep.setImportStatement(imp);
            dep.setResolved(resolvedPath != null);
            deps.add(dep);
        }
        return deps;
    }

    private String extractModuleName(String importStmt) {
        String trimmed = importStmt.trim();
        if (trimmed.startsWith("import ")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("from ")) {
            trimmed = trimmed.substring(5).trim();
            int spaceIdx = trimmed.indexOf(' ');
            if (spaceIdx > 0) {
                trimmed = trimmed.substring(0, spaceIdx);
            }
        }
        int asIdx = trimmed.indexOf(" as ");
        if (asIdx > 0) {
            trimmed = trimmed.substring(0, asIdx);
        }
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            trimmed = trimmed.substring(0, commaIdx);
        }
        trimmed = trimmed.trim();
        while (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
