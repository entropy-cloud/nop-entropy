package io.nop.code.lang.rust;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeFileDependency;
import io.nop.code.core.resolver.IImportResolver;

/**
 * Rust use 路径解析。crate 路径（crate::utils::helper / self::x / 外部 crate 名）
 * → 项目内 mod 目录前缀匹配：项目文件按 “crate 名即项目根” 假设，将 use 路径的
 * crate 段剥去后与项目目录匹配。
 */
public class RustImportResolver implements IImportResolver {

    @Override
    public String getLanguage() {
        return "RUST";
    }

    @Override
    public List<CodeFileDependency> resolveImports(String sourceFilePath, List<String> imports,
                                                   Set<String> projectFiles) {
        List<CodeFileDependency> deps = new ArrayList<>(imports.size());
        for (String imp : imports) {
            String usePath = stripPrefix(imp);
            if (usePath == null || usePath.isEmpty()) {
                continue;
            }

            CodeFileDependency dep = new CodeFileDependency();
            dep.setSourceFilePath(sourceFilePath);
            dep.setImportStatement(imp);

            String resolvedPath = matchProjectModule(usePath, projectFiles);
            dep.setTargetFilePath(resolvedPath);
            dep.setResolved(resolvedPath != null);
            deps.add(dep);
        }
        return deps;
    }

    /**
     * use 语句可能是原始语句（use std::fmt;）或已提取的裸路径；提取路径本体。
     */
    private String stripPrefix(String useStmt) {
        String trimmed = useStmt.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("pub ")) {
            trimmed = trimmed.substring(4).trim();
        }
        if (trimmed.startsWith("use ")) {
            trimmed = trimmed.substring(4).trim();
        }
        return trimmed;
    }

    /**
     * use 路径首段若是 crate/self/super 等本 crate 定位符，剥去后与项目文件
     * 目录前缀匹配（项目文件相对根 = crate 根）。
     */
    private String matchProjectModule(String usePath, Set<String> projectFiles) {
        String path = usePath;
        for (String selfRef : new String[]{"crate::", "self::", "super::"}) {
            if (path.startsWith(selfRef)) {
                path = path.substring(selfRef.length());
                break;
            }
        }
        path = path.replace("::", "/");
        String modulePrefix = path + "/";
        for (String file : projectFiles) {
            int idx = file.indexOf(modulePrefix);
            if (idx >= 0) {
                return file.substring(0, idx + path.length());
            }
        }
        String directFile = path + ".rs";
        for (String file : projectFiles) {
            if (file.equals(directFile) || file.endsWith("/" + directFile)) {
                int slash = file.lastIndexOf('/');
                return slash > 0 ? file.substring(0, slash) : file;
            }
        }
        return null;
    }
}
