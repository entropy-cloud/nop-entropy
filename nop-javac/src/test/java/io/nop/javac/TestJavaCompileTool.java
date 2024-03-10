/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaCompileTool {
    @Test
    public void testParse() {
        File file = FileHelper.getClassPathFile("_FunctionDeclaration.java");
        String text = FileHelper.readText(file, null);

        IJavaCompileResult result = JavaCompileTool.instance().parseJavaSource(null, text);
        System.out.println(result.getFormattedSource());

        String source = JavaCompileTool.instance().formatJavaSource(null, text);
        assertEquals(source, result.getFormattedSource());
    }

    @Test
    public void testError() {
        File file = FileHelper.getClassPathFile("_CompileError.java");
        String text = FileHelper.readText(file, null);

        try {
            JavaCompileTool.instance().parseJavaSource(null, text);
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(6, e.getErrorLocation().getLine());
        }
    }

    @Test
    public void defineClass() {
        DynamicURLClassLoader loader = new DynamicURLClassLoader("a", this.getClass().getClassLoader());

    }
}
