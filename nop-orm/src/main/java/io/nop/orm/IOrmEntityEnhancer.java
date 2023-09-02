/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.orm.model.IEntityModel;

import java.util.Map;

public interface IOrmEntityEnhancer {

    Object internalCompute(IOrmEntity entity, String propName, Map<String, Object> args);

    boolean internalLoad(IOrmEntity entity);

    boolean internalLoadProperty(IOrmEntity entity, int propId);

    void internalMarkDirty(IOrmEntity entity);

    void internalClearDirty(IOrmEntity entity);

    void internalMarkExtDirty(IOrmEntity entity);

    void internalClearExtDirty(IOrmEntity entity);

    IOrmEntity internalLoadRefEntity(IOrmEntity entity, String propName);

    void internalLoadCollection(IOrmEntitySet coll);

    Object initEntityId(IOrmEntity entity);

    IOrmEntity newEntity(String entityName);

    IEntityModel getEntityModel(String entityName);

    IOrmComponent newComponent(String componentName);

    IOrmEntity internalLoad(String entityName, Object id);

    IBeanProvider getBeanProvider();
}