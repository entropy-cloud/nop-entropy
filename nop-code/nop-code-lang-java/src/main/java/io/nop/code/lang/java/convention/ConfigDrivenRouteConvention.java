package io.nop.code.lang.java.convention;

import java.util.HashSet;
import java.util.Set;

import io.nop.code.core.framework.FrameworkPatternModel;

/**
 * N5.3: DSL 驱动的路由约定。未命中的映射注解返回 ""（与硬编码 Spring 实现一致）。
 */
public class ConfigDrivenRouteConvention implements IFrameworkRouteConvention {

    private final FrameworkPatternModel model;

    public ConfigDrivenRouteConvention(FrameworkPatternModel model) {
        this.model = model;
    }

    @Override
    public Set<String> mappingAnnotations() {
        return new HashSet<>(model.getMappingAnnotations());
    }

    @Override
    public Set<String> classPrefixAnnotations() {
        return new HashSet<>(model.getClassPrefixAnnotations());
    }

    @Override
    public String httpMethodFor(String mappingAnnotationName) {
        return model.getHttpMethodMappings().getOrDefault(mappingAnnotationName, "");
    }
}
