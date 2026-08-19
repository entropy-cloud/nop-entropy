/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

import io.nop.xlang.expr.ExprConstants;

/**
 * 生成代码的调用约定（设计 xlang-java 01 §七，janino {@code EvalMethod} 先例同构）。
 *
 * <p>约定内容：
 * <ul>
 * <li>生成入口方法为 static，方法名固定 {@link #ENTRY_METHOD_NAME}，首参固定
 * {@code IEvalScope $scope}（{@link ExprConstants#SYS_VAR_SCOPE}），其后为声明参数；</li>
 * <li>纯表达式单元不追加其他隐参，经 {@code EvalMethodInvoker} 包装为 {@code IEvalFunction}
 * （见 {@link GeneratedEvalBinding}）；</li>
 * <li><b>模板入口包装器契约（I2 定稿，执行路径验证归 I4）</b>：xpl/xlib 有输出语义的编译单元在
 * 声明参数之前追加固定第二隐参 {@code IEvalOutput $out}（{@link #OUT_PARAM}）——live 事实：
 * 输出缓冲由 {@code EvalRuntime} 携带、不经 {@code IEvalScope}，故输出通路必须以隐参承载；
 * 生成代码的输出节点（设计 java §三输出族）经 {@code $out} 发出 API 调用序列。该执行路径的
 * corpus 验证（含模板单元样例覆盖 {@code $out} 通路）属 I4 验收范围。</li>
 * </ul>
 *
 * <p>生成类名从 resourcePath 确定性派生（{@link #generatedClassName}）；生产 {@code _gen/}
 * 产物布局与包名策略由 I11 定稿，测试域以 {@link #GENERATED_PACKAGE} 编译加载。
 */
public final class EvalMethodConvention {

    /**
     * 测试域编译加载使用的生成类包名（生产 {@code _gen/} 布局归 I11 定稿）。
     */
    public static final String GENERATED_PACKAGE = "io.nop.xlang.gen";

    public static final String ENTRY_METHOD_NAME = "execute";

    public static final String SCOPE_PARAM = ExprConstants.SYS_VAR_SCOPE;

    /**
     * xpl/xlib 有输出语义单元的固定第二隐参名（位于声明参数之前；纯表达式单元不追加）。
     */
    public static final String OUT_PARAM = "$out";

    private static final String CLASS_NAME_PREFIX = "Gen_";

    private EvalMethodConvention() {
    }

    /**
     * 从 resourcePath 确定性派生生成类名：非字母数字字符统一映射为 '_'，加 {@code Gen_} 前缀。
     * 同形路径（如 {@code a-b} 与 {@code a_b}）可能折叠为同名，生产清单侧唯一性校验归 I11。
     */
    public static String generatedClassName(String resourcePath) {
        StringBuilder sb = new StringBuilder(resourcePath.length() + 8);
        sb.append(CLASS_NAME_PREFIX);
        for (int i = 0, n = resourcePath.length(); i < n; i++) {
            char c = resourcePath.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
