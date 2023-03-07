/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.layout;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.impl.AbstractRow;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class LayoutRowModel extends AbstractRow {
    private final List<ICell> cells = new ArrayList<>();

    public void display(StringBuilder sb, int indent) {
        if (isEmpty()) {
            sb.append(StringHelper.repeat("-", 32));
            sb.append('\n');
            return;
        }
        boolean first = true;
        for (ICell cell : cells) {
            if (cell.isProxyCell())
                continue;

            if (!first) {
                sb.append("  ");
            }
            first = false;
            ILayoutCellModel layoutCell = (ILayoutCellModel) cell;
            layoutCell.display(sb, indent);
        }
        sb.append('\n');
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        out.put("cells", cells);
    }

    @Override
    public ICell makeCell(int colIndex) {
        ICell cell = cells.get(colIndex);
        if (cell == null) {
            LayoutCellModel layoutCell = new LayoutCellModel();
            layoutCell.setRow(this);
            cell = layoutCell;
            cells.set(colIndex, cell);
        }
        return cell;
    }

    @Override
    public List<? extends ICell> getCells() {
        return cells;
    }
}