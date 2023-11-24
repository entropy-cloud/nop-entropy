/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.junit;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.autotest.core.AutoTestCase;
import io.nop.autotest.core.data.AutoTestDataHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.lang.reflect.Method;

import static io.nop.autotest.core.AutoTestConfigs.CFG_AUTOTEST_DISABLE_SNAPSHOT;
import static io.nop.autotest.core.AutoTestConfigs.CFG_AUTOTEST_FORCE_SAVE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({NopJunitExtension.class, NopJunitParameterResolver.class})
public abstract class JunitAutoTestCase extends AutoTestCase {

    protected TestInfo testInfo;

    @BeforeEach
    public void init(TestInfo testInfo) {
        this.testInfo = testInfo;
        this.beginTestCase();
        initVariant(testInfo);
        String path = getCaseDataPath(testInfo);
        File caseDataDir = new File(getCasesDir(), path);
        configExecutionMode(testInfo);
        initCaseDataDir(caseDataDir);
        initBeans();
        initDao();
        runLazyActions();
    }

    void initVariant(TestInfo testInfo) {
        String displayName = testInfo.getDisplayName();
        if (displayName.startsWith("[") && displayName.indexOf(']') > 0) {
            int pos = displayName.indexOf(']');
            String variant = displayName.substring(pos + 1).trim();
            setVariant(variant);
        }
    }

    @Override
    public String getTestMethod() {
        Method method = testInfo.getTestMethod().orElse(null);
        return method == null ? null : method.getName();
    }

    @AfterEach
    public void destroy(ExtensionContext ctx) {
        complete(!ctx.getExecutionException().isPresent());
    }

    protected void configExecutionMode(TestInfo testInfo) {
        EnableSnapshot enableSnapshot = testInfo.getTestMethod().get().getAnnotation(EnableSnapshot.class);
        if (enableSnapshot != null && !CFG_AUTOTEST_DISABLE_SNAPSHOT.get()) {
            setCheckOutput(enableSnapshot.checkOutput());
            setLocalDb(enableSnapshot.localDb());
            setSqlInit(enableSnapshot.sqlInit());
            setTableInit(enableSnapshot.tableInit());
            setSaveOutput(enableSnapshot.saveOutput());

            configLocalDb();

            // if (enableSnapshot.localDb()) {
            // NopTestConfig testConfig = testInfo.getTestClass().get().getAnnotation(NopTestConfig.class);
            // if (testConfig == null || !testConfig.localDb()) {
            // throw new NopException(ERR_AUTOTEST_TEST_CLASS_NO_LOCAL_DB_ANNOTATION)
            // .param(ARG_TEST_CLASS, testInfo.getTestClass().get())
            // .param(ARG_TEST_METHOD, testInfo.getTestMethod().get().getName());
            // }
            // }

            if (CFG_AUTOTEST_FORCE_SAVE_OUTPUT.get()) {
                setSaveOutput(true);
                return;
            }
        } else {
            setCheckOutput(false);
            setLocalDb(false);
            setSqlInit(false);
            setTableInit(false);
            setSaveOutput(true);
        }
    }

    protected String getCaseDataPath(TestInfo testInfo) {
        return AutoTestDataHelper.getTestDataPath(testInfo.getTestClass().get(), testInfo.getTestMethod().get());
    }

    protected void checkEquals(ErrorCode errorCode, String fileName, Object expected, Object value) {
        assertEquals(expected, value, "fileName=" + fileName);
    }
}