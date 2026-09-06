/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface PdmModelErrors {
    String ARG_TABLE_NAME = "tableName";
    String ARG_TAG_NAME = "tagName";
    String ARG_PARENT_TABLE_NAME = "parentTableName";
    String ARG_CHILD_TABLE_NAME = "childTableName";

    ErrorCode ERR_PDM_PRIMARY_KEY_NO_KEY_REF = define("nop.err.orm.pdm.primary-key-no-key-ref",
            "表[{tableName}]的<c:PrimaryKey>定义下缺少<c:Key>节点，无法确定主键引用", ARG_TABLE_NAME);

    ErrorCode ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE = define("nop.err.orm.pdm.element-missing-name-or-code",
            "PDM元素[{tagName}]缺少<a:Name>或<a:Code>节点，无法解析", ARG_TAG_NAME);

    ErrorCode ERR_PDM_REFERENCE_NO_JOIN_COLUMN = define("nop.err.orm.pdm.reference-no-join-column",
            "表[{childTableName}]到表[{parentTableName}]的引用没有可解析的关联列，请检查<c:Joins>下的<c:ReferenceJoin>定义",
            ARG_CHILD_TABLE_NAME, ARG_PARENT_TABLE_NAME);
}
