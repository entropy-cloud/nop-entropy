/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import jakarta.annotation.PostConstruct;

public class MyInitializingBean extends MyBeanA {

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        this.setInited(true);
    }

}
