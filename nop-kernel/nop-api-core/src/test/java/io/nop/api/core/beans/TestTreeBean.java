package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.gt;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTreeBean {
    @Test
    public void testTransformChild() {
        TreeBean bean = and(and(eq("test", 1), gt("status", 2)));

        bean.transformChild(null, child -> {
            if (!"test".equals(child.getAttr("name"))) {
                return child;
            }
            return null;
        }, true);

        Object json = bean.toJsonObject();
        System.out.println(json);
        assertEquals("{$type=and, $body=[{$type=gt, name=status, value=2}]}", json.toString());
    }
}
