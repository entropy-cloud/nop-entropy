/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.completion;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;

// based on BasePlatformTestCase to get runInDispatchThread() to work
public class TestAutoPopupCompletion extends BasePlatformTestCase {
    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    public void testAutoPopupCompletions() throws Throwable {
        CompletionAutoPopupTester tester = new CompletionAutoPopupTester(myFixture);
        tester.runWithAutoPopupEnabled(() -> {
            myFixture.configureByText("test.xgen", "<root>");

            tester.typeWithPauses("foo");

            tester.joinCompletion();
//            tester.getLookup().getItems();
            // fixme: test completion items via tester.getLookup()
        });
    }
}
