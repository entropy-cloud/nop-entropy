package io.nop.plugin.manager.impl;

import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C2-007）订阅/退订双侧 compute 原子性载体钉。
 */
public class TestSubscribeChangeOrphan {

    /** pin5pre：顺序对照——单线程订阅→触发必须可达（harness 有效性自证）。 */
    @Test
    public void pin5pre_sequentialBaseline() throws Exception {
        DefinitionConfigProvider provider = newProvider();
        List<String> received = new CopyOnWriteArrayList<>();
        IConfigChangeListener l = (p2, c) -> received.add("x");
        provider.subscribeChange("cfg.*", l);
        invokeNotify(provider, "cfg.x");
        assertTrue(received.contains("x"), "顺序基线：订阅后通知应可达，实际 " + received);
    }

    private DefinitionConfigProvider newProvider() {
        return new DefinitionConfigProvider((IConfigProvider) java.lang.reflect.Proxy
                .newProxyInstance(IConfigProvider.class.getClassLoader(), new Class<?>[] { IConfigProvider.class },
                        (proxy, method, args) -> {
                            if ("subscribeChange".equals(method.getName()))
                                return (Runnable) () -> {
                                };
                            return null;
                        }));
    }

    private void invokeNotify(DefinitionConfigProvider provider, String var) throws Exception {
        java.lang.reflect.Method m = DefinitionConfigProvider.class
                .getDeclaredMethod("notifyListeners", String.class, Object.class);
        m.setAccessible(true);
        m.invoke(provider, var, null);
    }

    /** pin5：交错「退订 × 订阅」后无孤儿——新订阅仍能收到通知（双侧 compute 形态）。 */
    @Test
    public void pin5_noOrphanAfterConcurrentUnsubscribe() throws Exception {
        DefinitionConfigProvider provider = newProvider();
        List<String> received = new CopyOnWriteArrayList<>();
        IConfigChangeListener oldListener = (p, c) -> received.add("old");
        IConfigChangeListener newListener = (p, c) -> received.add("new");

        Runnable cleanupOld = provider.subscribeChange("cfg.*", oldListener);
        Thread t1 = new Thread(cleanupOld::run);
        Thread t2 = new Thread(() -> provider.subscribeChange("cfg.*", newListener));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        invokeNotify(provider, "cfg.x");

        assertTrue(received.contains("new"), "缺陷态：孤儿 list → 新订阅永久失聪");
        assertFalse(received.contains("old"), "已退订监听器不应再收到通知");
    }
}
