/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.meta;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 使用java的@Deprecated注解
 * <p>
 * 如果属性为interface或者abstract类，则对应xdef:body-type="union"。如果为List则是有序列表需要保持元素顺序。如果是Set表示是无序集合。
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface PropMeta {

    String displayName() default "";

    String description() default "";

    int propId() default -1;

    String domain() default "";

    /**
     * 是否允许非空
     */
    boolean mandatory() default false;

    /**
     * 是否内部属性。内部属性不出现在IDE的提示列表中，一般情况下在界面上也不可见。
     */
    boolean internal() default false;

    /**
     * 对于集合属性，指定集合中元素的唯一键属性，例如id,name等
     */
    String keyProp() default "";

    String orderProp() default "";

    /**
     * 转换为xml属性或者节点时对应的标签名，一般情况下与属性名一致
     */
    String xmlName() default "";

    String childXmlName() default "";

    /**
     * 对于集合节点，指定集合元素的属性名，用于生成getXXX这样的帮助函数
     */
    String childName() default "";

    /**
     * 缺省override配置
     */
    String defaultOverride() default "";

    /**
     * 获取本字段的值的时候，需要依赖其他字段。例如在批量加载的时候，表示需要把相关字段也进行批量加载
     */
    String[] depends() default {};

    /**
     * 对应数据字典的名称。可以是常量类的类名，或者dict定义文件的路径
     */
    String dict() default "";

    int precision() default -1;

    int scale() default -1;

    /**
     * 正则字符串模式
     */
    String pattern() default "";

    double min() default Double.MIN_VALUE;

    double max() default Double.MAX_VALUE;

    boolean excludeMin() default false;

    boolean excludeMax() default false;

    int minLength() default 0;

    int maxLength() default Integer.MAX_VALUE;

    int multipleOf() default 0;

    int minItems() default 0;

    int maxItems() default Integer.MAX_VALUE;

    /**
     * 扩展验证条件，xml格式
     */
    String validate() default "";

    /**
     * 编辑页面上的提示信息
     */
    String hint() default "";

    /**
     * 导出时执行的格式转换代码
     */
    String exportExpr() default "";

    /**
     * 导入时需要执行的格式解析和转换代码
     */
    String importExpr() default "";

    /**
     * 动态计算属性的取值函数
     */
    String getter() default "";

    /**
     * 动态计算属性的设值函数
     */
    String setter() default "";

    boolean computed() default false;

    boolean get() default true;

    boolean set() default true;
}
