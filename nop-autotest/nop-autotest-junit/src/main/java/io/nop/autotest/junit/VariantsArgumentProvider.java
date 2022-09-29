package io.nop.autotest.junit;

import io.nop.autotest.core.data.AutoTestDataHelper;
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
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getTestClass().get();
        Method testMethod = context.getTestMethod().get();

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

        return ret.stream().map(Arguments::of);
    }
}
