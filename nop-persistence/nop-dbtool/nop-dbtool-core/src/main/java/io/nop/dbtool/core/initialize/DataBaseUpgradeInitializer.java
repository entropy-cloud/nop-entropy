/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.initialize;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dbtool.core.DataBaseUpgrader;
import io.nop.orm.IOrmSessionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * 数据库自动升级
 * <p/>
 * 在工程内引入依赖 `io.github.entropy-cloud:nop-dbtool-core`
 * 并配置 `nop.orm.db-differ.auto-upgrade-database` 为 `true` 可启用该功能，
 * 可选配置: `nop.orm.db-differ.auto-upgrade-database-specify-query-spaces` 指定需要自动升级的 querySpace 列表，
 * <p/>
 * 由于无法识别表移除、表更名和字段移除、字段重命名，为确保数据不会被误删除，
 * 其不会执行 `drop table` 和 `drop column` 语句，需自行处理。
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-29
 */
public class DataBaseUpgradeInitializer {
    @Inject
    protected IJdbcTemplate jdbcTemplate;
    @Inject
    protected IOrmSessionFactory ormSessionFactory;

    @PostConstruct
    public void init() {
        DataBaseUpgrader upgrader = new DataBaseUpgrader(this.jdbcTemplate, this.ormSessionFactory);
        upgrader.upgrade();
    }
}
