/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IOrmModelValidator;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;

import java.util.HashMap;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ARG_COL_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_PROP_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_TAG;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_ASSISTED_NOT_ENC;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_CHAINED;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_DUPLICATE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_NOT_VARCHAR;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_PRIMARY_KEY;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_TAG_MISMATCH;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_TAG_ORPHAN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_UNKNOWN_COLUMN;

/**
 * 可检索加密声明的模型扩展校验器（契约见 ai-dev/design/nop-orm-enc-assisted-columns.md）：
 * 主列（tagSet含enc）通过 ext:enc-assisted / ext:enc-like 指向同实体内的辅助列，
 * 辅助列以 enc-assisted / enc-like 角色标签标记。
 *
 * <p>经 META-INF/services 注册到 {@link OrmModelValidatorRegistry}，由模型内核初始化末尾驱动；
 * 校验失败抛 NopException（启动即失败）。从 OrmModelInitializer 迁出：扩展校验不硬编码在内核初始化中。
 */
public class OrmEncAssistedModelValidator implements IOrmModelValidator {
    @Override
    public void validate(OrmModel model) {
        Map<String, IColumnModel> referencedHelpers = new HashMap<>();
        for (OrmEntityModel entityModel : model.getEntities()) {
            validateEntity(entityModel, referencedHelpers);
        }
    }

    private void validateEntity(OrmEntityModel entityModel, Map<String, IColumnModel> referencedHelpers) {
        referencedHelpers.clear();
        String entityName = entityModel.getName();

        for (IColumnModel col : entityModel.getColumns()) {
            String assistedName = getExtRefName(col, OrmModelConstants.EXT_ENC_ASSISTED);
            String likeName = getExtRefName(col, OrmModelConstants.EXT_ENC_LIKE);
            if (assistedName == null && likeName == null)
                continue;

            if (!col.containsTag(OrmModelConstants.TAG_ENC))
                throw new NopException(ERR_ORM_ENC_ASSISTED_NOT_ENC).source(col)
                        .param(ARG_ENTITY_NAME, entityName).param(ARG_PROP_NAME, col.getName());

            checkEncHelperRef(entityModel, col, assistedName, OrmModelConstants.TAG_ENC_ASSISTED, referencedHelpers);
            checkEncHelperRef(entityModel, col, likeName, OrmModelConstants.TAG_ENC_LIKE, referencedHelpers);
        }

        for (IColumnModel col : entityModel.getColumns()) {
            checkEncHelperTagReferenced(entityModel, col, OrmModelConstants.TAG_ENC_ASSISTED, referencedHelpers);
            checkEncHelperTagReferenced(entityModel, col, OrmModelConstants.TAG_ENC_LIKE, referencedHelpers);
        }
    }

    private void checkEncHelperRef(OrmEntityModel entityModel, IColumnModel master, String helperName,
                                   String expectedTag, Map<String, IColumnModel> referencedHelpers) {
        if (helperName == null)
            return;

        String entityName = entityModel.getName();
        IColumnModel helper = entityModel.getColumn(helperName, true);
        if (helper == null)
            throw new NopException(ERR_ORM_ENC_HELPER_UNKNOWN_COLUMN).source(master)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);

        if (helper == master)
            throw new NopException(ERR_ORM_ENC_HELPER_CHAINED).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);

        if (!helper.containsTag(expectedTag))
            throw new NopException(ERR_ORM_ENC_HELPER_TAG_MISMATCH).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName).param(ARG_TAG, expectedTag);

        if (helper.isPrimary())
            throw new NopException(ERR_ORM_ENC_HELPER_PRIMARY_KEY).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);

        if (helper.getStdSqlType() != StdSqlType.VARCHAR)
            throw new NopException(ERR_ORM_ENC_HELPER_NOT_VARCHAR).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);

        if (getExtRefName(helper, OrmModelConstants.EXT_ENC_ASSISTED) != null
                || getExtRefName(helper, OrmModelConstants.EXT_ENC_LIKE) != null)
            throw new NopException(ERR_ORM_ENC_HELPER_CHAINED).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);

        if (referencedHelpers.put(helperName, helper) != null)
            throw new NopException(ERR_ORM_ENC_HELPER_DUPLICATE).source(helper)
                    .param(ARG_ENTITY_NAME, entityName).param(ARG_COL_NAME, helperName);
    }

    private void checkEncHelperTagReferenced(OrmEntityModel entityModel, IColumnModel col, String tag,
                                             Map<String, IColumnModel> referencedHelpers) {
        if (!col.containsTag(tag))
            return;
        if (!referencedHelpers.containsKey(col.getName()))
            throw new NopException(ERR_ORM_ENC_HELPER_TAG_ORPHAN).source(col)
                    .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_COL_NAME, col.getName())
                    .param(ARG_TAG, tag);
    }

    private String getExtRefName(IColumnModel col, String extName) {
        Object value = col.prop_get(extName);
        return value == null ? null : value.toString();
    }

}
