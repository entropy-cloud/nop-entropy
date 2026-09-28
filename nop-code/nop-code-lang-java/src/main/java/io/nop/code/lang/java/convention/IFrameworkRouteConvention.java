package io.nop.code.lang.java.convention;

import java.util.Set;

/**
 * 框架路由约定（N5.2 外置）：语言分析器不内嵌任何具体框架的路由知识，
 * 注解集合 / class-prefix 注解 / HTTP 方法映射由约定实现提供。
 */
public interface IFrameworkRouteConvention {

    /** 触发路由提取的方法级映射注解短名（如 GetMapping）。 */
    Set<String> mappingAnnotations();

    /** 承载路径前缀的类级注解短名（如 RequestMapping）。 */
    Set<String> classPrefixAnnotations();

    /** 映射注解短名 → HTTP 方法（如 GetMapping → GET）。 */
    String httpMethodFor(String mappingAnnotationName);
}
