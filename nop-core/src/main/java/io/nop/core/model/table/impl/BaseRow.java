/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.impl;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class BaseRow extends AbstractRow implements IRow {

    private static final long serialVersionUID = 6917319826479925078L;

    private final List<ICell> cells;

    private String id;
    private String styleId;
    private Double height;
    private boolean hidden;

    public BaseRow(int size) {
        if (size >= 0) {
            cells = new ArrayList<>(size);
        } else {
            cells = new ArrayList<>();
        }
    }

    public BaseRow() {
        cells = new ArrayList<>();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        checkAllowChange();
        this.id = id;
    }

    @Override
    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        checkAllowChange();
        this.styleId = styleId;
    }

    @Override
    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        checkAllowChange();
        this.height = height;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        checkAllowChange();
        this.hidden = hidden;
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        if (id != null)
            out.put("id", id);
        if (styleId != null)
            out.put("styleId", styleId);
        if (getHeight() != null)
            out.put("height", getHeight());
        if (isHidden())
            out.put("hidden", true);
        out.put("cells", cells);
    }

    public void freeze(boolean cascade) {
        super.freeze(cascade);
        if (cascade) {
            for (ICell cell : cells) {
                cell.freeze(true);
            }
        }
    }

    @Override
    public ICell makeCell(int colIndex) {
        ICell cell = getCell(colIndex);
        if (cell == null) {
            BaseCell bc = new BaseCell();
            internalSetCell(colIndex, bc);
            cell = bc;
        }
        return cell;
    }

    @Override
    public @Nonnull List<? extends ICell> getCells() {
        return cells;
    }
}