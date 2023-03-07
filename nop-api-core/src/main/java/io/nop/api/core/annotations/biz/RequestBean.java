/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.biz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * GraphQL的field的args是一个Map结构，一个情况下每个key对应一个方法参数。
 * 如果需要把它们作为一个整体转型为一个Bean对象，则在方法参数上标注@RequestBean
 *
 * <pre>{@code
 *    type MyEntity{
 *        myField(a:String,b:Int)
 *    }
 *
 * @BizModel("MyEntity")
 * class MyEntityLoader{
 *     @BizLoader("myField")
 *     public Object myField(@ContextSource MyEntity source, @RequestBean MyFieldArgs request){
 *        // request对象中包含a和b两个属性
 *         ...
 *    }
 * }
 *
 * }</pre>
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RequestBean {
}