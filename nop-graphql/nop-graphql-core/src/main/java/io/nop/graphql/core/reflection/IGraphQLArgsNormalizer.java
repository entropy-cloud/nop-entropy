package io.nop.graphql.core.reflection;

import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.Map;

/**
 * 对前台传入的参数进行规范化之后再传给服务函数
 */
public interface IGraphQLArgsNormalizer {
    Map<String, Object> normalize(Map<String, Object> args, IGraphQLExecutionContext context);
}
