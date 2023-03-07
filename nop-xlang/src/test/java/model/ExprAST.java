/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package model;

import io.nop.xlang.xmeta.impl.SchemaImpl;

import java.util.ArrayList;
import java.util.List;

public class ExprAST {
    public static void main(String[] args) throws Exception {
        System.gc();
        Thread.sleep(1000);
        System.gc();
        System.gc();
        long m1 = Runtime.getRuntime().freeMemory();
        List<SchemaImpl> list = new ArrayList<SchemaImpl>();
        for (int i = 0; i < 1000000; i++) {
            list.add(new SchemaImpl());
        }
        System.gc();
        Thread.sleep(1000);
        System.gc();
        System.gc();
        long m2 = Runtime.getRuntime().freeMemory();

        long diff = m2 - m1;
        System.out.println("used=" + (diff / 1024));
    }
}
