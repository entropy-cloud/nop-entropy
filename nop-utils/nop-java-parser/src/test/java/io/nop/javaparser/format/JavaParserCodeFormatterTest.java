package io.nop.javaparser.format;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.javaparser.utils.Utils.assertNotNull;
import static io.nop.javaparser.JavaParserErrors.ERR_JAVA_PARSER_PARSE_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class JavaParserCodeFormatterTest extends BaseTestCase {
    static final Logger LOG = LoggerFactory.getLogger(JavaParserCodeFormatterTest.class);

    @Test
    public void testSuccessfulParsing() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "class Test{}";
        boolean ignoreErrors = false;

        // Execute
        String result = formatter.format(loc, sourceCode, ignoreErrors);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("class Test"));
    }

    @Test
    public void testFailedParsingWithoutIgnoreErrors() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "invalid code";
        boolean ignoreErrors = false;

        // Test & Verify
        NopException exception = assertThrows(NopException.class,
                () -> formatter.format(loc, sourceCode, ignoreErrors));

        assertNotNull(exception.getErrorLocation());
        assertEquals(ERR_JAVA_PARSER_PARSE_FAILED.getErrorCode(), exception.getErrorCode());
    }

    @Test
    public void testFormatWithMultipleClasses() {
        // Setup
        JavaParserCodeFormatter formatter = new JavaParserCodeFormatter();
        SourceLocation loc = SourceLocation.fromPath("test.java");
        String sourceCode = "class A{}\nclass B{}";
        boolean ignoreErrors = false;

        // Execute
        String result = formatter.format(loc, sourceCode, ignoreErrors);

        // Verify
        // The exact formatted output might depend on the DefaultPrettyPrinter implementation,
        // but we can verify it contains both class definitions
        assertEquals("class A {\n}\n\nclass B {\n}", result.trim());
    }

    /**
     * 测试对整个nop-entropy项目的所有src/main/java下的java文件进行解析和格式化
     * 确保nop平台源码总是可以用JavaParser解析和格式化
     *
     * 这个测试一般不启用，比较耗时
     */
    @Disabled
    @Test
    public void testParseAllProjectJavaFiles() throws Exception {
        JavaParserCodeFormatter formatter = JavaParserCodeFormatter.INSTANCE;

        // 获取模块目录
        File moduleDir = getModuleDir();

        // 从模块目录向上查找项目根目录（nop-entropy-feat-code-index）
        File projectRoot = moduleDir;
        while (projectRoot != null) {
            File pomFile = new File(projectRoot, "pom.xml");
            // 项目根目录的特征：包含pom.xml，且名称为nop-entropy-feat-code-index
            if (pomFile.exists() && "nop-entropy-feat-code-index".equals(projectRoot.getName())) {
                break;
            }
            projectRoot = projectRoot.getParentFile();
        }
        if (projectRoot == null) {
            throw new IllegalStateException("Cannot find project root directory");
        }
        LOG.info("Project root: {}", projectRoot.getAbsolutePath());
        // 收集所有src/main/java下的java文件
        List<Path> javaFiles = new ArrayList<>();
        collectJavaFiles(projectRoot.toPath(), javaFiles);

        LOG.info("Found {} java files to test", javaFiles.size());

        int successCount = 0;
        int failCount = 0;
        List<String> failedFiles = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            try {
                String sourceCode = Files.readString(javaFile);
                if (StringHelper.isBlank(sourceCode)) {
                    continue;
                }

                String relativePath = projectRoot.toPath().relativize(javaFile).toString();
                SourceLocation loc = SourceLocation.fromPath(relativePath);

                // 执行格式化（不忽略错误，如果有问题会抛出异常）
                formatter.format(loc, sourceCode, false);
                successCount++;
            } catch (Exception e) {
                failCount++;
                String relativePath = projectRoot.toPath().relativize(javaFile).toString();
                failedFiles.add(relativePath + ": " + e.getMessage());
                LOG.error("Failed to parse: {}", relativePath, e);
            }
        }

        LOG.info("Parse result: {} success, {} failed", successCount, failCount);

        // 如果有失败的文件，打印详细信息
        if (!failedFiles.isEmpty()) {
            LOG.error("Failed files:");
            for (String failed : failedFiles) {
                LOG.error("  {}", failed);
            }
        }

        // 确保所有文件都能解析成功
        assertEquals(0, failCount, 
            "Some java files failed to parse. Failed count: " + failCount + 
            ". See log for details. First few failures: " + 
            failedFiles.stream().limit(5).collect(Collectors.joining("\n")));
    }

    private void collectJavaFiles(Path rootPath, List<Path> javaFiles) throws Exception {
        // 查找所有模块下的src/main/java目录
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> p.toString().contains(File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator))
                 .forEach(javaFiles::add);
        }
    }
}