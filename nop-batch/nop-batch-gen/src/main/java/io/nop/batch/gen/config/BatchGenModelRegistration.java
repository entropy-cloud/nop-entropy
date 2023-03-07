/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen.config;

import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IComponentModel;
import io.nop.batch.gen.BatchGenConstants;
import io.nop.batch.gen.model.BatchGenModelParser;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

public class BatchGenModelRegistration {
    private ICancellable cancellable;

    @PostConstruct
    public void init() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType(BatchGenConstants.MODEL_TYPE_BATCH_GEN);
        Map<String, IResourceObjectLoader<IComponentModel>> loaders = new HashMap<>();
        IResourceObjectLoader<IComponentModel> loader = path -> new BatchGenModelParser().parseFromVirtualPath(path);
        loaders.put(BatchGenConstants.FILE_TYPE_GEN_JSON, loader);
        loaders.put(BatchGenConstants.FILE_TYPE_GEN_JSON5, loader);
        loaders.put(BatchGenConstants.FILE_TYPE_GEN_YAML, loader);

        ResourceComponentManager.instance().registerComponentModelConfig(config);
    }

    @PreDestroy
    public void destroy() {
        if (cancellable != null) {
            cancellable.cancel();
            cancellable = null;
        }
    }
}
