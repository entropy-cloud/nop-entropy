/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.regex.impl;

import io.nop.commons.text.regex.IRegex;
import io.nop.commons.text.regex.IRegexCompiler;

import java.util.regex.Pattern;

public class JdkRegexCompiler implements IRegexCompiler {
    public static final JdkRegexCompiler INSTANCE = new JdkRegexCompiler();

    @Override
    public IRegex compileRegex(String pattern) {
        return new JdkRegex(Pattern.compile(pattern));
    }
}
