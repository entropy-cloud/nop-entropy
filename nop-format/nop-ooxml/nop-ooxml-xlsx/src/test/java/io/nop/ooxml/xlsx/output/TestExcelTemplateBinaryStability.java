/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.output;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExcelTemplateBinaryStability extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGeneratedXlsxHasStableLogicalContentButUnstableBinary() throws Exception {
        IResource resource = new ClassPathResource("classpath:xlsx/calc.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelTemplate firstTemplate = new ExcelTemplate(ExcelOfficePackage.loadEmpty(), workbook, null);
        ExcelTemplate secondTemplate = new ExcelTemplate(ExcelOfficePackage.loadEmpty(), workbook, null);

        File firstFile = getTargetFile("binary-stability-1.xlsx");
        File secondFile = getTargetFile("binary-stability-2.xlsx");

        firstTemplate.generateToFile(firstFile, XLang.newEvalScope());
        // ZIP/DOS time stored in OOXML entries is rounded to 2-second precision.
        Thread.sleep(2200L);
        secondTemplate.generateToFile(secondFile, XLang.newEvalScope());

        assertNotEquals(sha256(firstFile), sha256(secondFile),
                "Whole XLSX binaries should currently differ because ZIP entry timestamps are regenerated");

        ZipSnapshot firstSnapshot = ZipSnapshot.load(firstFile);
        ZipSnapshot secondSnapshot = ZipSnapshot.load(secondFile);

        assertEquals(firstSnapshot.entryHashes, secondSnapshot.entryHashes,
                "OOXML entry payloads should remain identical when workbook content is unchanged");
        assertNotEquals(firstSnapshot.entryTimes, secondSnapshot.entryTimes,
                "ZIP entry timestamps should differ across repeated generations");
        assertTrue(firstSnapshot.hasSameNames(secondSnapshot),
                "OOXML package should contain the same entry set across repeated generations");
    }

    @Test
    public void testGeneratedXlsxCanBeMadeBinaryStableWithFixedEntryTime() throws Exception {
        IResource resource = new ClassPathResource("classpath:xlsx/calc.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelTemplate firstTemplate = new ExcelTemplate(ExcelOfficePackage.loadEmpty(), workbook, null);
        ExcelTemplate secondTemplate = new ExcelTemplate(ExcelOfficePackage.loadEmpty(), workbook, null);

        File firstFile = getTargetFile("binary-stability-fixed-1.xlsx");
        File secondFile = getTargetFile("binary-stability-fixed-2.xlsx");

        IEvalScope firstScope = XLang.newEvalScope();
        IEvalScope secondScope = XLang.newEvalScope();
        long fixedTime = 1700000000000L;
        firstScope.setLocalValue(null, OfficeConstants.VAR_ZIP_ENTRY_TIME, fixedTime);
        secondScope.setLocalValue(null, OfficeConstants.VAR_ZIP_ENTRY_TIME, fixedTime);

        firstTemplate.generateToFile(firstFile, firstScope);
        Thread.sleep(2200L);
        secondTemplate.generateToFile(secondFile, secondScope);

        assertEquals(sha256(firstFile), sha256(secondFile),
                "Whole XLSX binaries should match when a fixed OOXML entry timestamp is supplied");
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file.toPath());
        return toHex(digest.digest(bytes));
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(bytes));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static final class ZipSnapshot {
        private final Map<String, String> entryHashes;
        private final Map<String, String> entryTimes;

        private ZipSnapshot(Map<String, String> entryHashes, Map<String, String> entryTimes) {
            this.entryHashes = entryHashes;
            this.entryTimes = entryTimes;
        }

        static ZipSnapshot load(File file) throws Exception {
            Map<String, String> entryHashes = new LinkedHashMap<>();
            Map<String, String> entryTimes = new LinkedHashMap<>();
            try (ZipFile zip = new ZipFile(file)) {
                List<? extends ZipEntry> entries = orderedEntries(zip);
                for (ZipEntry entry : entries) {
                    entryTimes.put(entry.getName(), String.valueOf(entry.getTime()));
                    byte[] data = zip.getInputStream(entry).readAllBytes();
                    entryHashes.put(entry.getName(), sha256(data));
                }
            }
            return new ZipSnapshot(entryHashes, entryTimes);
        }

        boolean hasSameNames(ZipSnapshot other) {
            return entryHashes.keySet().equals(other.entryHashes.keySet());
        }

        private static List<? extends ZipEntry> orderedEntries(ZipFile zip) {
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement());
            }
            entries.sort((a, b) -> a.getName().compareTo(b.getName()));
            return entries;
        }
    }
}
