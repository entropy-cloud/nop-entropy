/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
