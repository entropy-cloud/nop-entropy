package io.nop.code.lang.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
import io.nop.code.core.resolver.IImportResolver;
public class JavaImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "JAVA";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String qualifiedName = extractImportedName(imp);
            if (qualifiedName == null) continue;

            String candidatePath = "src/main/java/" + qualifiedName.replace('.', '/') + ".java";
            boolean resolved = projectFiles.contains(candidatePath);

            CodeFileDependency dep = new CodeFileDependency();
            dep.setSourceFilePath(sourceFilePath);
            dep.setTargetFilePath(resolved ? candidatePath : null);
            dep.setImportStatement(imp);
            dep.setResolved(resolved);
            deps.add(dep);
        }
        return deps;
    }

    private String extractImportedName(String importStmt) {
        String trimmed = importStmt.trim();
        if (trimmed.startsWith("import ")) {
            trimmed = trimmed.substring(7).trim();
        }
        if (trimmed.startsWith("static ")) {
            trimmed = trimmed.substring(7).trim();
        }
        if (importStmt.contains("static ")) {
            int lastDot = trimmed.lastIndexOf('.');
            if (lastDot > 0) {
                trimmed = trimmed.substring(0, lastDot);
            }
        }
        if (trimmed.endsWith(".*")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        trimmed = trimmed.replace(";", "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
