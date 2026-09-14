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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiFileTool 错误路径 typed-error 化 + merge 条件修复 + 沙箱路径包含性（fail-closed）回归。
 * <p>
 * 路径逃逸用例全部经由 MCP 工具入口（loadNopFile/saveNopFile）触发 getResource 的
 * ERR_MCP_PATH_ESCAPE 校验，断言错误码与 ARG_PATH 参数契约：
 * 绝对路径、{@code ..} 逃逸（含目标尚不存在的越界写入）一律拒绝；沙箱内相对路径
 * 读/写与 saveNopFile 新建文件不受影响。
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
        io.nop.commons.util.FileHelper.deleteAll(baseDir);
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
        File target = new File(baseDir, "merged.dict.xml");
        Files.writeString(target.toPath(), "<dict x:schema=\"/nop/schema/dict.xdef\"></dict>");

        String result = tool.saveNopFile("merged.dict.xml", "dict.xml",
                "<dict x:schema=\"/nop/schema/dict.xdef\"></dict>", Boolean.TRUE);
        assertEquals("SUCCESS", result);
        assertTrue(target.exists() && target.length() > 0, "merged file should be written");
    }

    @Test
    public void testSaveNopFileMergeToNewFileWritesFile() throws IOException {
        // merge 到沙箱内尚不存在的新文件：getResource 放行新路径，merge 分支走新建写入
        String result = tool.saveNopFile("new-merged.dict.xml", "dict.xml",
                "<dict x:schema=\"/nop/schema/dict.xdef\"></dict>", Boolean.TRUE);
        assertEquals("SUCCESS", result);
        File target = new File(baseDir, "new-merged.dict.xml");
        assertTrue(target.exists() && target.length() > 0, "new merged file should be created");
    }

    @Test
    public void testLoadNopFileAbsolutePathEscapeRejected() throws IOException {
        File outside = new File(baseDir.getParentFile(), "escape-abs.txt");
        Files.writeString(outside.toPath(), "secret");

        NopException e = assertThrows(NopException.class,
                () -> tool.loadNopFile(outside.getAbsolutePath(), null, null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals(outside.getAbsolutePath(), e.getParam(McpServerErrors.ARG_PATH));
    }

    @Test
    public void testLoadNopFileSystemAbsolutePathEscapeRejected() {
        // /etc/passwd 形态的系统绝对路径：存在走 exists 分支校验，不存在走非存在路径校验，均 fail-closed
        NopException e = assertThrows(NopException.class,
                () -> tool.loadNopFile("/etc/passwd", "txt", null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals("/etc/passwd", e.getParam(McpServerErrors.ARG_PATH));
    }

    @Test
    public void testSaveNopFileAbsolutePathEscapeRejected() throws IOException {
        File outside = new File(baseDir.getParentFile(), "escape-abs.txt");
        Files.writeString(outside.toPath(), "secret");

        NopException e = assertThrows(NopException.class,
                () -> tool.saveNopFile(outside.getAbsolutePath(), "txt", "pwned", null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals(outside.getAbsolutePath(), e.getParam(McpServerErrors.ARG_PATH));
        assertEquals("secret", Files.readString(outside.toPath()), "escape target must not be overwritten");
    }

    @Test
    public void testLoadNopFileDotDotEscapeRejected() throws IOException {
        File outside = new File(baseDir.getParentFile(), "escape-dotdot.txt");
        Files.writeString(outside.toPath(), "secret");

        NopException e = assertThrows(NopException.class,
                () -> tool.loadNopFile("../escape-dotdot.txt", null, null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals("../escape-dotdot.txt", e.getParam(McpServerErrors.ARG_PATH));
    }

    @Test
    public void testSaveNopFileDotDotEscapeViaSubdirRejected() throws IOException {
        File outside = new File(baseDir.getParentFile(), "escape-dotdot-sub.txt");
        Files.writeString(outside.toPath(), "secret");

        // foo/../../bar 形态：子目录段 + 两级 .. 逃逸
        NopException e = assertThrows(NopException.class,
                () -> tool.saveNopFile("missing-sub/../../escape-dotdot-sub.txt", "txt", "pwned", null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals("missing-sub/../../escape-dotdot-sub.txt", e.getParam(McpServerErrors.ARG_PATH));
        assertEquals("secret", Files.readString(outside.toPath()), "escape target must not be overwritten");
    }

    @Test
    public void testSaveNopFileEscapeToNonExistentTargetRejected() throws IOException {
        // 越界但目标尚不存在的写入也必须 fail-closed，不允许在沙箱外新建文件
        File outside = new File(baseDir.getParentFile(), "escape-new.txt");

        NopException e = assertThrows(NopException.class,
                () -> tool.saveNopFile("../escape-new.txt", "txt", "pwned", null));
        assertEquals(McpServerErrors.ERR_MCP_PATH_ESCAPE.getErrorCode(), e.getErrorCode());
        assertEquals("../escape-new.txt", e.getParam(McpServerErrors.ARG_PATH));
        assertFalse(outside.exists(), "non-existent escape target must not be created");
    }

    @Test
    public void testLoadNopFileWithinBaseDirSucceeds() throws IOException {
        File inside = new File(baseDir, "inside.txt");
        Files.writeString(inside.toPath(), "hello sandbox");

        String content = tool.loadNopFile("inside.txt", "txt", null);
        assertTrue(content.contains("hello sandbox"), "sandbox file should be readable");
    }

    @Test
    public void testLoadNopFileDotDotWithinBaseDirAllowed() throws IOException {
        // .. 段回退后仍落在沙箱内：不误伤正常路径（POSIX 要求中间目录存在才能穿越 ..）
        File subDir = new File(baseDir, "sub");
        Files.createDirectories(subDir.toPath());
        File inside = new File(baseDir, "root.txt");
        Files.writeString(inside.toPath(), "hello sandbox");

        String content = tool.loadNopFile("sub/../root.txt", "txt", null);
        assertTrue(content.contains("hello sandbox"), "in-sandbox .. path should be readable");
    }

    @Test
    public void testSaveNopFileNewFileWithinBaseDirSucceeds() throws IOException {
        String result = tool.saveNopFile("created.txt", "txt", "new content", null);
        assertEquals("SUCCESS", result);
        assertEquals("new content", Files.readString(new File(baseDir, "created.txt").toPath()));
    }

    @Test
    public void testSaveNopFileNewFileInNewSubdirSucceeds() throws IOException {
        String result = tool.saveNopFile("new-sub/created.txt", "txt", "deep content", null);
        assertEquals("SUCCESS", result);
        assertEquals("deep content", Files.readString(new File(baseDir, "new-sub/created.txt").toPath()));
    }

    @Test
    public void testSaveNopFileOverwriteWithinBaseDirSucceeds() throws IOException {
        File inside = new File(baseDir, "overwrite.txt");
        Files.writeString(inside.toPath(), "old");

        String result = tool.saveNopFile("overwrite.txt", "txt", "new", null);
        assertEquals("SUCCESS", result);
        assertEquals("new", Files.readString(inside.toPath()));
    }
}