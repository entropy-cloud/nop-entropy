package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import static io.nop.commons.util.StringHelper.compareVersions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareTest {

    // 基础相等测试
    @Test
    void testEqualVersions() {
        assertEquals(0, compareVersions("1.2.3", "1.2.3"));
        assertEquals(0, compareVersions("1.02", "1.2"));
        assertEquals(0, compareVersions("1.2.0", "1.2"));
        assertEquals(0, compareVersions("1.2.00", "1.2.0"));
        assertEquals(0, compareVersions("001.002.003", "1.2.3"));
    }

    // 更大版本测试
    @Test
    void testGreaterVersions() {
        assertTrue(compareVersions("2.0", "1.9.9") > 0);
        assertTrue(compareVersions("1.10", "1.9") > 0);
        assertTrue(compareVersions("1.2.1", "1.2") > 0);
        assertTrue(compareVersions("3.1.2", "1.2.11") > 0);
        assertTrue(compareVersions("1.2.10", "1.2.9") > 0);
        assertTrue(compareVersions("1.0.0-alpha.2", "1.0.0-alpha.1") > 0);
    }

    // 更小版本测试
    @Test
    void testSmallerVersions() {
        assertTrue(compareVersions("1.9.9", "2.0") < 0);
        assertTrue(compareVersions("1.9", "1.10") < 0);
        assertTrue(compareVersions("1.2", "1.2.1") < 0);
        assertTrue(compareVersions("1.2.9", "1.2.10") < 0);
        assertTrue(compareVersions("0.9", "1.0") < 0);
    }

    // 不同长度测试
    @Test
    void testDifferentLengths() {
        assertEquals(0, compareVersions("1.2", "1.2.0.0"));  // 修改为应该相等
        assertEquals(0, compareVersions("1.2.0.0", "1.2"));  // 修改为应该相等
        assertTrue(compareVersions("1.2.3", "1.2.3.4") < 0);
        assertTrue(compareVersions("1.2.3.4", "1.2.3") > 0);
        assertTrue(compareVersions("1.2", "1.2.0.1") < 0);
    }

    // 边界情况测试
    @Test
    void testEdgeCases() {
        assertEquals(0, compareVersions("", ""));
        assertTrue(compareVersions("1", "0") > 0);
        assertTrue(compareVersions("0", "1") < 0);
        assertTrue(compareVersions("0.0.1", "0.0.0.1") > 0);
        assertEquals(0, compareVersions("1.0.0", "1"));  // 修改为应该相等
        assertEquals(0, compareVersions("0.0.0", "0"));
        assertTrue(compareVersions("2147483647", "2147483646") > 0);  // 大数测试
    }

    // 前导零测试
    @Test
    void testLeadingZeros() {
        assertEquals(0, compareVersions("01.02.03", "1.2.3"));
        assertTrue(compareVersions("1.02", "1.2.0") == 0);
        assertTrue(compareVersions("1.00010", "1.9") > 0);
        assertTrue(compareVersions("1.001", "1.01") == 0);
        assertEquals(0, compareVersions("0001", "1"));
    }

    // 修正案例测试
    @Test
    void testFixedCases() {
        assertEquals(0, compareVersions("1", "1.0.0"));  // 修改为应该相等
        assertTrue(compareVersions("1.0.1", "1.0") > 0);
        assertEquals(0, compareVersions("1.000", "1.0"));
        assertTrue(compareVersions("1.001", "1.000") > 0);
        assertEquals(0, compareVersions("1.0", "1"));
        assertTrue(compareVersions("1", "1.1") < 0);
        assertTrue(compareVersions("1.999", "1.1000") < 0);
    }

    // 新增：非法格式测试
    @Test
    void testInvalidFormats() {
        // 连续点
        assertEquals(0, compareVersions("1..2", "1.0.2"));

        // 非数字字符
        assertTrue(compareVersions("1.a.2", "1.0.2") > 0);
        assertTrue(compareVersions("1.0.2", "1.a.2") < 0);

        // 开头/结尾的点
        assertTrue(compareVersions(".1.2", "0.1.2") == 0);
        assertTrue(compareVersions("1.2.", "1.2.0") == 0);
    }

    // 新增：超长版本号测试
    @Test
    void testLongVersions() {
        String longVersion1 = "1." + "9".repeat(100) + ".1";
        String longVersion2 = "1." + "8".repeat(100) + ".1";
        assertTrue(compareVersions(longVersion1, longVersion2) > 0);

        String longEqual1 = "1." + "0".repeat(1000);
        String longEqual2 = "1";
        assertEquals(0, compareVersions(longEqual1, longEqual2));
    }



    @Test
    void testImprovedComparison() {
        // 前导零
        assertEquals(0, compareVersions("01.02.03", "1.2.3"));

        // 不同长度数字
        assertTrue(compareVersions("1.10", "1.9") > 0);
        assertTrue(compareVersions("1.9", "1.10") < 0);

        // 不同段数
        assertEquals(0, compareVersions("1.2", "1.2.0.0"));
        assertTrue(compareVersions("1.2.1", "1.2") > 0);

        // 大数比较
        assertTrue(compareVersions("1.999", "1.1000") < 0);

        // 边界情况
        assertEquals(0, compareVersions("0.0.0", "0"));
        assertTrue(compareVersions("1", "0") > 0);
    }
}