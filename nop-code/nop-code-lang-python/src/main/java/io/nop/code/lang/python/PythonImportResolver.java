package io.nop.code.lang.python;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
import io.nop.code.core.resolver.IImportResolver;
public class PythonImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "PYTHON";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        String sourcePackage = extractPackageFromPath(sourceFilePath);
        for (String imp : imports) {
            ParsedImport parsed = parseImportStatement(imp);
            if (parsed.moduleName == null) continue;

            String moduleName = parsed.moduleName;
            if (parsed.relativeLevel > 0) {
                moduleName = resolveRelativeModule(sourcePackage, moduleName, parsed.relativeLevel);
                if (moduleName == null) continue;
            }

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

    private String extractPackageFromPath(String filePath) {
        if (filePath == null) return "";
        String path = filePath.replace('\\', '/');
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) return "";
        return path.substring(0, lastSlash).replace('/', '.');
    }

    private String resolveRelativeModule(String sourcePackage, String moduleSuffix, int level) {
        if (sourcePackage == null || sourcePackage.isEmpty()) {
            if (level > 1) return null;
            return moduleSuffix;
        }
        String[] parts = sourcePackage.split("\\.");
        int parentLevel = level - 1;
        if (parentLevel > parts.length) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - parentLevel; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(parts[i]);
        }
        if (moduleSuffix != null && !moduleSuffix.isEmpty()) {
            if (sb.length() > 0) sb.append('.');
            sb.append(moduleSuffix);
        }
        return sb.toString();
    }

    private ParsedImport parseImportStatement(String importStmt) {
        ParsedImport result = new ParsedImport();
        String trimmed = importStmt.trim();
        if (trimmed.startsWith("import ")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("from ")) {
            trimmed = trimmed.substring(5).trim();
            int relativeLevel = 0;
            while (trimmed.startsWith(".")) {
                relativeLevel++;
                trimmed = trimmed.substring(1);
            }
            result.relativeLevel = relativeLevel;
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
        result.moduleName = trimmed.trim().isEmpty() ? null : trimmed.trim();
        return result;
    }

    private static class ParsedImport {
        String moduleName;
        int relativeLevel;
    }
}
