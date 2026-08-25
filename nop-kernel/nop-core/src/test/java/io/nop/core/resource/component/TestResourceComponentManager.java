/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.core.CoreErrors.ERR_COMPONENT_INVALID_MODEL_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestResourceComponentManager extends BaseTestCase {

    public static class MyCompositeModel implements ICompositeComponentModel {
        private final String name;

        public MyCompositeModel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public SourceLocation getLocation() {
            return null;
        }

        @Override
        public IComponentModel getSubComponent(String name) {
            return new MyCompositeModel("sub-of:" + this.name + ":" + name);
        }
    }

    private ResourceComponentManager newManager() {
        ResourceComponentManager mgr = new ResourceComponentManager(false);

        ComponentModelConfig config = new ComponentModelConfig();
        config.setModelType("test-model");

        Map<String, ComponentModelConfig.LoaderConfig> loaders = new LinkedHashMap<>();
        loaders.put("myext", new ComponentModelConfig.LoaderConfig("myext", null, null, null,
                (IResourceObjectLoader<Object>) path -> new MyCompositeModel("raw:" + path)));
        config.setLoaders(loaders);

        Map<String, IComponentTransformer<Object, Object>> transformers = new LinkedHashMap<>();
        transformers.put("upper", model -> ((MyCompositeModel) model).getName().toUpperCase());
        config.setTransformers(transformers);

        mgr.registerComponentModelConfig(config);
        return mgr;
    }

    @Test
    public void testLoadComponentModelByUrl() {
        ResourceComponentManager mgr = newManager();

        // 无参数
        Object model = mgr.loadComponentModelByUrl("/test/a/my-file.myext");
        assertEquals("raw:/test/a/my-file.myext", ((MyCompositeModel) model).getName());

        // ?transform=xxx 按接口文档解析查询参数
        Object transformed = mgr.loadComponentModelByUrl("/test/a/my-file.myext?transform=upper");
        assertEquals("RAW:/TEST/A/MY-FILE.MYEXT", transformed);

        // ?sub=xxx 获取子组件
        Object sub = mgr.loadComponentModelByUrl("/test/a/my-file.myext?sub=child");
        assertEquals("sub-of:raw:/test/a/my-file.myext:child", ((MyCompositeModel) sub).getName());

        // #fragment 仍然支持
        Object sub2 = mgr.loadComponentModelByUrl("/test/a/my-file.myext#child");
        assertEquals("sub-of:raw:/test/a/my-file.myext:child", ((MyCompositeModel) sub2).getName());
    }

    /**
     * resolve-前缀路径缺少冒号时应抛ERR_COMPONENT_INVALID_MODEL_PATH，
     * 与getModelConfigByModelPath的处理保持一致，而不是把整个路径当作subName继续拼接
     */
    @Test
    public void testResolveModelLoaderRejectsPathWithoutColon() {
        ResourceComponentManager mgr = new ResourceComponentManager(false);
        NopException e = assertThrows(NopException.class,
                () -> mgr.resolveModelLoader("resolve-no-colon", "test-model"));
        assertEquals(ERR_COMPONENT_INVALID_MODEL_PATH.getErrorCode(), e.getErrorCode());
    }
}
