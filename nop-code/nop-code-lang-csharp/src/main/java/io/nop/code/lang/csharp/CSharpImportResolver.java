package io.nop.code.lang.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
import io.nop.code.core.resolver.IImportResolver;

/**
 * C# using 解析。using 名（System.Collections.Generic 等）→ 项目内 namespace
 * 目录前缀匹配：项目文件的目录段与 using 的点分段逐段对齐。
 */
public class CSharpImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "CSHARP";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String ns = extractUsingPath(imp);
            if (ns == null || ns.isEmpty()) {
                continue;
            }

            CodeFileDependency dep = new CodeFileDependency();
            dep.setSourceFilePath(sourceFilePath);
            dep.setImportStatement(imp);

            String resolvedPath = matchProjectNamespace(ns, projectFiles);
            dep.setTargetFilePath(resolvedPath);
            dep.setResolved(resolvedPath != null);
            deps.add(dep);
        }
        return deps;
    }

    /**
     * using 条目可能是原始语句（using System.Xyz;）或已提取的裸名。
     */
    private String extractUsingPath(String usingStmt) {
        String trimmed = usingStmt.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("global ")) {
            trimmed = trimmed.substring(7).trim();
        }
        if (trimmed.startsWith("using ")) {
            trimmed = trimmed.substring(6).trim();
        }
        return trimmed;
    }

    /**
     * using 点分名 → 项目文件目录段逐段对齐：取项目文件路径中与 using 段
     * 完全一致的目录链，返回命中的根目录。
     */
    private String matchProjectNamespace(String ns, Set<String> projectFiles) {
        String[] segments = ns.split("\\.");
        for (String file : projectFiles) {
            String[] parts = file.split("/");
            // try each start position in the file path
            for (int start = 0; start + segments.length <= parts.length - 1; start++) {
                boolean matched = true;
                for (int i = 0; i < segments.length; i++) {
                    if (!segments[i].equals(parts[start + i])) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= start + segments.length - 1; i++) {
                        if (i > 0) {
                            sb.append('/');
                        }
                        sb.append(parts[i]);
                    }
                    return sb.toString();
                }
            }
        }
        return null;
    }
}
