/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import java.lang.reflect.Modifier;

public class ModifierBuilder {
    static final int PUBLIC_MASK = Modifier.PUBLIC & Modifier.PROTECTED & Modifier.PRIVATE;

    private int mod;

    public ModifierBuilder(int mod) {
        this.mod = mod;
    }

    public ModifierBuilder() {
    }

    public static ModifierBuilder begin() {
        return new ModifierBuilder();
    }

    public int end() {
        return mod;
    }

    public ModifierBuilder isPublic() {
        this.mod &= ~PUBLIC_MASK;
        this.mod |= Modifier.PUBLIC;
        return this;
    }

    public ModifierBuilder notPublic() {
        this.mod &= ~Modifier.PUBLIC;
        return this;
    }

    public ModifierBuilder isPrivate() {
        this.mod &= ~PUBLIC_MASK;
        this.mod |= Modifier.PRIVATE;
        return this;
    }

    public ModifierBuilder notPrivate() {
        mod &= ~Modifier.PRIVATE;
        return this;
    }

    public ModifierBuilder isProtected() {
        this.mod &= ~PUBLIC_MASK;
        this.mod |= Modifier.PROTECTED;
        return this;
    }

    public ModifierBuilder notProtected() {
        mod &= ~Modifier.PROTECTED;
        return this;
    }

    public ModifierBuilder isNative() {
        this.mod |= Modifier.NATIVE;
        return this;
    }

    public ModifierBuilder notNative() {
        mod &= ~Modifier.NATIVE;
        return this;
    }

    public ModifierBuilder isAbstract() {
        this.mod |= Modifier.ABSTRACT;
        return this;
    }

    public ModifierBuilder notAbstract() {
        mod &= ~Modifier.ABSTRACT;
        return this;
    }

    public ModifierBuilder isStatic() {
        this.mod |= Modifier.STATIC;
        return this;
    }

    public ModifierBuilder notStatic() {
        mod &= ~Modifier.STATIC;
        return this;
    }

    public ModifierBuilder isFinal() {
        this.mod |= Modifier.FINAL;
        return this;
    }

    public ModifierBuilder notFinal() {
        mod &= ~Modifier.FINAL;
        return this;
    }

    public ModifierBuilder isVolatile() {
        this.mod |= Modifier.VOLATILE;
        return this;
    }

    public ModifierBuilder notVolatile() {
        mod &= ~Modifier.VOLATILE;
        return this;
    }

    public ModifierBuilder isTransient() {
        this.mod |= Modifier.TRANSIENT;
        return this;
    }

    public ModifierBuilder notTransient() {
        mod &= ~Modifier.TRANSIENT;
        return this;
    }
}