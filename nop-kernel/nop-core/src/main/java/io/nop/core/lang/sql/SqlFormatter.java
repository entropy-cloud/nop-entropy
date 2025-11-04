/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.sql;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.text.marker.MarkedStringBuilder;
import io.nop.commons.text.marker.Marker;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.text.marker.Markers.ValueMarker;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConfigs;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/**
 * @author canonical_entropy@163.com
 */
public class SqlFormatter {
    public static void formatSql(StringBuilder buf, final SQL sql) {
        MarkedStringBuilder sb = new MarkedStringBuilder(buf);
        sb.appendWithTransform(sql, marker -> {
            if (marker instanceof ValueMarker || marker instanceof Markers.ProviderMarker) {
                StringBuilder markerText = new StringBuilder();
                formatMarkerText(markerText, marker, sql);
                return new MarkedStringBuilder(markerText);
            } else {
                return null;
            }
        });

        // CharSequenceHelper.replace(buf, " select ", "\n select ");
        // CharSequenceHelper.replace(buf, " from ", "\n from ");
        // CharSequenceHelper.replace(buf, " where ", "\n where ");
        // CharSequenceHelper.replace(buf, " order by ", "\n order by ");
        // CharSequenceHelper.replace(buf, " group by ", "\n group by ");
    }

    private static void formatMarkerText(StringBuilder sb, Marker marker, SQL sql) {
        sb.append("/*").append(marker.getMarkedText(sql.getText())).append("*/");
        if (marker instanceof ValueMarker) {
            ValueMarker vm = (ValueMarker) marker;
            toSqlText(sb, vm.getValue(), vm.isMasked());
        } else if (marker instanceof Markers.ProviderMarker) {
            Markers.ProviderMarker pm = (Markers.ProviderMarker) marker;
            toSqlText(sb, pm.getValue(), pm.isMasked());
        }
    }

    private static void toSqlText(StringBuilder sb, Object o, boolean masked) {
        if (o == null) {
            sb.append(" null ");
            return;
        }
        if (o instanceof String) {
            String str = o.toString();

            if (str.length() > 1000)
                str = str.substring(0, 1000) + "<...>";
            str = defaultMask(str, masked);
            sb.append('\'');
            sb.append(StringHelper.escapeSql(str, true));
            sb.append('\'');
            return;
        }
        if (o instanceof LocalDate || o instanceof java.sql.Date) {
            String str = ConvertHelper.toString(o);
            str = defaultMask(str, masked);
            sb.append("DATE '").append(str).append("'");
            return;
        } else if (o instanceof LocalTime || o instanceof Time) {
            String str = ConvertHelper.toString(o);
            str = defaultMask(str, masked);
            sb.append("TIME '").append(str).append("'");
            return;
        } else if (o instanceof Date || o instanceof LocalDateTime) {
            String str = ConvertHelper.toString(o);
            str = defaultMask(str, masked);
            sb.append("TIMESTAMP '").append(str).append("'");
            return;
        }
        sb.append(o);
    }

    static String defaultMask(String value, boolean masked) {
        if (!masked)
            return value;
        if (value.isEmpty())
            return value;

        String last = value;
        int keep = CoreConfigs.CFG_DEFAULT_MASKING_KEEP_CHARS.get();
        if (keep <= 0) {
            return StringHelper.repeat("*", value.length());
        }

        return StringHelper.maskPattern(value, "*" + keep);
    }
}