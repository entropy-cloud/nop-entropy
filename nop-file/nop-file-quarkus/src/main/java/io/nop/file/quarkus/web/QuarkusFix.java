/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.quarkus.web;

import com.sun.mail.util.LineInputStream;

import java.io.ByteArrayInputStream;

public class QuarkusFix {
    public void load() {
        new LineInputStream(new ByteArrayInputStream(new byte[0]));
    }
}
