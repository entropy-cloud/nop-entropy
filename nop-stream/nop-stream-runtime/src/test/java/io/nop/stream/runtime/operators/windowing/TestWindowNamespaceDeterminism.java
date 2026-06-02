package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class TestWindowNamespaceDeterminism {

    private static class CustomWindow extends Window {
        private final String label;

        CustomWindow(String label) {
            this.label = label;
        }

        @Override
        public long maxTimestamp() {
            return Long.MAX_VALUE;
        }

        @Override
        public String toString() {
            return "CustomWindow[" + label + "]";
        }
    }

    private String invokeWindowNamespace(Window window) throws Exception {
        Object operator = allocateInstance();
        Method m = WindowOperator.class.getDeclaredMethod("windowNamespace", Window.class);
        m.setAccessible(true);
        return (String) m.invoke(operator, window);
    }

    @SuppressWarnings("unchecked")
    private Object allocateInstance() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        java.lang.reflect.Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocate.invoke(unsafe, WindowOperator.class);
    }

    @Test
    public void nonTimeWindowNamespaceIsDeterministicAcrossCalls() throws Exception {
        CustomWindow w = new CustomWindow("test-label");

        String ns1 = invokeWindowNamespace(w);
        String ns2 = invokeWindowNamespace(w);

        assertNotNull(ns1);
        assertEquals(ns1, ns2, "windowNamespace must return the same value for the same window instance");
        assertTrue(ns1.contains("CustomWindow[test-label]"),
                "namespace should use toString(), not identityHashCode");
    }

    @Test
    public void nonTimeWindowNamespaceDoesNotUseIdentityHashCode() throws Exception {
        CustomWindow w = new CustomWindow("stable");
        String ns = invokeWindowNamespace(w);

        assertFalse(ns.contains("@"),
                "namespace must not contain '@' (identityHashCode pattern)");
        assertTrue(ns.startsWith(w.getClass().getName() + "#"),
                "namespace should use ClassName#toString() format");
    }

    @Test
    public void nullWindowReturnsConstant() throws Exception {
        String ns = invokeWindowNamespace(null);
        assertEquals("_null_window_", ns);
    }

    @Test
    public void timeWindowNamespaceUsesStartEnd() throws Exception {
        String ns = invokeWindowNamespace(new TimeWindow(1000, 2000));
        assertTrue(ns.startsWith("TW:"));
        assertTrue(ns.contains("1000") && ns.contains("2000"));
    }
}
