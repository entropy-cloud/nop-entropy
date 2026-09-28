package io.nop.code.core.framework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 框架模式描述（N5.3 DSL 模型）：入口点注解/类后缀/名称模式 + 路由约定。
 * namePatterns 为声明性元数据（经 getNamePatterns() 暴露，不参与入口点判定）。
 */
public class FrameworkPatternModel {
    private String name;
    private final List<String> annotationFqns = new ArrayList<>();
    private final List<String> classSuffixes = new ArrayList<>();
    private final List<String> namePatterns = new ArrayList<>();
    private final List<String> mappingAnnotations = new ArrayList<>();
    private final List<String> classPrefixAnnotations = new ArrayList<>();
    private final Map<String, String> httpMethodMappings = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotationFqns() {
        return annotationFqns;
    }

    public List<String> getClassSuffixes() {
        return classSuffixes;
    }

    public List<String> getNamePatterns() {
        return namePatterns;
    }

    public List<String> getMappingAnnotations() {
        return mappingAnnotations;
    }

    public List<String> getClassPrefixAnnotations() {
        return classPrefixAnnotations;
    }

    public Map<String, String> getHttpMethodMappings() {
        return httpMethodMappings;
    }
}
