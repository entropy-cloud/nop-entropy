package io.nop.ai.core.service;

import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-21（plan 2555 裁定 I / R1-M4）护栏载体：聊天日志保留期——
 * retentionDays>0 时过期日期分区目录（logDir/yyyy/MM-dd/）被惰性清理；
 * =0（缺省）不清理；once-per-date 节流（lastSweepDate）使同日期第二次
 * 触发不再扫描删除。
 */
public class TestChatLogRetention {

    private static final DateTimeFormatter DAY_DIR = DateTimeFormatter.ofPattern("MM-dd");

    private static File newTempDir() throws Exception {
        return Files.createTempDirectory("chat-log-retention").toFile();
    }

    private static File newDayDir(File root, LocalDate date) {
        File dayDir = new File(root, date.getYear() + "/" + DAY_DIR.format(date) + "/sess-1");
        assertTrue(dayDir.mkdirs(), "cannot create " + dayDir);
        File logFile = new File(dayDir, "1-r-0-abc-request.yaml");
        try {
            Files.write(logFile.toPath(), "content".getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new File(root, date.getYear() + "/" + DAY_DIR.format(date));
    }

    @Test
    public void testRetentionZeroSweepsNothing() throws Exception {
        File root = newTempDir();
        LocalDate today = CoreMetrics.currentDate();
        File oldDay = newDayDir(root, today.minusDays(30));
        File todayDay = newDayDir(root, today);

        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir(root.getPath());
        logger.setRetentionDays(0);

        logger.maybeSweepExpired();

        assertTrue(oldDay.exists(), "retention=0 (default) must not sweep anything");
        assertTrue(todayDay.exists());
        assertTrue(FileHelper_deleteAll(root));
    }

    @Test
    public void testPositiveRetentionDeletesExpiredPartitions() throws Exception {
        File root = newTempDir();
        LocalDate today = CoreMetrics.currentDate();
        // 过期分区取不同年份（today-400d），验证空年份目录机会性清理
        File expiredDay = newDayDir(root, today.minusDays(400));
        File keptDay = newDayDir(root, today.minusDays(2));
        File todayDay = newDayDir(root, today);

        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir(root.getPath());
        logger.setRetentionDays(7);

        logger.maybeSweepExpired();

        assertFalse(expiredDay.exists(), "partition older than retention must be deleted");
        assertTrue(keptDay.exists(), "partition within retention must be kept");
        assertTrue(todayDay.exists(), "today's partition must never be deleted");
        // 空年份目录机会性清理（过期分区独占年份目录）
        assertFalse(new File(root, String.valueOf(today.minusDays(400).getYear())).exists());
        assertTrue(FileHelper_deleteAll(root));
    }

    @Test
    public void testOncePerDateThrottle() throws Exception {
        File root = newTempDir();
        LocalDate today = CoreMetrics.currentDate();

        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir(root.getPath());
        logger.setRetentionDays(7);

        File first = newDayDir(root, today.minusDays(10));
        logger.maybeSweepExpired();
        assertFalse(first.exists(), "first sweep in a date must delete expired partitions");

        // 同日期内新出现的过期分区：第二次触发被 once-per-date 节流跳过
        File second = newDayDir(root, today.minusDays(9));
        logger.maybeSweepExpired();
        assertTrue(second.exists(),
                "second sweep on the same date must be throttled (lastSweepDate guard)");

        // 直接 sweepExpired 不受节流约束（校准路径仍在）
        logger.sweepExpired(today.minusDays(7));
        assertFalse(second.exists());
        assertTrue(FileHelper_deleteAll(root));
    }

    @Test
    public void testPartitionNameParsing() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        assertEquals(date, DefaultChatLogger.parseMonthDay(2026, "11-09"));
        assertTrue(DefaultChatLogger.parseMonthDay(2026, "not-a-date") == null);
        assertTrue(DefaultChatLogger.parseMonthDay(2026, "13-99") == null);
        assertEquals(2026, DefaultChatLogger.parseYear("2026"));
        assertTrue(DefaultChatLogger.parseYear("2x26") < 0);
    }

    private static boolean FileHelper_deleteAll(File dir) {
        return io.nop.commons.util.FileHelper.deleteAll(dir);
    }
}
