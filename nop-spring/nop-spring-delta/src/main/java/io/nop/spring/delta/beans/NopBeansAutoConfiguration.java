/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.delta.beans;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.spring.delta.SpringDeltaErrors;
import io.nop.core.lang.xml.XNode;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslExtendResult;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Import(NopBeansAutoConfiguration.NopBeansRegistrar.class)
@Configuration
public class NopBeansAutoConfiguration {

    public static class NopBeansRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            // 4.4-C4-004（plan 2562 裁定 G）：预初始化守卫——未初始化时 typed 拒绝
            // （rule 24 fail-fast 缺省）。爆破半径显式登记：variant-A 纯 SpringApplication.run
            // 应用（SpringDemoMain 形态，无预初始化）现以静默空 discovery 启动，修复后启动失败。
            if (!CoreInitialization.isInitialized()) {
                throw new NopException(SpringDeltaErrors.ERR_SPRING_DELTA_CORE_NOT_INITIALIZED);
            }

            List<IResource> resources = ModuleManager.instance().findModuleResources(false,"/beans", "beans.xml");
            if (resources.isEmpty())
                return;

            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
            for (IResource resource : resources) {
                if (!resource.getName().startsWith("spring-"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource,"/nop/schema/beans.xdef");
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                Resource springResource = toResource(node);
                reader.loadBeanDefinitions(springResource);
            }
        }

        private Resource toResource(XNode node) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                node.saveToStream(out, StringHelper.ENCODING_UTF8);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
            return new ByteArrayResource(out.toByteArray(), node.resourcePath());
        }
    }

}
