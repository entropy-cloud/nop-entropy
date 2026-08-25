package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.gt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * 回归：replaceChild(old, null)的null语义是"删除已存在的old"，
     * old不存在时不能把null追加进children。
     */
    @Test
    public void testReplaceChildNotFoundWithNull() {
        TreeBean bean = new TreeBean("and");
        TreeBean child = eq("a", 1);
        bean.addChild(child);

        // old不存在 + new为null：children保持不变，不能混入null
        bean.replaceChild(eq("other", 2), null);
        assertEquals(1, bean.getChildren().size());
        assertTrue(child.treeEquals(bean.getChildren().get(0)));

        // old存在 + new为null：删除old
        bean.replaceChild(child, null);
        assertEquals(0, bean.getChildren().size());

        // old不存在 + new非null：追加
        TreeBean added = gt("b", 2);
        bean.replaceChild(eq("other", 2), added);
        assertEquals(1, bean.getChildren().size());
        assertTrue(added.treeEquals(bean.getChildren().get(0)));
    }

    /**
     * 回归：无参构造的TreeBean tagName为null，treeEquals不能对其NPE。
     */
    @Test
    public void testTreeEqualsOnDefaultConstructedBean() {
        TreeBean empty1 = new TreeBean();
        TreeBean empty2 = new TreeBean();
        assertTrue(empty1.treeEquals(empty2));

        TreeBean named = new TreeBean("and");
        assertFalse(empty1.treeEquals(named));
        assertFalse(named.treeEquals(empty1));
        assertTrue(named.treeEquals(new TreeBean("and")));
    }
}
