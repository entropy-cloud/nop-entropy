/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.junit;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.SnapshotTest;
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
        complete(ctx.getExecutionException().isEmpty());
        afterDestroy();
    }

    protected void afterDestroy() {

    }

    protected void configExecutionMode(TestInfo testInfo) {
        Method method = testInfo.getTestMethod().orElse(null);
        if (method == null)
            return;

        NopTestConfig testConfig = this.getClass().getAnnotation(NopTestConfig.class);
        if (testConfig == null)
            throw new IllegalArgumentException("Classes inheriting from JunitAutoTestCase must be annotated with @NopTestConfig.");

        boolean disable = CFG_AUTOTEST_DISABLE_SNAPSHOT.get();
        if (testConfig.snapshotTest() == SnapshotTest.RECORDING)
            disable = true;

        EnableSnapshot enableSnapshot = method.getAnnotation(EnableSnapshot.class);
        if (enableSnapshot != null && !disable) {
            setUseSnapshot(true);
            setCheckOutput(enableSnapshot.checkOutput());
            setLocalDb(enableSnapshot.localDb());
            setSqlInput(enableSnapshot.sqlInput());
            setSqlInit(enableSnapshot.sqlInit());
            setTableInit(enableSnapshot.tableInit());
            setSaveOutput(enableSnapshot.saveOutput());
            configLocalDb();
        } else {
            if (testConfig.snapshotTest() == SnapshotTest.NOT_USE) {
                setUseSnapshot(false);
                setSaveOutput(false);
                setCheckOutput(false);
                setSqlInit(false);
                setTableInit(false);
                setSqlInput(false);
            } else if (testConfig.snapshotTest() == SnapshotTest.RECORDING) {
                setUseSnapshot(true);
                setCheckOutput(false);
                setLocalDb(testConfig.localDb());
                setSqlInit(false);
                setSqlInput(false);
                setTableInit(false);
                setSaveOutput(true);
            } else {
                // CHECKING
                setUseSnapshot(true);
                setCheckOutput(true);
                setLocalDb(true);
                setSqlInput(true);
                setSqlInput(true);
                setTableInit(true);
                setSaveOutput(false);
            }
        }

        if (CFG_AUTOTEST_FORCE_SAVE_OUTPUT.get()) {
            setSaveOutput(true);
        }
    }

    protected String getCaseDataPath(TestInfo testInfo) {
        Class<?> testClass = testInfo.getTestClass().orElse(null);
        Method testMethod = testInfo.getTestMethod().orElse(null);
        if (testClass == null || testMethod == null)
            throw new IllegalArgumentException("null test info");
        return AutoTestDataHelper.getTestDataPath(testClass, testMethod);
    }

    protected void checkEquals(ErrorCode errorCode, String fileName, Object expected, Object value) {
        assertEquals(expected, value, "fileName=" + fileName);
    }
}