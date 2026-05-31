package io.nop.code.core.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
public class TypeScriptImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "TYPESCRIPT";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String moduleSpecifier = extractModuleSpecifier(imp);
            if (moduleSpecifier == null) continue;

            String resolvedPath = null;
            if (moduleSpecifier.startsWith("./") || moduleSpecifier.startsWith("../")) {
                resolvedPath = resolveRelativePath(sourceFilePath, moduleSpecifier, projectFiles);
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

    private String extractModuleSpecifier(String importStmt) {
        String trimmed = importStmt.trim();
        int singleQuote = trimmed.indexOf('\'');
        int doubleQuote = trimmed.indexOf('"');
        int firstQuote = -1;
        if (singleQuote >= 0 && doubleQuote >= 0) {
            firstQuote = Math.min(singleQuote, doubleQuote);
        } else if (singleQuote >= 0) {
            firstQuote = singleQuote;
        } else if (doubleQuote >= 0) {
            firstQuote = doubleQuote;
        }
        if (firstQuote < 0) return null;

        char quoteChar = trimmed.charAt(firstQuote);
        int endQuote = trimmed.indexOf(quoteChar, firstQuote + 1);
        if (endQuote < 0) return null;

        return trimmed.substring(firstQuote + 1, endQuote);
    }

    private String resolveRelativePath(String sourceFilePath, String moduleSpecifier,
                                       Set<String> projectFiles) {
        int lastSlash = sourceFilePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? sourceFilePath.substring(0, lastSlash) : "";

        String combined = dir.isEmpty() ? moduleSpecifier : dir + "/" + moduleSpecifier;
        String normalized = normalizePath(combined);

        String candidate = normalized + ".ts";
        if (projectFiles.contains(candidate)) return candidate;

        candidate = normalized + ".tsx";
        if (projectFiles.contains(candidate)) return candidate;

        candidate = normalized + "/index.ts";
        if (projectFiles.contains(candidate)) return candidate;

        candidate = normalized + "/index.tsx";
        if (projectFiles.contains(candidate)) return candidate;

        return null;
    }

    private String normalizePath(String path) {
        String[] parts = path.split("/");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(part);
            }
        }
        return String.join("/", result);
    }
}
