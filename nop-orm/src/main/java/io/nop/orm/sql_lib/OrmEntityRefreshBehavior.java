package io.nop.orm.sql_lib;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;

@Locale("zh-CN")
public enum OrmEntityRefreshBehavior {
    @Description("保持第一次加载的值，忽略后续所有加载的值。这是ORM引擎加载实体数据的方式")
    useFirst,

    @Description("使用当前加载的最新值")
    useLast,

    @Description("加载数据时发现内存中的记录已经被修改则抛出异常，否则使用当前加载的最新值")
    errorWhenDirty,
}
