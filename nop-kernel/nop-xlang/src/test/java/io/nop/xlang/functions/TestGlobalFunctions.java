package io.nop.xlang.functions;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGlobalFunctions {

    @Test
    public void testBeanExists() {
        IEvalScope scope = XLang.newEvalScope();
        scope.setBeanProvider(new IBeanProvider() {
            @Override
            public boolean containsBean(String name) {
                return "myBean".equals(name);
            }

            @Override
            public Object getBean(String name) {
                return null;
            }

            @Override
            public <T> T getBeanByType(Class<T> clazz) {
                return null;
            }

            @Override
            public String getBeanScope(String name) {
                return null;
            }
        });

        assertTrue(GlobalFunctions.beanExists(scope, "myBean"));
        assertFalse(GlobalFunctions.beanExists(scope, "otherBean"));
    }
}