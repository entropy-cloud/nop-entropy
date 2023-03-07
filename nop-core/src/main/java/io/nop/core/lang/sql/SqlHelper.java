/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.core.lang.sql;

import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.util.CharSequenceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class SqlHelper {

    // MSSQL不支持双引号表示字符串，双引号表示的是名称，而MySQL和Access都是同时支持双引号和单引号。两个连续的单引号表示转义
    // 如果使用双引号，则嵌入的单引号不需要用两个引号来表示
    // PLSQL中&字符表示变量定义开始，需要使用\&转义，或者通过set define off来关闭这一特性
    /**
     * Set of characters that qualify as comment or quotes starting characters.
     */
    private static final String[] START_SKIP = new String[]{"'", "\"", "--", "/*"};

    /**
     * Set of characters that at are the corresponding comment or quotes ending characters.
     */
    private static final String[] STOP_SKIP = new String[]{"'", "\"", "\n", "*/"};

    public static List<SQL> splitSqlText(String sqlText) {
        return splitSqlText(sqlText, () -> SQL.begin());
    }

    public static List<SQL> splitSqlText(String sqlText, Supplier<SQL.SqlBuilder> creator) {
        return splitSql(SQL.begin().append(sqlText).end(), creator);
    }

    public static SQL.SqlBuilder markNamedParam(String sqlText) {
        SQL.SqlBuilder sb = SQL.begin().append(sqlText);

        int pos = 0;
        do {
            int pos2 = sqlText.indexOf(':', pos);
            if (pos2 < 0) {
                break;
            }
            pos = findParamEndPos(sqlText, pos2 + 1);
            if (pos < 0)
                break;

            String paramName = sqlText.substring(pos2 + 1, pos);
            if (paramName.length() > 0) {
                sb.appendMarker(new Markers.NameMarker(pos2, pos, paramName));
            }
        } while (true);

        return sb;
    }

    static int findParamEndPos(String sqlText, int startPos) {
        for (int i = startPos, n = sqlText.length(); i < n; i++) {
            char c = sqlText.charAt(i);
            if (!Character.isJavaIdentifierPart(c))
                return i;
        }
        return sqlText.length();
    }

    /**
     * 根据字符;将给定的MarkedString拆分成多条sql语句。
     *
     * @param str sql文本
     * @return 拆分得到的sql列表
     */
    public static List<SQL> splitSql(IMarkedString str, Supplier<SQL.SqlBuilder> creator) {
        if (str == null || str.isEmpty())
            return Collections.emptyList();

        int pos = CharSequenceHelper.indexOf(str.getTextSequence(), 0, ';');
        if (pos < 0) {
            return Collections.singletonList(SQL.begin().append(str).end());
        }

        List<SQL> ret = new ArrayList<>();

        CharSequence statement = str.getTextSequence();

        int startPos = 0;

        int i = 0;
        int n = statement.length();
        while (i < n) {
            int skipToPosition = i;
            while (i < n) {
                skipToPosition = skipCommentsAndQuotes(statement, i);
                if (i == skipToPosition) {
                    break;
                } else {
                    i = skipToPosition;
                }
            }
            if (i >= n) {
                break;
            }
            char c = statement.charAt(i);
            if (c == ';') {
                SQL.SqlBuilder part = SQL.begin();
                part.appendRange(str, startPos, i);
                ret.add(part.end());
                i++;
                i = CharSequenceHelper.skipWhitespace(statement, i);
                startPos = i;
            } else {
                i++;
            }
        }
        if (i != n) {
            SQL.SqlBuilder part = creator.get();
            part.appendRange(str, i, n);
            ret.add(part.end());
        }

        return ret;
    }

    /**
     * copy from springframework project
     * <p>
     * Skip over comments and quoted names present in an SQL statement
     *
     * @param statement character array containing SQL statement
     * @param position  current position of statement
     * @return next position to process after any comments or quotes are skipped
     */
    static int skipCommentsAndQuotes(CharSequence statement, int position) {
        for (int i = 0; i < START_SKIP.length; i++) {
            if (statement.charAt(position) == START_SKIP[i].charAt(0)) {
                boolean match = true;
                for (int j = 1; j < START_SKIP[i].length(); j++) {
                    if (!(statement.charAt(position + j) == START_SKIP[i].charAt(j))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    int offset = START_SKIP[i].length();
                    for (int m = position + offset; m < statement.length(); m++) {
                        char c = statement.charAt(m);
                        if (c == STOP_SKIP[i].charAt(0)) {
                            boolean endMatch = true;
                            int endPos = m;
                            for (int n = 1; n < STOP_SKIP[i].length(); n++) {
                                if (m + n >= statement.length()) {
                                    // last comment not closed properly
                                    return statement.length();
                                }
                                if (!(statement.charAt(m + n) == STOP_SKIP[i].charAt(n))) {
                                    endMatch = false;
                                    break;
                                }
                                endPos = m + n;
                            }
                            if (endMatch) {
                                // found character sequence ending comment or
                                // quote
                                return endPos + 1;
                            }
                        }
                    }
                    // character sequence ending comment or quote not found
                    return statement.length();
                }

            }
        }
        return position;
    }

}
