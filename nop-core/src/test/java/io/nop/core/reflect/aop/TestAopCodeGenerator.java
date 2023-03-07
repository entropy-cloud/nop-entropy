/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.unittest.BaseTestCase;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAopCodeGenerator extends BaseTestCase {
    public static class BaseModel<T> {
        @BizMutation
        public T save(T entity) {
            return null;
        }

        @BizMutation
        public void update(T entity) {

        }
    }

    public static class MyModel extends BaseModel<QueryBean> {
        @BizMutation
        @Override
        public void update(QueryBean entity) {

        }


    }

    public static class GenModel extends MyModel {
        private QueryBean default_save(QueryBean entity) {
            return super.save(entity);
        }

        @Override
        public QueryBean save(QueryBean entity) {
            return super.save(entity);
        }
    }

    @Test
    public void testResolveType() throws Exception {
        String code = new AopCodeGenerator().build(MyModel.class, new Class[]{BizMutation.class});
        System.out.println(code);

        JdkJavaCompiler cp = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();

        JavaCompileResult result = cp.compile(AopCodeGenerator.getAopClassName(MyModel.class), code, classPaths);
        System.out.println(result.getErrorMessage());
        assertTrue(result.isSuccess());
        for (String className : result.getGeneratedClassNames()) {
            System.out.println("generated-class:" + className);
            result.getGeneratedClass(className);
        }
        assertEquals(normalizeCRLF(attachmentText("MyModel__aop.java")), normalizeCRLF(code));
    }
}