package io.nop.code.lang.go;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
import io.nop.code.core.resolver.IImportResolver;

/**
 * Go import 路径解析。Go import 的“使用名”默认为路径末段
 * （github.com/x/y/z → z），显式 alias 由 import_spec.name 覆盖（分析器已在
 * IMPORT 符号上记录 importAlias）。同项目内的包路径按项目文件前缀匹配解析。
 */
public class GoImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "GO";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String importPath = extractImportPath(imp);
            if (importPath == null || importPath.isEmpty()) {
                continue;
            }

            CodeFileDependency dep = new CodeFileDependency();
            dep.setSourceFilePath(sourceFilePath);
            dep.setImportStatement(imp);

            String resolvedPath = matchProjectPackage(importPath, projectFiles);
            dep.setTargetFilePath(resolvedPath);
            dep.setResolved(resolvedPath != null);
            deps.add(dep);
        }
        return deps;
    }

    /**
     * import 条目可能是原始语句（import "path"）或已提取的裸路径；提取引号内路径。
     */
    private String extractImportPath(String importStmt) {
        String trimmed = importStmt.trim();
        int firstQuote = trimmed.indexOf('"');
        if (firstQuote >= 0) {
            int endQuote = trimmed.indexOf('"', firstQuote + 1);
            if (endQuote > firstQuote) {
                return trimmed.substring(firstQuote + 1, endQuote);
            }
        }
        int backQuote = trimmed.indexOf('`');
        if (backQuote >= 0) {
            int endBackQuote = trimmed.indexOf('`', backQuote + 1);
            if (endBackQuote > backQuote) {
                return trimmed.substring(backQuote + 1, endBackQuote);
            }
        }
        return trimmed;
    }

    /**
     * Go 包路径 → 项目文件目录前缀匹配：项目内任一文件位于
     * {@code <root>/<importPath>/} 之下即视为命中，返回该根目录。
     */
    private String matchProjectPackage(String importPath, Set<String> projectFiles) {
        String packagePrefix = importPath + "/";
        for (String file : projectFiles) {
            int idx = file.indexOf(packagePrefix);
            if (idx >= 0) {
                return file.substring(0, idx + importPath.length());
            }
        }
        return null;
    }
}
