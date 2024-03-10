/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.entity;

public class MyEntity {
    int a;
    int b;
    String c;
    Object myDate;

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public Object getMyDate() {
        return myDate;
    }

    public void setMyDate(Object myDate) {
        this.myDate = myDate;
    }
}
