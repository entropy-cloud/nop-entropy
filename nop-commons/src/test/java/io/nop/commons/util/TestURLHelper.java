/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestURLHelper {

    @Test
    public void testJar() throws Exception {
        File file = FileHelper.getClassFile(TestURLHelper.class);
        URL url = URLHelper.buildJarURL(new File(file.getParentFile(), "util.zip"), "/util/test.txt");

        System.out.println(url);
        url = new URL(url.toExternalForm());
        assertTrue(URLHelper.exists(url));
    }
}
