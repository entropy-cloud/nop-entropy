/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Option;

public enum GenericClassKind {
    @Option("interface")
    INTERFACE,

    @Label("abstract class")
    @Option("abstract-class")
    ABSTRACT_CLASS,

    @Option("class")
    CLASS,

    @Option("enum")
    ENUM,

    @Option("annotation")
    ANNOTATION;

    public boolean isInterface() {
        return this == INTERFACE || this == ANNOTATION;
    }

    public boolean isAbstract() {
        return isInterface() || this == ABSTRACT_CLASS;
    }

    public boolean isConcrete() {
        return this == CLASS || this == ENUM;
    }

    public String getText() {
        switch (this) {
            case INTERFACE:
                return "interface";
            case ABSTRACT_CLASS:
                return "abstract class";
            case CLASS:
                return "class";
            case ENUM:
                return "enum";
            case ANNOTATION:
                return "annotation";
        }
        return "unknown";
    }
}
