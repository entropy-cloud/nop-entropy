/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.mybatis.vo;

import lombok.Data;

import java.util.List;

@Data
public class MyBatisSysCustomerView {
    private Integer id;
    private String code;
    private String name;
    private List<MyBatisSysOrder> order;
}
