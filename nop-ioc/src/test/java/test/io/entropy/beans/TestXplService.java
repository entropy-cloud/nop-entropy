/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

public class TestXplService {
    TestXplConfig config;

    int startCount;

    public TestXplConfig getConfig() {
        return config;
    }

    public void setConfig(TestXplConfig config) {
        this.config = config;
    }

    public void restart() {
        startCount++;
    }

    public int getStartCount() {
        return startCount;
    }
}
