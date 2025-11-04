/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import java.util.HashSet;
import java.util.Set;

import static io.nop.commons.util.CollectionHelper.addToSet;
import static io.nop.commons.util.CollectionHelper.immutableSet;

public class Keywords {
    public static final Set<String> JAVA = CollectionHelper.buildImmutableSet("class", "interface", "float", "int",
            "long", "double", "byte", "short", "void", "boolean", "char", //
            "true", "false", "null", //
            "if", "else", "while", "for", "switch", "do", "default", "case", "continue", "return", "break", //
            "protected", "public", "private", //
            "static", "final", "abstract", "synchronized", //
            "extends", "implements", //
            "enum", "new", "this", "super", "instanceof", //
            "throws", "throw", "try", "catch", "finally", //
            "import", "package");

    public static final Set<String> JAVASCRIPT = CollectionHelper.buildImmutableSet("break", "do", "instanceof",
            "typeof", "case", "else", "new", "var", "catch", "finally", "return", "void", "continue", "for", "switch",
            "while", "debugger", "function", "this", "with", "default", "if", "throw", "delete", "in", "try",
            // =======================ECMA-262=============

            "abstract", "enum", "int", "short", "boolean", "export", "interface", "static", "byte", "extends", "long",
            "super", "char", "final", "native", "synchronized", "class", "float", "package", "throws", "const", "goto",
            "private", "transient", "debugger", "implements", "protected", "volatile", "double", "import", "public",
            // ========================ECMA-262 第5版严格模式====
            "implements", "package", "public", "interface", "private", "static", "let", "protected", "yield"
            // =====
    );

    public static final Set<String> XLANG = immutableSet(addToSet(new HashSet<>(JAVA), JAVASCRIPT));
}
