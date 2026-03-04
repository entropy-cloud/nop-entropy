/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.expr;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.nop.config.ConfigErrors.ARG_PROPERTY;
import static io.nop.config.ConfigErrors.ARG_REASON;
import static io.nop.config.ConfigErrors.ARG_RESOLUTION_CHAIN;
import static io.nop.config.ConfigErrors.ARG_VALUE;
import static io.nop.config.ConfigErrors.ERR_CONFIG_EXPR_CIRCULAR_REFERENCE;
import static io.nop.config.ConfigErrors.ERR_CONFIG_EXPR_UNRESOLVED;

/**
 * 解析配置值中的 ${...} 表达式，采用 Spring 语法。
 *
 * <p>支持语法：
 * <ul>
 *   <li>${prop.name} - 引用其他配置</li>
 *   <li>${prop.name:default} - 带默认值</li>
 *   <li>支持嵌套引用和多表达式组合</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * app.base-url=https://api.example.com
 * app.endpoint=${app.base-url}/v1/users  # 解析为 https://api.example.com/v1/users
 *
 * database.host=localhost
 * database.url=jdbc:postgresql://${database.host}:5432/mydb
 *
 * timeout=${connection.timeout:30s}  # 如果 connection.timeout 未定义，使用 30s
 * </pre>
 */
public class ConfigExpressionResolver {

    /**
     * 匹配 ${...} 或 ${...:default} 表达式
     */
    private static final Pattern EXPR_PATTERN = Pattern.compile(
            "\\$\\{(?<name>[^}:]+)(?::(?<default>[^}]*))?\\}"
    );

    /**
     * 最大解析深度，防止无限递归
     */
    private static final int MAX_DEPTH = 10;

    private final IConfigProvider configProvider;

    /**
     * 严格模式：未找到变量时是否抛出异常
     */
    private final boolean strict;

    public ConfigExpressionResolver(IConfigProvider configProvider, boolean strict) {
        this.configProvider = configProvider;
        this.strict = strict;
    }

    /**
     * 解析字符串中的所有 ${...} 表达式
     *
     * @param value 包含可能表达式的字符串
     * @return 解析后的字符串
     */
    public String resolve(String value) {
        return resolve(value, 0, new HashSet<>());
    }

    private String resolve(String value, int depth, Set<String> resolving) {
        // 快速检查：不包含表达式则直接返回
        if (value == null || !value.contains("${")) {
            return value;
        }

        // 防止无限递归
        if (depth > MAX_DEPTH) {
            throw new NopException(ERR_CONFIG_EXPR_UNRESOLVED)
                    .param(ARG_VALUE, value)
                    .param(ARG_REASON, "Exceeded max resolution depth (possible circular reference)");
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = EXPR_PATTERN.matcher(value);
        boolean found = false;

        while (matcher.find()) {
            found = true;
            String propName = matcher.group("name").trim();
            String defaultValue = matcher.group("default");

            // 检测循环引用
            if (resolving.contains(propName)) {
                throw new NopException(ERR_CONFIG_EXPR_CIRCULAR_REFERENCE)
                        .param(ARG_PROPERTY, propName)
                        .param(ARG_RESOLUTION_CHAIN, resolving);
            }

            // 通过 ConfigProvider 获取值
            Object propValue = configProvider.getConfigValue(propName, null);

            String replacement;
            if (propValue != null) {
                // 递归解析嵌套引用
                Set<String> newResolving = new HashSet<>(resolving);
                newResolving.add(propName);
                replacement = resolve(String.valueOf(propValue), depth + 1, newResolving);
            } else if (defaultValue != null) {
                // 使用默认值
                replacement = defaultValue;
            } else if (strict) {
                throw new NopException(ERR_CONFIG_EXPR_UNRESOLVED)
                        .param(ARG_PROPERTY, propName)
                        .param(ARG_VALUE, value);
            } else {
                // 非严格模式，保留原始表达式
                replacement = matcher.group(0);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // 处理可能新生成的表达式（例如默认值中包含表达式）
        String resolved = result.toString();
        if (found && resolved.contains("${") && !resolved.equals(value)) {
            return resolve(resolved, depth + 1, resolving);
        }
        return resolved;
    }
}
