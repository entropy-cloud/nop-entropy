package io.nop.shell.utils;

import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TestDesktopHelper {

    @Test
    public void testOpenBrowserHeadlessDoesNotThrow() {
        // 仅在 headless 环境验证（CI）：修复前 Desktop.getDesktop() 在这里直接抛 HeadlessException，
        // 后置的 isDesktopSupported 检查永远起不到保护作用；修复后应静默跳过。
        // 非 headless 环境跳过，避免真打开浏览器。
        assumeTrue(GraphicsEnvironment.isHeadless());
        assertDoesNotThrow(() -> DesktopHelper.openBrowser("https://example.com"));
    }
}
