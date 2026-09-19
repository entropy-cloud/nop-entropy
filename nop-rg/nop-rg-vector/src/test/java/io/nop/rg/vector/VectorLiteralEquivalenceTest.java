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
    public void testFuzzEquivalenceWithScalar() {
        NopRgVectorLiteralFinderProvider provider = new NopRgVectorLiteralFinderProvider();
        assertTrue(provider.available());
        java.util.Random random = new java.util.Random(4242);
        char[] alphabet = {'a', 'b', 'c', 'N'};
        int cases = 0;
        for (int iter = 0; iter < 500; iter++) {
            int dataLen = 8 + random.nextInt(300);
            byte[] data = new byte[dataLen];
            for (int i = 0; i < dataLen; i++) {
                data[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            int patternLen = 1 + random.nextInt(6);
            byte[] pattern = new byte[patternLen];
            for (int i = 0; i < patternLen; i++) {
                pattern[i] = (byte) alphabet[random.nextInt(alphabet.length)];
            }
            boolean ignoreCase = random.nextBoolean();
            int offset = random.nextInt(dataLen + 1);
            int limit = offset + random.nextInt(dataLen - offset + 1);

            PreparedFinder vectorFinder = provider.compile(pattern, ignoreCase);
            PreparedLiteral scalarFinder = PreparedLiteral.compile(pattern, ignoreCase);
            MemorySegment seg = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);

            long expected = scalarFinder.find(seg, offset, limit);
            long actual = vectorFinder.find(seg, offset, limit);
            assertEquals(expected, actual, () -> "data=" + new String(data) + " pattern="
                    + new String(pattern) + " ignoreCase=" + ignoreCase
                    + " offset=" + offset + " limit=" + limit);
            cases++;
        }
        assertTrue(cases >= 500);
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
