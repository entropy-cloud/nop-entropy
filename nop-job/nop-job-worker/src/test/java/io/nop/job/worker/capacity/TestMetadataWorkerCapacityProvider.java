/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.worker.capacity;

import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.resource.ResourceVector;
import io.nop.job.core.NopJobCoreConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 212 Phase 2 capacity provider 单元测试。
 * 不依赖 IoC 容器，直接构造对象测试。
 */
class TestMetadataWorkerCapacityProvider {

    @Test
    void testReadsCapacityFromMetadata() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "4000",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "8192"));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(4000, capacity.getCpu());
        assertEquals(8192, capacity.getMemory());
    }

    @Test
    void testReturnsMaxValueWhenMetadataAndConfigBothAbsent() {
        MetadataWorkerCapacityProvider provider = newProvider();

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(ResourceVector.MAX_VALUE, capacity);
    }

    @Test
    void testFallsBackToConfigWhenMetadataAbsent() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setConfigCpu(2000);
        provider.setConfigMemory(4096);

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(2000, capacity.getCpu());
        assertEquals(4096, capacity.getMemory());
    }

    @Test
    void testMetadataOverridesConfigWhenBothPresent() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setConfigCpu(2000);
        provider.setConfigMemory(4096);
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "8000",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "16384"));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(8000, capacity.getCpu());
        assertEquals(16384, capacity.getMemory());
    }

    @Test
    void testPartialMetadataFallsBackPerDimension() {
        // 只声明 CPU，memory 走 config
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setConfigMemory(4096);
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "6000"));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(6000, capacity.getCpu());
        assertEquals(4096, capacity.getMemory());
    }

    @Test
    void testPartialMetadataFallsBackToMaxValueForMissingDimension() {
        // 只声明 CPU，memory 未在 metadata/config 任何地方声明 → MAX_VALUE
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "6000"));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(6000, capacity.getCpu());
        assertEquals(NopJobCoreConstants.DEFAULT_CAPACITY_IF_UNDECLARED, capacity.getMemory());
    }

    @Test
    void testMalformedMetadataCpuThrowsNopException() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "not-a-number"));

        NopException ex = assertThrows(NopException.class, provider::getMyCapacity);
        assertEquals("nop.err.job.worker-capacity-malformed", ex.getErrorCode());
    }

    @Test
    void testMalformedMetadataMemoryThrowsNopException() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "1000",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "abc MB"));

        NopException ex = assertThrows(NopException.class, provider::getMyCapacity);
        assertEquals("nop.err.job.worker-capacity-malformed", ex.getErrorCode());
    }

    @Test
    void testResultCachedAcrossCalls() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "4000",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "8192"));

        ResourceVector first = provider.getMyCapacity();
        // 更新 metadata 后结果不变（启动时缓存）
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "9999",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "9999"));
        ResourceVector second = provider.getMyCapacity();
        assertEquals(first, second);
        assertEquals(4000, second.getCpu());
    }

    @Test
    void testBlankMetadataTreatedAsAbsent() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "  ",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, ""));

        // 空白值视为未声明，回退 MAX_VALUE（不抛异常）
        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(ResourceVector.MAX_VALUE, capacity);
    }

    @Test
    void testTrimWhitespaceInMetadataValue() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "  3000  ",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "  6144  "));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(3000, capacity.getCpu());
        assertEquals(6144, capacity.getMemory());
    }

    // ========== AR-90: capacity "0" 语义统一 + 负数校验 ==========

    /**
     * AR-90：metadata 字面量 "0" 与 config "0" 语义一致——均视为"未设→MAX_VALUE"（无限），
     * 不再是真实零黑洞。
     */
    @Test
    void testMetadataLiteralZeroMeansUnsetMaxValue() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "0",
                NopJobCoreConstants.METADATA_KEY_CAPACITY_MEMORY, "0"));

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(ResourceVector.MAX_VALUE.getCpu(), capacity.getCpu(),
                "metadata '0' must mean unset→MAX_VALUE, not a real-zero black hole (AR-90)");
        assertEquals(ResourceVector.MAX_VALUE.getMemory(), capacity.getMemory());
    }

    @Test
    void testConfigZeroMeansUnsetMaxValue() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setConfigCpu(0);
        provider.setConfigMemory(0);

        ResourceVector capacity = provider.getMyCapacity();
        assertEquals(ResourceVector.MAX_VALUE, capacity, "config 0 = unset = MAX_VALUE");
    }

    /**
     * AR-90：负数 capacity 是配置错误，抛 NopException（不静默退化为拒绝一切的黑洞 worker）。
     */
    @Test
    void testNegativeMetadataCapacityThrows() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setMetadataSource(Map.of(
                NopJobCoreConstants.METADATA_KEY_CAPACITY_CPU, "-1"));

        NopException ex = assertThrows(NopException.class, provider::getMyCapacity);
        assertEquals("nop.err.job.worker-capacity-malformed", ex.getErrorCode());
    }

    @Test
    void testNegativeConfigCapacityThrows() {
        MetadataWorkerCapacityProvider provider = newProvider();
        provider.setConfigCpu(-100);

        assertThrows(NopException.class, provider::getMyCapacity,
                "negative config capacity must throw, not silently disable the worker");
    }

    /**
     * 工厂方法：每次返回新实例，确保 cached 状态隔离。
     */
    private MetadataWorkerCapacityProvider newProvider() {
        MetadataWorkerCapacityProvider provider = new MetadataWorkerCapacityProvider();
        // 显式设为 0 与默认值一致，避免污染（@InjectValue 不在测试中生效）
        provider.setConfigCpu(0);
        provider.setConfigMemory(0);
        return provider;
    }
}
