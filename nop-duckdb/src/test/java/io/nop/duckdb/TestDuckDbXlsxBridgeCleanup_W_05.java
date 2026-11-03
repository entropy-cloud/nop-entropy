package io.nop.duckdb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-05（plan 2529 Phase 2 组 A / 设计裁定③）readXlsx 桥接临时文件配对回归钉。
 *
 * <p>缺陷形态（C6-003 + CS-003）：xlsx→CSV 转换失败路径的临时文件不删（catch 重抛发生在
 * finally 作用域之外）——失败按次累积磁盘垃圾；叠加 {@code deleteOnExit} 向 JVM 级
 * DeleteOnExitHook 只增不删。修复形态：创建段与生命周期段合并，转换段与 readCsv 段成功
 * 失败均在同一 finally 中 delete()，deleteOnExit 注册移除。
 *
 * <p>红③ 形态（TestDuckDbFaultInjection :92 失败不留残留断言先例复制）：损坏 xlsx（非 zip
 * 字节）→ 断言显式 NopDuckDbException + 系统临时目录无 {@code duckdb-xlsx-} 前缀残留。
 * 模块内既有测试对 readXlsx 的消费全为成功路径（finally 已删），前缀扫描零干扰。
 */
public class TestDuckDbXlsxBridgeCleanup_W_05 {

    /**
     * 转换失败路径：显式错误 + 临时目录零残留（先关闭后拒绝语义的文件域同构——失败也必须
     * 完成清理配对）。
     */
    @Test
    @Timeout(60)
    public void testW_05_corruptXlsxLeavesNoTempResidue() throws Exception {
        Path dir = Files.createTempDirectory("w05-corrupt");
        Path xlsx = dir.resolve("corrupt.xlsx");
        // valid file extension, invalid content: the xlsx->CSV conversion fails after the
        // temp csv was already created - the failure path must still delete it
        Files.write(xlsx, "this is not a zip archive".getBytes(StandardCharsets.UTF_8));

        DuckDbEngine engine = new DuckDbEngine();
        try {
            Connection conn = engine.openMemory();
            try {
                NopDuckDbException e = assertThrows(NopDuckDbException.class,
                        () -> DuckDbFiles.readXlsx(conn, xlsx.toString(), "t_corrupt"));
                assertEquals(NopDuckDbErrors.ERR_DUCKDB_IO_FAILED.getErrorCode(), e.getErrorCode(),
                        "conversion failure keeps the documented io-failed wrapping, got: "
                                + e.getErrorCode());
            } finally {
                conn.close();
            }
        } finally {
            engine.close();
        }

        // the failed conversion must not leak the bridge temp csv into the system temp dir
        try (Stream<Path> tmp = Files.list(Paths.get(System.getProperty("java.io.tmpdir")))) {
            List<String> residue = tmp
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("duckdb-xlsx-"))
                    .collect(Collectors.toList());
            assertTrue(residue.isEmpty(),
                    "failed xlsx bridge conversion must delete its temp csv, found: " + residue);
        }
    }
}
