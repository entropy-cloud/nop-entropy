/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package model;

import io.nop.xlang.xmeta.impl.SchemaImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * 草稿测量 fixture（时间确定化 plan 2513-WI7）：GC 稳定等待不再依赖真实时间流逝——
 * 显式清空引用并连续调用 System.gc() 后按确定步骤读取内存。
 */
public class ExprAST {
    public static void main(String[] args) throws Exception {
        System.gc();
        System.gc();
        System.gc();
        long m1 = Runtime.getRuntime().freeMemory();
        List<SchemaImpl> list = new ArrayList<SchemaImpl>();
        for (int i = 0; i < 1000000; i++) {
            list.add(new SchemaImpl());
        }
        // 显式解除引用后 GC：内存测量不依赖 sleep 等待
        list = null;
        System.gc();
        System.gc();
        System.gc();
        long m2 = Runtime.getRuntime().freeMemory();

        long diff = m2 - m1;
        System.out.println("used=" + (diff / 1024));
    }
}
