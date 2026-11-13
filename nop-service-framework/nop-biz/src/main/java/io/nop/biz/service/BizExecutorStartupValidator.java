/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.service;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.biz.api.IBizObjectManager;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * 2.3-C4-001（plan 2551 裁定 A②，closure audit Major-2 接线闭环）：xbiz 异步 action
 * 执行器启动期校验的生产挂接点。{@code BizActionService} 本身非 bean（由部署方装配），
 * 本 validator 以独立 bean（depends-on nopBizObjectManager）在容器启动期执行
 * {@link BizActionService#validateExecutors()}——无效 executor 名 / bizSequential 绑定
 * 非分区执行器的组合在启动期 fail-fast。
 */
@Singleton
public class BizExecutorStartupValidator {
    private IBizObjectManager bizObjManager;
    private IBeanContainer beanContainer;

    @Inject
    public void setBizObjManager(IBizObjectManager bizObjManager) {
        this.bizObjManager = bizObjManager;
    }

    @InjectValue(InjectValue.VALUE_BEAN_CONTAINER)
    public void setBeanContainer(IBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @PostConstruct
    public void start() {
        BizActionService service = new BizActionService();
        service.setBizObjManager(bizObjManager);
        service.setBeanContainer(beanContainer);
        service.validateExecutors();
    }
}
