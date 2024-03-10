/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.impl.AbstractTable;
import io.nop.core.model.table.impl.BaseColumnConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static io.nop.commons.util.CharSequenceHelper.appendIndent;

public class LayoutTableModel extends AbstractTable<LayoutRowModel> implements ILayoutGroupModel {
    private String label;
    private boolean foldable;
    private boolean folded;
    private int level;
    private List<LayoutRowModel> rows = new ArrayList<>();
    private List<BaseColumnConfig> cols = Collections.emptyList();
    private boolean autoId;

    public boolean isAutoId() {
        return autoId;
    }

    public void setAutoId(boolean autoId) {
        this.autoId = autoId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public List<BaseColumnConfig> getCols() {
        return cols;
    }

    public void setCols(List<BaseColumnConfig> cols) {
        this.cols = cols;
    }

    @Override
    public List<LayoutRowModel> getRows() {
        return rows;
    }

    public boolean isSimple() {
        return !foldable && label == null;
    }

    @Override
    public void display(StringBuilder sb, int indent) {
        display(sb, false, false, indent);
    }

    public void display(StringBuilder sb, boolean headContinue, boolean tailContinue, int indent) {
        sb.append('\n');
        appendIndent(sb, indent);
        LayoutHelper.buildHeaderLine(this, headContinue, tailContinue, sb);
        for (LayoutRowModel row : getRows()) {
            appendIndent(sb, indent + 2);
            if (headContinue)
                appendIndent(sb, 3);
            row.display(sb, indent + 2);
        }
        LayoutHelper.buildTailLine(sb, tailContinue, indent);
        sb.append("\n");
    }

    @Override
    protected void outputJson(IJsonHandler handler) {
        super.outputJson(handler);

        handler.put("cols", this.getCols());
        handler.put("rows", this.getRows());

        handler.put("level", level);

        if (label != null)
            handler.put("label", label);

        if (foldable) {
            handler.put("foldable", true);
        }

        if (folded) {
            handler.put("folded", true);
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    protected LayoutRowModel newRow() {
        return new LayoutRowModel();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        checkAllowChange();
        this.label = label;
    }

    @Override
    public String getType() {
        return "table";
    }

    public boolean isFoldable() {
        return foldable;
    }

    public void setFoldable(boolean foldable) {
        checkAllowChange();
        this.foldable = foldable;
    }

    public boolean isFolded() {
        return folded;
    }

    public void setFolded(boolean folded) {
        checkAllowChange();
        this.folded = folded;
    }

    public void addRow(LayoutRowModel row) {
        row.setTable(this);
        this.getRows().add(row);
    }

    public void forEachLayoutCell(Consumer<LayoutCellModel> action) {
        for (IRow row : getRows()) {
            for (ICell cell : row.getCells()) {
                if (cell.isProxyCell())
                    continue;
                if (cell instanceof LayoutGroupModel) {
                    ((LayoutGroupModel) cell).getTable().forEachLayoutCell(action);
                } else {
                    action.accept((LayoutCellModel) cell);
                }
            }
        }
    }
}