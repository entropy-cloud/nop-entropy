/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.loader;

import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeansModel;

import java.util.function.Consumer;
import java.util.function.Function;

public interface IBeanContainerBuilder {
    IBeanContainerBuilder addResource(IResource resource);

    IBeanContainerBuilder addBeans(XNode beansNode);

    IBeanContainerBuilder addBeansModel(BeansModel beansModel);

    IBeanContainerBuilder startMode(BeanContainerStartMode startMode);

    <T> IBeanContainerBuilder registerBean(String beanName, Class<T> beanClass,
                                           Function<IBeanContainerImplementor, T> supplier, Consumer<BeanModel> customizer);

    IBeanContainerImplementor build(String containerId);
}