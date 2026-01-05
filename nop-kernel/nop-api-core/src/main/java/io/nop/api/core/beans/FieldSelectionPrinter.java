/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.Symbol;

import java.util.List;
import java.util.Map;

class FieldSelectionPrinter {
    static final FieldSelectionPrinter _INSTANCE = new FieldSelectionPrinter();

    public static FieldSelectionPrinter instance() {
        return _INSTANCE;
    }

    public String print(FieldSelectionBean selection, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        _toSelectionExpr(sb, pretty, 0, selection.getFields());
        return sb.toString();
    }

    public void printTo(StringBuilder sb, FieldSelectionBean selection, boolean pretty) {
        _toSelectionExpr(sb, pretty, 0, selection.getFields());
    }

    void _toSelectionExpr(StringBuilder sb, boolean pretty, int indent, Map<String, FieldSelectionBean> fields) {
        int i = 0;
        for (Map.Entry<String, FieldSelectionBean> entry : fields.entrySet()) {
            FieldSelectionBean subField = entry.getValue();
            String alias = entry.getKey();
            String name = subField.getName();
            boolean useAlias = name != null && !name.equals(alias);

            if (pretty) {
                if (i != 0)
                    sb.append('\n');
                addIndent(sb, indent);
            } else {
                if (i != 0)
                    sb.append(',');
            }

            sb.append(alias);
            if (useAlias) {
                sb.append(':');
                sb.append(name);
            }

            _appendArgs(sb, subField.getArgs());

            _appendDirectives(sb, subField.getDirectives());

            if (subField.hasField()) {
                sb.append('{');
                if (pretty) {
                    sb.append('\n');
                }
                _toSelectionExpr(sb, pretty, indent + 1, subField.getFields());
                if (pretty) {
                    sb.append('\n');
                    addIndent(sb, indent);
                }
                sb.append('}');
            }
            i++;
        }
    }

    void addIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    void _appendArgs(StringBuilder sb, Map<String, Object> args) {
        if (args == null || args.isEmpty())
            return;
        sb.append('(');
        int i = 0;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (i > 0)
                sb.append(',');
            String name = entry.getKey();
            Object value = entry.getValue();
            sb.append(name).append(':');
            _encodeValue(sb, value);
            i++;
        }
        sb.append(')');
    }

    void _appendDirectives(StringBuilder sb, Map<String, Map<String, Object>> directives) {
        if (directives == null || directives.isEmpty())
            return;

        for (Map.Entry<String, Map<String, Object>> entry : directives.entrySet()) {
            sb.append(" @");
            sb.append(entry.getKey());
            _appendArgs(sb, entry.getValue());
        }
    }

    void _encodeValue(StringBuilder sb, Object value) {
        if (value instanceof Symbol) {
            sb.append(((Symbol) value).getText());
        } else if (value instanceof List) {
            sb.append('[');
            List<?> list = (List<?>) value;
            for (int i = 0, n = list.size(); i < n; i++) {
                if (i != 0)
                    sb.append(',');
                _encodeValue(sb, list.get(i));
            }
            sb.append(']');
        } else if (value instanceof String) {
            sb.append(ApiStringHelper.quote(value.toString()));
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            sb.append("{");
            int i = 0;
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                if (i > 0)
                    sb.append(',');
                String name = entry.getKey();
                if (name.startsWith("$")) {
                    name = ApiStringHelper.quote(name);
                }
                Object subValue = entry.getValue();
                sb.append(name).append(':');
                _encodeValue(sb, subValue);
                i++;
            }
            sb.append("}");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append(ApiStringHelper.quote(value.toString()));
        }
    }
}
