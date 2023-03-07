/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.orm.entity.MyEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@SqlLibMapper
public interface MyMapper {
    MyEntity select(@Name("id") String id);

    /**
     * LongRangeBean类型总是被识别为分页参数，不需要标记参数名
     */
    List<Map<String, Object>> selectPage(LongRangeBean range);

    int insert(@Name("id") String id, @Name("a") String a, @Name("b") int b, @Name("d") LocalDate c);

    int update(@Name("id") String id, @Name("a") String a, @Name("b") String b);

    int delete(@Name("id") String id);

    default int deleteTest(String id) {
        return delete(id);
    }

    // 静态方法不会参与映射
    static int add(int a, int b) {
        return a + b;
    }
}
