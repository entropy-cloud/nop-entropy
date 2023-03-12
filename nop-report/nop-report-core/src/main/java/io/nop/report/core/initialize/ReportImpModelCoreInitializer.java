/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.initialize;

import io.nop.api.core.util.Guard;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdsl.DslModelHelper;

import java.util.Collection;

public class ReportImpModelCoreInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
        Collection<? extends IResource> resources =
                VirtualFileSystem.instance().getAllResources("/nop/report/imp-model", "");

        for (IResource resource : resources) {
            String fileName = resource.getName();
            String impModelPath = ResourceHelper.readText(resource).trim();

            if (StringHelper.isEmpty(impModelPath) || impModelPath.equals("ignore"))
                continue;

            Guard.checkArgument(StringHelper.isValidFilePath(impModelPath), "Invalid Imp Model File Path");

            cancellable.appendOnCancelTask(ResourceComponentManager.instance().registerComponentModelLoader(fileName,
                    fileName + ".xlsx", DslModelHelper.newExcelModelLoader(impModelPath), true));
        }
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}