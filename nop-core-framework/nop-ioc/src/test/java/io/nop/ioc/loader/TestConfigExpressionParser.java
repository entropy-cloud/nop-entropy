package io.nop.ioc.loader;

import io.nop.core.type.PredefinedGenericTypes;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.model.BeanValue;
import org.junit.jupiter.api.Test;

public class TestConfigExpressionParser {
    @Test
    public void testNestedExpr(){
        BeanDefinition beanDef = new BeanDefinition(new BeanValue());
        String expr = "ab${test.my-var}-data";
        ConfigExpressionProcessor.INSTANCE.process(beanDef,null,"a",expr, PredefinedGenericTypes.STRING_TYPE);
    }
}
