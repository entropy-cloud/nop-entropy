/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

public interface OrmEqlConstants {
    String PROP_ID = "id";

    String PREFIX_PLACEHOLDER = "{prefix}";

    String DECORATOR_DUMP = "dump";
    String DECORATOR_QUERY_SPACE = "querySpace";

    String DECORATOR_ENABLE_FILTER = "enableFilter";


    String FUNC_COUNT = "count";

    String MARKER_TENANT_ID = "tenantId";

    String FEATURE_SUPPORT_RETURNING_FOR_UPDATE = "supportReturningForUpdate";
    String FEATURE_SUPPORT_LATERAL = "supportLateral";
    // WI2 BUG-6: full [outer] join 编译期能力门
    String FEATURE_SUPPORT_FULL_JOIN = "supportFullJoin";
    // WI10b（Phase 0 §2.5 终裁）：right join 编译期能力门（checkJoinTypeFeature 扩展分支）
    String FEATURE_SUPPORT_RIGHT_JOIN = "supportRightJoin";
    // WI10b（Phase 0 §2.6 终裁）：行值构造器 (a,b) 编译期能力门（visitSqlMultiValueExpr 覆写）
    String FEATURE_SUPPORT_ROW_VALUE_CONSTRUCTOR = "supportRowValueConstructor";
    String FEATURE_SUPPORT_WINDOW_FRAME_ROWS = "supportWindowFrameRows";
    String FEATURE_SUPPORT_WINDOW_FRAME_RANGE = "supportWindowFrameRange";
    String FEATURE_SUPPORT_WINDOW_FRAME_GROUPS = "supportWindowFrameGroups";

    // WI3 版本门：集合算子关键字的哨兵值。方言 XML 配置 intersectKeyword="none" /
    // exceptKeyword="none" 表示该方言无此集合算子，编译期显式报错而非静默输出目标库
    // 不支持的算子。（空串经 xdef string 绑定实测被规范化为 null、与未配置不可区分，
    // 故采用显式哨兵；见 ai-dev/design/eql-cross-db-compat/
    // 2026-10-04-wi3-phase0-adjudications.md §二）
    String DIALECT_KEYWORD_NONE = "none";

    // WI3 版本门：报错时 ARG_FEATURE 取对应配置名
    String FEATURE_INTERSECT_KEYWORD = "intersectKeyword";
    String FEATURE_EXCEPT_KEYWORD = "exceptKeyword";

    String VAR_PARAMS = "params";

    String VAR_O = "o";
}
