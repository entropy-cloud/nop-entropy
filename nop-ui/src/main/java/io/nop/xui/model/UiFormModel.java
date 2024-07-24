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
import io.nop.commons.mutable.MutableInt;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xmeta.layout.ILayoutCellModel;
import io.nop.xlang.xmeta.layout.LayoutModel;
import io.nop.xlang.xmeta.layout.LayoutTableModel;
import io.nop.xui.model._gen._UiFormModel;

import java.util.Collections;
import java.util.List;

import static io.nop.xui.XuiErrors.ARG_CELL_ID;
import static io.nop.xui.XuiErrors.ARG_FORM_ID;
import static io.nop.xui.XuiErrors.ERR_FORM_CELL_NOT_PROP;

public class UiFormModel extends _UiFormModel implements INeedInit {
    public UiFormModel() {

    }

    public ILayoutCellModel getLayoutCellById(String cellId) {
        if (getLayout() == null)
            return null;
        return getLayout().getLayoutCellById(cellId);
    }

    public IObjMeta loadObjMeta() {
        return SchemaLoader.loadXMeta(getObjMeta());
    }

    public int getCellCount() {
        MutableInt count = new MutableInt(0);
        getTables().forEach(table -> {
            table.forEachLayoutCell(cell -> {
                count.incrementAndGet();
            });
        });
        return count.get();
    }

    public void init() {
        getCells().forEach(cell -> cell.init());
    }

    public List<LayoutTableModel> getTables() {
        LayoutModel layout = getLayout();
        if (layout == null)
            return Collections.emptyList();
        return layout.getGroups();
    }

    public void validate(IObjMeta objMeta) {
        if (getLayout() == null || objMeta == null)
            return;
        getLayout().forEachLayoutCell(cell -> {
            UiFormCellModel cm = getCell(cell.getId());
            if (cm != null)
                cm.validate();

            String propName = cell.getId();
            if (cm != null && cm.getProp() != null)
                propName = cm.getProp();

            if (cm == null || !cm.isCustom()) {
                if (!objMeta.hasProp(propName))
                    throw new NopException(ERR_FORM_CELL_NOT_PROP).source(cell).param(ARG_CELL_ID, cell.getId())
                            .param(ARG_FORM_ID, getId());
            }
        });
    }
}
