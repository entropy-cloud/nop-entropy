/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout;

import io.nop.commons.util.StringHelper;

import static io.nop.commons.util.CharSequenceHelper.appendIndent;

public class LayoutHelper {
    /**
     * 格式为 ======^##id[label]=======
     */
    public static void buildHeaderLine(LayoutTableModel group, boolean headContinue, boolean tailContinue,
                                       StringBuilder sb) {
        StringBuilder buf = new StringBuilder();
        buf.append(' ');
        if (group.isFoldable()) {
            buf.append(group.isFolded() ? '^' : '>');
        }

        if (group.getLevel() > 0) {
            for (int i = 0; i < group.getLevel(); i++) {
                buf.append('#');
            }
        }

        boolean hasTitle = false;
        if (group.getId() != null && !group.isAutoId()) {
            buf.append(group.getId());
            hasTitle = true;
        }

        if (group.getLabel() != null) {
            buf.append('[');
            buf.append(group.getLabel());
            buf.append(']');
            hasTitle = true;
        }

        if (hasTitle) {
            buf.append(' ');
        }else if(buf.length() == 1){
            buf.setLength(0);
        }

        int padLen = 16 - buf.length() / 2;
        if (padLen < 3)
            padLen = 3;

        if (headContinue) {
            sb.append("~~~");
        }

        char c = '=';
        for (int i = 0; i < padLen; i++) {
            sb.append(c);
        }

        sb.append(buf);
        for (int i = 0; i < padLen; i++) {
            sb.append(c);
        }

        if (tailContinue) {
            sb.append("~~~");
        }

        sb.append('\n');
    }

    public static void buildTailLine(StringBuilder sb, boolean tailContinue, int indent) {
        if (tailContinue) {
            appendIndent(sb, indent);
            sb.append(StringHelper.repeat("=", 32));
        }
    }
}
