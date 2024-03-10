/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.weed.model;

import lombok.Data;
import org.noear.weed.annotation.PrimaryKey;
import org.noear.weed.annotation.Table;

@Data
@Table("sys_customer")
public class WeedSysCustomer {
    @PrimaryKey
    private Integer id;
    private String code;
    private String name;

    // @FetchMany("customerId")
    // private List<BeetlSysOrder> order;
}
