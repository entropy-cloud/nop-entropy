/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.junit;

import io.nop.api.core.util.Guard;
import io.nop.autotest.core.data.AutoTestDataHelper;
import io.nop.commons.util.ArrayHelper;
import io.nop.commons.util.MavenDirHelper;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class VariantsArgumentProvider implements ArgumentsProvider, AnnotationConsumer<EnableVariants> {
    private String[] variants;

    @Override
    public void accept(EnableVariants enableVariants) {
        variants = enableVariants.value();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {

        Class<?> testClass = context.getTestClass().orElse(null);
        Method testMethod = context.getTestMethod().orElse(null);

        if(testClass == null || testMethod == null)
            throw new IllegalArgumentException("null test info");

        String dataDir = AutoTestDataHelper.getTestDataPath(testClass, testMethod);

        File casesDir = new File(MavenDirHelper.projectDir(testClass), "cases");

        File variantsDir = new File(casesDir, dataDir + "/variants");
        List<String> ret = new ArrayList<>();
        ret.add("_default");

        variantsDir.mkdirs();

        if (variantsDir.exists()) {
            String[] names = variantsDir.list();
            if (names != null) {
                Arrays.sort(names);
                ret.addAll(Arrays.asList(names));
            }
        }

        if(variants.length != 0){
            return ret.stream().filter(a-> ArrayHelper.indexOf(variants,a)>=0).map(Arguments::of);
        }

        return ret.stream().map(Arguments::of);
    }
}
