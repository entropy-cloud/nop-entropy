/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.regex.impl;

import io.nop.commons.text.regex.IRegex;

import java.util.regex.Pattern;

public class JdkRegex implements IRegex {
    private final Pattern pattern;

    public JdkRegex(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean test(String text) {
        return pattern.matcher(text).matches();
    }
}