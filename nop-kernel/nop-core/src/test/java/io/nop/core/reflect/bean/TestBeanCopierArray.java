/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestBeanCopierArray extends BaseTestCase {
    @Test
    public void testCopyToStringArrayShallow() {
        String[] src = {"a", "b", "c"};
        String[] target = new String[3];
        BeanTool.copyProperties(src, target);
        assertArrayEquals(src, target);

        String[] target2 = new String[3];
        BeanTool.copyBean(src, target2, String[].class, false);
        assertArrayEquals(src, target2);
    }

    @Test
    public void testCopyToStringArrayDeep() {
        String[] src = {"a", "b", "c"};
        String[] target = new String[3];
        BeanTool.copyBean(src, target, String[].class, true);
        assertArrayEquals(src, target);
    }

    @Test
    public void testCopyToPrimitiveIntArray() {
        int[] src = {1, 2, 3};
        int[] target = new int[3];
        BeanTool.copyProperties(src, target);
        assertArrayEquals(src, target);
    }

    @Test
    public void testCopyToArrayLengthNotMatch() {
        String[] src = {"a", "b", "c"};
        String[] target = new String[2];
        assertThrows(NopException.class, () -> BeanTool.copyProperties(src, target));
    }

    public static class MyList extends ArrayList<String> {
        private static final long serialVersionUID = 0L;
    }

    @Test
    public void testCopyFromCollectionToArray() {
        MyList src = new MyList();
        src.add("a");
        src.add("b");

        String[] target = new String[2];
        BeanTool.copyBean(src, target, String[].class, false);
        assertArrayEquals(new String[]{"a", "b"}, target);
    }
}
