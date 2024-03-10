/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public class PageModelValidator {
    @Inject
    PageProvider pageProvider;

    public void setPageProvider(PageProvider pageProvider) {
        this.pageProvider = pageProvider;
    }

    @PostConstruct
    public void validate() {
        pageProvider.validateAllPages();
    }
}
