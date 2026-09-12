package io.nop.ai.mcp.server;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiFileTool 错误路径 typed-error 化 + merge 条件修复回归。
 * json fileType 的 builder 不提供 xdef（IDocumentObjectBuilder.getXdefPath 默认 null），
 * 用于触发两个新 ErrorCode；dict.xml 为带 xdef 的 DSL，验证 merge 支持路径可走通。
 * <p>
 * ConverterRegistrationBean 在最小容器中是懒加载 bean（无消费方不构造），测试显式
 * 调 register() 保证 DocumentConverterManager 注册表就绪。
 */
@NopTestConfig
public class TestAiFileTool extends JunitBaseTestCase {

    private AiFileTool tool;
    private File baseDir;

    @BeforeEach
    public void setUp() throws IOException {
        new ConverterRegistrationBean().register();
        tool = new AiFileTool();
        baseDir = new File("target/test-ai-file-tool");
        Files.createDirectories(baseDir.toPath());
        tool.setBaseDir(baseDir.getPath());
    }

    @Test
    public void testLoadNopFileXDefThrowsForFileTypeWithoutXdef() {
        NopException e = assertThrows(NopException.class, () -> tool.loadNopFileXDef("json"));
        assertEquals(McpServerErrors.ERR_MCP_NO_XDEF_FOR_FILE_TYPE.getErrorCode(), e.getErrorCode());
        assertEquals("json", e.getParam("fileType"));
    }

    @Test
    public void testSaveNopFileMergeUnsupportedThrows() throws IOException {
        File jsonFile = new File(baseDir, "merge-unsupported.json");
        Files.writeString(jsonFile.toPath(), "{}");

        NopException e = assertThrows(NopException.class,
                () -> tool.saveNopFile("merge-unsupported.json", "json", "{\"a\":1}", Boolean.TRUE));
        assertEquals(McpServerErrors.ERR_MCP_MERGE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        assertEquals("json", e.getParam("fileType"));
    }

    @Test
    public void testSaveNopFileMergeSupportedWritesFile() throws IOException {
        // getResource 对不存在的文件先抛 ERR_MCP_FILE_NOT_FOUND——merge 支持路径需目标文件已存在
        File target = new File(baseDir, "merged.dict.xml");
        Files.writeString(target.toPath(), "<dict x:schema=\"/nop/schema/dict.xdef\"></dict>");

        String result = tool.saveNopFile("merged.dict.xml", "dict.xml",
                "<dict x:schema=\"/nop/schema/dict.xdef\"></dict>", Boolean.TRUE);
        assertEquals("SUCCESS", result);
        assertTrue(target.exists() && target.length() > 0, "merged file should be written");
    }
}
