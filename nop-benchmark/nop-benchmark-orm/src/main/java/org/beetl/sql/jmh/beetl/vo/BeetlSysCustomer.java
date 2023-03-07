/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.beetl.vo;

import lombok.Data;
import org.beetl.sql.annotation.entity.AssignID;
import org.beetl.sql.annotation.entity.Table;
import org.beetl.sql.fetch.annotation.Fetch;
import org.beetl.sql.fetch.annotation.FetchMany;

import java.util.List;

/**
 * fetch 测试
 */
@Table(name = "sys_customer")
@Fetch
@Data
public class BeetlSysCustomer {
    @AssignID
    private Integer id;
    private String code;
    private String name;

    @FetchMany("customerId")
    private List<BeetlSysOrder> order;
}
