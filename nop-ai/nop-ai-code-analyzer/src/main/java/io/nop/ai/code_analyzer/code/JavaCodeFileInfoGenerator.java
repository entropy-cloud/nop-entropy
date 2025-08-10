package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import io.nop.ai.code_analyzer.maven.MavenModule;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class JavaCodeFileInfoGenerator {
    static final Logger LOG = LoggerFactory.getLogger(JavaCodeFileInfoGenerator.class);

    private final JavaCodeFileInfoParser parser;

    public JavaCodeFileInfoGenerator(JavaCodeFileInfoParser parser) {
        this.parser = parser;
    }

    public JavaCodeFileInfoGenerator(JavaParser javaParser, Predicate<String> ignoredTypes) {
        this(new JavaCodeFileInfoParser(javaParser, ignoredTypes));
    }

    public void generate(MavenModule module, File rootDir, File outDir, File summaryDir) {
        rootDir = FileHelper.getAbsoluteFile(rootDir);
        outDir = FileHelper.getAbsoluteFile(outDir);

        String subPath = StringHelper.appendPath(module.getModulePath(), "src/main/java");

        File javaSrcDir = new File(rootDir, subPath);
        File outSrcDir = new File(outDir, subPath);
        File summarySrcDir = summaryDir == null ? null : new File(summaryDir, subPath);

        generate0(module, subPath, javaSrcDir, outSrcDir, summarySrcDir);
    }

    void generate0(MavenModule module, String basePath, File srcDir, File destDir, File summaryDir) {
        FileHelper.walk2(srcDir, destDir, (file1, file2) -> {
            if (file1.getName().startsWith("."))
                return FileVisitResult.SKIP_SUBTREE;

            if (!file1.getName().endsWith(".java"))
                return FileVisitResult.CONTINUE;

            File destFile = new File(file2.getParentFile(), StringHelper.replaceFileExt(file2.getName(), ".info.json"));
            if (destFile.exists())
                return FileVisitResult.CONTINUE;

            String relativePath = FileHelper.getRelativePath(srcDir, file1);
            String filePath = StringHelper.appendPath(basePath, relativePath);
            File summaryFile = summaryDir == null ? null : new File(summaryDir, StringHelper.replaceFileExt(relativePath, "summary.json"));
            generateFile(module, filePath, file1, destFile, summaryFile);
            return FileVisitResult.CONTINUE;
        });
    }

    private void generateFile(MavenModule module, String filePath, File srcFile, File destFile, File summaryFile) {
        CodeFileInfo fileInfo = parser.parseFromFile(srcFile);
        fileInfo.setFilePath(filePath);
        fileInfo.setArtifactId(module.getArtifactId());
        if (summaryFile != null && summaryFile.exists()) {
            List<Map<String, Object>> json = (List<Map<String, Object>>) JsonTool.parseBeanFromResource(new FileResource(summaryFile));
            addSummary(summaryFile.getName(), fileInfo, json);
        }
        FileHelper.writeText(destFile, JsonTool.serialize(fileInfo, true), null);
    }

    private void addSummary(String fileName, CodeFileInfo fileInfo, List<Map<String, Object>> json) {
        if (json == null || json.isEmpty())
            return;

        for (Map<String, Object> classJson : json) {
            String name = (String) classJson.get("name");
            String summary = (String) classJson.get("summary");
            List<Map<String, Object>> functions = (List<Map<String, Object>>) classJson.get("functions");
            CodeFileInfo.CodeClassInfo cls = fileInfo.getClassInfo(name);
            if (cls == null) {
                LOG.info("nop.ai.code.ignore-unknown-class-for-summary:class={},file={}", name, fileName);
                continue;
            }
            cls.setSummary(summary);
            for (Map<String, Object> fnJson : functions) {
                String fnName = (String) fnJson.get("name");
                String fnSummary = (String) fnJson.get("summary");
                CodeFileInfo.CodeFunctionInfo fn = cls.getFunction(fnName);
                if (fn == null) {
                    LOG.info("nop.ai.code.ignore-unknown-function-for-summary:function={},class={},file={}", fnName, name, fileName);
                    continue;
                }
                fn.setSummary(fnSummary);
            }
        }
    }
}
