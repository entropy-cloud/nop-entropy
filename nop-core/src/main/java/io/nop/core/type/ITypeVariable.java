/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

/**
 * 类型变量仅仅是一个变量名，例如
 *
 * <pre>{@code
 * <T extends List<?>> int count(T arg);
 * }</pre>
 * <p>
 * arg的类型为ITypeVariable。同名的TypeVariable永远相等
 */
public interface ITypeVariable extends IGenericType {
    String getName();
}