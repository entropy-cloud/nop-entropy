/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICloneable;

import java.io.Serializable;

@DataBean
@GraphQLObject
@SuppressWarnings("PMD.UselessParentheses")
public class OrderFieldBean implements Serializable, ICloneable {
    private static final long serialVersionUID = -6865693009305659956L;
    private String owner;

    private String name;

    // 缺省为升序
    private boolean desc;
    private Boolean nullsFirst;

    /**
     * <b>⚠️ 仅供调试打印，勿把本方法的输出当作可执行 SQL。</b>
     *
     * <p>本方法会渲染 {@code " nulls first"} / {@code " nulls last"}，但<b>不询问目标方言
     * 是否支持该语法</b>（{@code nop-api-core} 在依赖链最底层，无法引用 {@code IDialect}）。
     * 在 MySQL 等库上按此拼 SQL 会报语法错误。可执行 SQL 请走 ORM 查询通道
     * （按 plan 2309 接入 {@code supportNullsFirst} 能力位）。
     */
    public String toString() {
        Boolean nullsFirst = getNullsFirst();
        return (owner == null ? "" : owner + ".") + name + " " + (desc ? "desc" : "asc") +
                (nullsFirst != null ? (getNullsFirst() ? " nulls first" : " nulls last") : "");
    }

    public static OrderFieldBean desc(String name) {
        return orderBy(name, true);
    }

    public static OrderFieldBean asc(String name) {
        return orderBy(name, false);
    }

    public static OrderFieldBean orderBy(String name, boolean desc) {
        return forField(name, desc);
    }

    public static OrderFieldBean forField(String name, boolean desc) {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setName(name);
        ret.setDesc(desc);
        return ret;
    }

    public static OrderFieldBean forField(String name) {
        return forField(name, false);
    }

    public static OrderFieldBean fromTreeBean(ITreeBean tree) {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setName((String) tree.getAttr("name"));
        ret.setDesc(ConvertHelper.toPrimitiveBoolean(tree.getAttr("desc")));
        ret.setNullsFirst(ConvertHelper.toBoolean(tree.getAttr("nullsFirst")));
        return ret;
    }

    @Override
    public OrderFieldBean cloneInstance() {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setOwner(owner);
        ret.setName(name);
        // 克隆保持排序方向不变，反转语义属于reverse()
        ret.setDesc(desc);
        ret.setNullsFirst(nullsFirst);
        return ret;
    }

    public OrderFieldBean reverse() {
        OrderFieldBean ret = new OrderFieldBean();
        ret.setOwner(owner);
        ret.setName(name);
        ret.setDesc(!desc);
        if (nullsFirst != null)
            ret.setNullsFirst(!nullsFirst);
        return ret;
    }

    @PropMeta(propId = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Guard.notEmpty(name, "name");
    }

    @PropMeta(propId = 2)
    public boolean isDesc() {
        return desc;
    }

    public void setDesc(boolean desc) {
        this.desc = desc;
    }

    @JsonIgnore
    public boolean isAsc() {
        return !desc;
    }

    @PropMeta(propId = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @PropMeta(propId = 4)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getNullsFirst() {
        return nullsFirst;
    }

    /**
     * <b>⚠️ 这是内存比较语义，不是 SQL 空序语义。</b>
     *
     * <p>本方法用单个布尔推导「升序时 null 在前」，是 <b>SQL 层被明确否决的反模式</b>
     * （DuckDB 的 ASC 与 DESC 都不翻转缺省，单布尔无法表达真实空序模型）。
     * 故其调用方限于内存排序比较器（{@code OrderByComparator} 等），
     * <b>SQL 产出路径不得使用</b>。
     */
    public boolean shouldNullsFirst() {
        if (nullsFirst != null)
            return nullsFirst;
        return !desc;
    }

    public void setNullsFirst(Boolean value) {
        nullsFirst = value;
    }

}