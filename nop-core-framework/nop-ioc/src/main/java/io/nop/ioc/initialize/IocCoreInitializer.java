/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.initialize;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.ioc.IocConfigs;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;

import static io.nop.core.CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL;
import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_ANALYZE;

public class IocCoreInitializer implements ICoreInitializer {
    private IBeanContainerImplementor appContainer;
    private IBeanContainer parentContainer;

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_IOC;
    }

    @Override
    public boolean isEnabled() {
        return IocConfigs.CFG_IOC_ENABLED.get();
    }

    @Override
    public void initialize() {
        if (BeanContainer.isInitialized())
            parentContainer = BeanContainer.instance();

        appContainer = new AppBeanContainerLoader().loadAppContainer(parentContainer);
        BeanContainer.registerInstance(appContainer);

        if (CFG_CORE_MAX_INITIALIZE_LEVEL.get() > INITIALIZER_PRIORITY_ANALYZE) {
            try {
                appContainer.start();
                // concurrent-start 框架级 join（plan 2507 裁定①）：与 start() 同一 try 界内有界等待
                // 启动图终态。失败终态/join 超时经 awaitStartFinished 类型化抛出 → 本 catch 恢复
                // parentContainer 后重抛 → CoreInitialization.initializeTo → NopApplication.run()
                // 自然传播（主任务不启动、进程非零退出；NopApplication 零改动）。
                appContainer.awaitStartFinished();
            } catch (Exception e) {
                BeanContainer.registerInstance(parentContainer);
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void destroy() {
        if (this.appContainer != null) {
            IBeanContainerImplementor container = this.appContainer;
            this.appContainer = null;
            BeanContainer.registerInstance(parentContainer);
            container.stop();
        }
    }
}