/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通过反射访问时对应的名称。它将替代java代码中的方法名和字段名在代码中被使用。
 * 例如catch和finally是javascript中合法的方法名，但是在java中是关键字，无法被使用。
 * 通过增加这个注解，可以使得脚本中的方法名与javascript保持一致。
 * <pre>{@code
 *     @Name("catch")
 *     Promise Catch(Function<Throwable,?> fn){
 *          ....
 *     }
 * }</pre>
 *
 * <p>另外java缺省情况下没有保留方法的参数名，通过此注解也可以为参数增加名称
 * <pre>{@code
 *     void myFunc(@Name("value") String value){
 *
 *     }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Name {
    String value();
}
