package io.nop.xlang.backend;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static io.nop.xlang.backend.BackendTestFakes.FAKE_DYNAMIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FAKE_STATIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FakeDynamicBackend;
import static io.nop.xlang.backend.BackendTestFakes.FakeStaticBackend;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表契约测试：注册/反注册/查询/失败语义（初始化失败→不可用条目不阻断启动）、
 * 能力槽位唯一性 fail-fast、非法契约 fail-fast。
 */
public class TestEvalBackendRegistry {

    private EvalBackendRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = EvalBackendRegistry.instance();
        clearRegistry();
    }

    @AfterEach
    public void tearDown() {
        clearRegistry();
    }

    private void clearRegistry() {
        for (String id : Set.copyOf(registry.getBackendIds()))
            registry.unregister(registry.getBackend(id));
    }

    @Test
    public void testRegisterAndQuery() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Collections.emptySet());
        registry.register(staticBackend);
        assertTrue(registry.getBackendIds().contains(FAKE_STATIC_ID));
        assertSame(staticBackend, registry.getBackend(FAKE_STATIC_ID));
        assertSame(staticBackend, registry.findStaticBackend());
        assertNull(registry.findDynamicBackend());
        assertFalse(registry.isEmpty());

        registry.unregister(staticBackend);
        assertTrue(registry.isEmpty());
        assertNull(registry.getBackend(FAKE_STATIC_ID));
    }

    @Test
    public void testIdempotentReRegister() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Collections.emptySet());
        registry.register(staticBackend);
        registry.register(staticBackend);
        assertSame(staticBackend, registry.getBackend(FAKE_STATIC_ID));
    }

    @Test
    public void testDuplicateIdDifferentInstanceFailsFast() {
        FakeStaticBackend first = new FakeStaticBackend(Collections.emptySet());
        FakeStaticBackend second = new FakeStaticBackend(Collections.emptySet());
        registry.register(first);
        NopException e = assertThrows(NopException.class, () -> registry.register(second));
        assertTrue(e.getMessage().contains(FAKE_STATIC_ID));
        assertSame(first, registry.getBackend(FAKE_STATIC_ID));
    }

    @Test
    public void testCapabilitySlotConflictFailsFast() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Collections.emptySet());
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        registry.register(staticBackend);
        registry.register(dynamicBackend);
        assertSame(staticBackend, registry.findStaticBackend());
        assertSame(dynamicBackend, registry.findDynamicBackend());

        // 第三个后端占用已被占用的 STATIC_GENERATED 槽位 = 契约非法
        FakeStaticBackend conflict = new FakeStaticBackend(Collections.emptySet()) {
            @Override
            public String getBackendId() {
                return "conflict";
            }
        };
        assertThrows(NopException.class, () -> registry.register(conflict));
    }

    @Test
    public void testInvalidContractFailsFast() {
        assertThrows(NopException.class, () -> registry.register(null));

        IEvalExecutionBackend blankId = new FakeStaticBackend(Collections.emptySet()) {
            @Override
            public String getBackendId() {
                return "";
            }
        };
        assertThrows(NopException.class, () -> registry.register(blankId));

        IEvalExecutionBackend noCapability = new FakeStaticBackend(Collections.emptySet()) {
            @Override
            public java.util.Set<EvalBackendCapability> getCapabilities() {
                return Collections.emptySet();
            }
        };
        assertThrows(NopException.class, () -> registry.register(noCapability));
    }

    @Test
    public void testUnavailableEntryQueryable() {
        FakeDynamicBackend unavailable = new FakeDynamicBackend();
        unavailable.markUnavailable("engine-init-failed: injected");
        // 初始化失败 → 不可用条目注册成功（不抛出、不阻断启动）
        registry.register(unavailable);
        assertFalse(unavailable.isAvailable());
        assertEquals("engine-init-failed: injected", unavailable.getUnavailableReason());

        assertEquals(Collections.singletonMap(FAKE_DYNAMIC_ID, "engine-init-failed: injected"),
                registry.getUnavailableBackends());

        // 可用后端不出现在不可用条目查询中
        FakeStaticBackend available = new FakeStaticBackend(Collections.emptySet());
        registry.register(available);
        assertEquals(Collections.singletonMap(FAKE_DYNAMIC_ID, "engine-init-failed: injected"),
                registry.getUnavailableBackends());
    }

    @Test
    public void testUnregisterNonOwnerIsNoop() {
        FakeStaticBackend owner = new FakeStaticBackend(Collections.emptySet());
        FakeStaticBackend nonOwner = new FakeStaticBackend(Collections.emptySet());
        registry.register(owner);
        registry.unregister(nonOwner);
        assertSame(owner, registry.getBackend(FAKE_STATIC_ID));
    }
}
