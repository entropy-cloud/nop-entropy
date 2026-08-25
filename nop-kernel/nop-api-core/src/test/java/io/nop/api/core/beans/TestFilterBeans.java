package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_LABEL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_ALWAYS_FALSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testOrEmptyIsAlwaysFalse() {
        // 逻辑上空析取为假：空OR返回恒真会让数据过滤/权限条件被绕过
        assertEquals(FILTER_OP_ALWAYS_FALSE, FilterBeans.or().getTagName());
        assertEquals(FILTER_OP_ALWAYS_FALSE, FilterBeans.or(new TreeBean[0]).getTagName());
        assertEquals(FILTER_OP_ALWAYS_FALSE, FilterBeans.or(java.util.Collections.emptyList()).getTagName());
    }

    @Test
    public void testOrSkipsNullElements() {
        // or与and一致地容忍null元素，而不是抛NPE
        TreeBean or = FilterBeans.or(FilterBeans.eq("a", 1), null, FilterBeans.eq("b", 2));
        assertEquals("or", or.getTagName());
        assertEquals(2, or.getChildren().size());

        // 全null输入返回恒假
        assertEquals(FILTER_OP_ALWAYS_FALSE, FilterBeans.or(null, null).getTagName());
    }

    @Test
    public void testOrFlattensOrChildren() {
        TreeBean innerOr = FilterBeans.or(FilterBeans.eq("a", 1), FilterBeans.eq("b", 2));
        TreeBean or = FilterBeans.or(innerOr, FilterBeans.eq("c", 3));
        assertEquals("or", or.getTagName());
        assertEquals(3, or.getChildren().size());
    }

    /**
     * 回归：and(List)/or(List)必须与可变参数版本行为一致——跳过null元素、展平嵌套同名节点，
     * 且不直接复用调用方列表作为children（外部后续修改列表不能改变filter树）。
     */
    @Test
    public void testListOverloadConsistentWithVarargs() {
        List<TreeBean> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(FilterBeans.eq("a", 1));

        TreeBean and = FilterBeans.and(withNull);
        assertEquals("and", and.getTagName());
        assertEquals(1, and.getChildren().size(), "null elements must be skipped");
        assertTrue(FilterBeans.eq("a", 1).treeEquals(and.getChildren().get(0)));

        TreeBean or = FilterBeans.or(withNull);
        assertEquals("or", or.getTagName());
        assertEquals(1, or.getChildren().size());

        // 嵌套AND被展平
        TreeBean innerAnd = FilterBeans.and(FilterBeans.eq("a", 1), FilterBeans.eq("b", 2));
        TreeBean flattened = FilterBeans.and(java.util.Arrays.asList(innerAnd, FilterBeans.eq("c", 3)));
        assertEquals(3, flattened.getChildren().size(), "nested AND must be flattened");

        // 不别名引用调用方列表
        List<TreeBean> mutable = new ArrayList<>();
        mutable.add(FilterBeans.eq("a", 1));
        mutable.add(FilterBeans.eq("b", 2));
        TreeBean aliased = FilterBeans.and(mutable);
        mutable.clear();
        assertEquals(2, aliased.getChildren().size(), "children must not alias caller list");
    }
}
