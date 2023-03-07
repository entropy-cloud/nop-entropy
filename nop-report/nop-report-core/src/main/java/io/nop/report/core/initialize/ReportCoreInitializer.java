/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.report.core.functions.ReportFunctions;
import io.nop.xlang.xdef.domain.StdDomainRegistry;

public class ReportCoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
    }

    @Override
    public void initialize() {
        cancellable.append(ReportFunctionProvider.INSTANCE.registerStaticFunctions(ReportFunctions.class));
        StdDomainRegistry.instance().registerStdDomainHandler(ReportExprStdDomainHandler.INSTANCE);
        cancellable.appendOnCancelTask(() -> {
            StdDomainRegistry.instance().unregisterStdDomainHandler(ReportExprStdDomainHandler.INSTANCE);
        });

        registerXpt();
    }

    private void registerXpt() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(XptConstants.MODEL_TYPE_XPT);
        IResourceObjectLoader<ExcelWorkbook> loader = XptModelLoader.instance()::loadModel;
        config.loader(XptConstants.FILE_TYPE_XPT_XLSX, loader);
        config.loader(XptConstants.FILE_TYPE_XPT_XML, loader);
        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}