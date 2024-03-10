/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xui.model._gen._UiGridModel;

import static io.nop.xui.XuiErrors.ARG_COL_ID;
import static io.nop.xui.XuiErrors.ARG_GRID_ID;
import static io.nop.xui.XuiErrors.ERR_GRID_COL_NOT_PROP;

public class UiGridModel extends _UiGridModel implements INeedInit {
    public UiGridModel() {

    }

    public boolean containsBreakpoint() {
        return getCols().stream().anyMatch(col -> !StringHelper.isEmpty(col.getBreakpoint()));
    }

    public void init() {
        for (UiGridColModel col : getCols()) {
            col.init();
        }
    }

    public void validate(IObjMeta objMeta) {
        getCols().forEach(col -> {
            UiGridColModel cm = getCol(col.getId());
            cm.validate();

            if (objMeta != null) {
                String propName = cm.getId();
                if (cm != null && cm.getProp() != null)
                    propName = cm.getProp();

                if (cm == null || !cm.isCustom()) {
                    if (!objMeta.hasProp(propName))
                        throw new NopException(ERR_GRID_COL_NOT_PROP).source(col).param(ARG_COL_ID, col.getId())
                                .param(ARG_GRID_ID, getId());
                }
            }
        });
    }

    public IObjMeta loadObjMeta() {
        return SchemaLoader.loadXMeta(getObjMeta());
    }
}
