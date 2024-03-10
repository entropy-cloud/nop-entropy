/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.beetl.vo;

import lombok.Data;
import org.beetl.sql.annotation.entity.JsonMapper;
import org.beetl.sql.annotation.entity.ResultProvider;
import org.beetl.sql.core.mapping.join.JsonConfigMapper;

import java.util.List;

@ResultProvider(JsonConfigMapper.class)
@JsonMapper("{'id':'id','code':'code','name':'name'," + "'order':{'id':'o_id','name':'o_name'}}")
// @JsonMapper(resource ="user.jsonMapping")
@Data
public class BeetlSqlSysCustomerView {
    private Integer id;
    private String code;
    private String name;
    private List<BeetlSysOrder> order;
}
