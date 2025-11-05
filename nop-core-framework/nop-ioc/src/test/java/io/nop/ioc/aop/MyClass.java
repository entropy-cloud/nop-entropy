/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.aop;

import io.nop.api.core.annotations.txn.Transactional;

import java.io.IOException;
import java.util.List;

public class MyClass {
    private final String data;

    public MyClass(String data) throws IOException {
        this.data = data;
        if ("e".equals(data))
            throw new IOException();
    }

    @Transactional(txnGroup = "test")
    public <T extends List<String>> void myMethod(String arg, int x, byte[] y, T... list) throws IOException {
        System.out.println("myMethod:" + data);
    }

    @Transactional
    protected String innerMethod() {
        return "abc";
    }

    public String simpleMethod(int x) {
        return String.valueOf(x);
    }
}
