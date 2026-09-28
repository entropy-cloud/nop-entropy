package io.nop.code.lang.java.convention;

import java.util.Set;

/**
 * Spring 路由约定（N5.2 外置）：原 JavaFileAnalyzer 内部硬编码的映射注解集合、
 * class-prefix 注解与 HTTP 方法映射，原样迁出为可插拔实现。
 */
public class SpringFrameworkRouteConvention implements IFrameworkRouteConvention {

    public static final Set<String> SPRING_MAPPING_ANNOTATIONS = Set.of(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping",
            "DeleteMapping", "PatchMapping");

    @Override
    public Set<String> mappingAnnotations() {
        return SPRING_MAPPING_ANNOTATIONS;
    }

    @Override
    public Set<String> classPrefixAnnotations() {
        return Set.of("RequestMapping");
    }

    @Override
    public String httpMethodFor(String mappingAnnotationName) {
        switch (mappingAnnotationName) {
            case "GetMapping": return "GET";
            case "PostMapping": return "POST";
            case "PutMapping": return "PUT";
            case "DeleteMapping": return "DELETE";
            case "PatchMapping": return "PATCH";
            case "RequestMapping": return "";
            default: return "";
        }
    }
}
