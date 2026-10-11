package io.nop.rg.vector;

import io.nop.rg.core.search.LiteralFinderProvider;
import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.PreparedLiteral;
import io.nop.rg.core.search.ScalarByteSearcher;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vector vs Scalar 等价性 fuzz、降级 seam、SPI 可发现性（plan 2266 Phase 1）。
 * 运行前提：surefire argLine --add-modules jdk.incubator.vector（canary 断言守护）。
 */
public class VectorLiteralEquivalenceTest {

    // canary：argLine 配错（缺 add-modules）时本测试立即失败，避免"全跳过"假绿
    @Test
    public void canaryIncubatorModuleLoadable() throws ClassNotFoundException {
        Class.forName("jdk.incubator.vector.ByteVector");
    }

    @Test
    public void testSpiProviderDiscoverable() {
        ServiceLoader<LiteralFinderProvider> loader = ServiceLoader.load(LiteralFinderProvider.class);
        LiteralFinderProvider provider = loader.findFirst().orElse(null);
        assertNotNull(provider, "nop-rg-vector 必须经 META-INF/services 注册 provider");
        assertTrue(provider instanceof NopRgVectorLiteralFinderProvider);
    }

    @Test
    public void testVectorAvailableOnAddModulesJvm() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertTrue(provider.available(), "surefire argLine 应含 --add-modules jdk.incubator.vector");
        assertEquals("", provider.unavailableReason());
    }

    @Test
    public void testForcedFallbackReturnsScalarEquivalent() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider(true);
        assertFalse(provider.available());
        assertFalse(provider.unavailableReason().isEmpty());

        PreparedFinder finder = provider.compile("needle".getBytes(), false);
        // 降级实现 = 标量等价（PreparedLiteral），结果一致
        byte[] data = "xxneedlexx".getBytes();
        MemorySegment seg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
        assertEquals(2, finder.find(seg, 0, data.length));
        assertEquals(-1, finder.find(seg, 3, data.length));
    }

    @Test
    public void testEmptyPatternRejectedLikeScalar() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.compile(new byte[0], false));
    }

    @Test
    public void testLengthThresholdPolicyShortPatternReturnsScalar() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertTrue(provider.available());
        // 6B < SIMD_MIN_PATTERN_LENGTH：走标量等价实现（plan 2267 R1 长度阈值策略——
        // 6B 实测向量仅为标量 72-80%，避免 --vector 回退短模式性能）
        PreparedFinder finder = provider.compile("needle".getBytes(), false);
        assertFalse(finder instanceof VectorPreparedLiteral, "短模式不得走 Vector 实现");
        assertTrue(finder instanceof PreparedLiteral);
    }

    @Test
    public void testLengthThresholdPolicyLongPatternReturnsVector() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertTrue(provider.available());
        // length == SIMD_MIN_PATTERN_LENGTH 边界（>= 语义）与 > 阈值均走 Vector
        byte[] atThreshold = "QzWxEcRvTbYnUmIk".getBytes();
        assertEquals(NopRgVectorLiteralFinderProvider.SIMD_MIN_PATTERN_LENGTH, atThreshold.length);
        assertTrue(provider.compile(atThreshold, false) instanceof VectorPreparedLiteral);
        byte[] above = "QzWxEcRvTbYnUmIkOlPjHgFdSaGdJfKd".getBytes();
        assertTrue(provider.compile(above, false) instanceof VectorPreparedLiteral);
    }

    @Test
    public void testFuzzEquivalenceWithScalar() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertTrue(provider.available());
        java.util.Random random = new java.util.Random(4242);
        char[] alphabet = {'a', 'b', 'c', 'N'};
        int cases = 0;
        int vectorPathCases = 0;
        for (int iter = 0; iter < 500; iter++) {
            // plan 2267 R1：模式长度跨阈值分布——奇数迭代长模式（Vector 路径），偶数短模式（标量路径），
            // 保证阈值策略下两条实现路径都有等价性覆盖
            boolean longPattern = (iter & 1) == 1;
            int patternLen = longPattern
                    ? NopRgVectorLiteralFinderProvider.SIMD_MIN_PATTERN_LENGTH + random.nextInt(17)
                    : 1 + random.nextInt(6);
            byte[] pattern = new byte[patternLen];
            for (int i = 0; i < patternLen; i++) {
                pattern[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            int dataLen = patternLen * 2 + random.nextInt(300);
            byte[] data = new byte[dataLen];
            for (int i = 0; i < dataLen; i++) {
                data[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            if (longPattern && dataLen > patternLen) {
                // 植入至少一次真实命中，避免长模式路径退化为全 -1 比较
                int plantAt = random.nextInt(dataLen - patternLen + 1);
                System.arraycopy(pattern, 0, data, plantAt, patternLen);
            }
            boolean ignoreCase = random.nextBoolean();
            int offset = random.nextInt(dataLen + 1);
            int limit = offset + random.nextInt(dataLen - offset + 1);

            PreparedFinder vectorFinder = provider.compile(pattern, ignoreCase);
            PreparedLiteral scalarFinder = PreparedLiteral.compile(pattern, ignoreCase);
            if (longPattern) {
                assertTrue(vectorFinder instanceof VectorPreparedLiteral,
                        () -> "长模式应走 Vector 路径: len=" + patternLen);
                vectorPathCases++;
            }
            MemorySegment seg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);

            long expected = scalarFinder.find(seg, offset, limit);
            long actual = vectorFinder.find(seg, offset, limit);
            assertEquals(expected, actual, () -> "data=" + new String(data) + " pattern="
                    + new String(pattern) + " ignoreCase=" + ignoreCase
                    + " offset=" + offset + " limit=" + limit);
            cases++;
        }
        assertTrue(cases >= 500);
        assertTrue(vectorPathCases >= 100, "长模式（Vector 路径）用例不足: " + vectorPathCases);
    }

    @Test
    public void testBoundariesMatchScalar() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        byte[] data = {(byte) 0xFF, 0x00, 'a', (byte) 0x80, 'N', 'e', 'e', 'd', 'l', 'e'};
        MemorySegment seg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
        PreparedFinder vectorFinder = provider.compile("Needle".getBytes(), false);
        ScalarByteSearcher scalar = ScalarByteSearcher.INSTANCE;

        // 模式长于数据 / limit 裁剪 / 高字节数据
        assertEquals(-1, vectorFinder.find(seg, 0, 3));
        assertEquals(scalar.findPattern(seg, 0, data.length, "Needle".getBytes()),
                vectorFinder.find(seg, 0, data.length));
        assertEquals(-1, vectorFinder.find(seg, 5, 8));

        // plan 2267 R1：短模式走标量路径后，Vector 实现的边界覆盖改用 >= 阈值长模式
        String longToken = "QzWxEcRvTbYnUmIkOlPj";
        byte[] longData = ("xx" + longToken + "yy" + longToken).getBytes();
        MemorySegment longSeg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, longData);
        PreparedFinder longFinder = provider.compile(longToken.getBytes(), false);
        assertTrue(longFinder instanceof VectorPreparedLiteral);
        int first = "xx".length();
        int second = first + longToken.length() + "yy".length();
        assertEquals(first, longFinder.find(longSeg, 0, longData.length));
        assertEquals(second, longFinder.find(longSeg, first + 1, longData.length));
        assertEquals(-1, longFinder.find(longSeg, 0, longToken.length() - 1)); // 模式长于 limit
        assertEquals(-1, longFinder.find(longSeg, longData.length, longData.length));
    }

    @Test
    public void testFindFirstByteMatchesScalarContract() {
        byte[] data = "aXbXXc".getBytes();
        MemorySegment seg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
        assertEquals(1, VectorByteSearcher.INSTANCE.findFirstByte(seg, 0, data.length, (byte) 'X'));
        assertEquals(3, VectorByteSearcher.INSTANCE.findFirstByte(seg, 2, data.length, (byte) 'X'));
        assertEquals(4, VectorByteSearcher.INSTANCE.findFirstByte(seg, 4, data.length, (byte) 'X'));
        assertEquals(-1, VectorByteSearcher.INSTANCE.findFirstByte(seg, 5, data.length, (byte) 'X'));
        assertEquals(-1, VectorByteSearcher.INSTANCE.findFirstByte(seg, 0, 0, (byte) 'a'));
    }
}
