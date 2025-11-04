package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_LABEL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestFilterBeans {

    @Test
    public void testFromFilterBeanList_deepNesting() {
        // 构造嵌套 FilterBean 列表：and -> or -> eq，and -> eq
        List<FilterBean> beans = new ArrayList<>();

        // and (level=1)
        FilterBean root = new FilterBean();
        root.setLevel(1);
        // logic为空，默认and
        beans.add(root);

        // or (level=3)
        FilterBean orNode = new FilterBean();
        orNode.setLevel(3);
        orNode.setLogic("or");
        beans.add(orNode);

        // eq c=4,vC,C (level=5)
        FilterBean eqC = new FilterBean();
        eqC.setLevel(5);
        eqC.setOp("eq");
        eqC.setName("c");
        eqC.setValue(4);
        eqC.setValueName("vC");
        eqC.setLabel("C");
        beans.add(eqC);

        // eq d=100 (level=3) (与or同级，挂root的and下)
        FilterBean eqD = new FilterBean();
        eqD.setLevel(3);
        eqD.setOp("eq");
        eqD.setName("d");
        eqD.setValue(100);
        beans.add(eqD);

        // 执行
        TreeBean tree = FilterBeans.fromFilterBeanList(beans);

        // 验证根节点
        assertEquals("and", tree.getTagName());
        assertNotNull(tree.getChildren());
        assertEquals(2, tree.getChildren().size(), "root-children");

        // 第一个子节点是or，内有一个eq
        TreeBean orTree = tree.getChildren().get(0);
        assertEquals("or", orTree.getTagName());
        assertNotNull(orTree.getChildren());
        assertEquals(1, orTree.getChildren().size(), "sub-child");
        TreeBean eqTree = orTree.getChildren().get(0);
        assertEquals("eq", eqTree.getTagName());
        assertEquals("c", eqTree.getAttr(FILTER_ATTR_NAME));
        assertEquals(4, eqTree.getAttr(FILTER_ATTR_VALUE));
        assertEquals("vC", eqTree.getAttr(FILTER_ATTR_VALUE_NAME));
        assertEquals("C", eqTree.getAttr(FILTER_ATTR_LABEL));

        // 第二个子节点是 eq d=100
        TreeBean eqDTree = tree.getChildren().get(1);
        assertEquals("eq", eqDTree.getTagName());
        assertEquals("d", eqDTree.getAttr(FILTER_ATTR_NAME));
        assertEquals(100, eqDTree.getAttr(FILTER_ATTR_VALUE));
        assertNull(eqDTree.getAttr(FILTER_ATTR_VALUE_NAME));
        assertNull(eqDTree.getAttr(FILTER_ATTR_LABEL));
    }

}
