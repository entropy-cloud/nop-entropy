/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util.objects;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;

import java.io.Serializable;

public class PropPath implements Serializable, IJsonString {
    private final String name;
    private final PropPath next;

    private String fullName;

    public PropPath(String name, PropPath next) {
        this.name = Guard.notEmpty(name, "name");
        this.next = next;
    }

    public PropPath(String name) {
        this(name, null);
    }

    @StaticFactoryMethod
    public static PropPath parse(String str) {
        int pos = str.indexOf('.');
        if (pos < 0) {
            return new PropPath(str, null);
        }
        return new PropPath(str.substring(0, pos), parse(str.substring(pos + 1)));
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof PropPath))
            return false;

        PropPath other = (PropPath) o;
        return getFullName().equals(other.getFullName());
    }

    public String toString() {
        return getFullName();
    }

    public String getFullName() {
        if (next == null)
            return name;

        if (fullName == null) {
            StringBuilder sb = new StringBuilder();
            getFullName(sb);
            fullName = sb.toString();
        }
        return fullName;
    }

    public void getFullName(StringBuilder sb) {
        sb.append(name);
        if (next != null) {
            sb.append('.');
            next.getFullName(sb);
        }
    }

    public String getName() {
        return name;
    }

    public PropPath getNext() {
        return next;
    }

    public PropPath getOwner() {
        if (next == null)
            return null;

        return new PropPath(name, next.getOwner());
    }

    public String getLast() {
        if (next == null)
            return name;
        return next.getLast();
    }
}