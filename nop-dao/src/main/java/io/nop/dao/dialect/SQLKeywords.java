/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.commons.util.CollectionHelper;

import java.util.Set;

/**
 * @author canonical_entropy@163.com
 */
public class SQLKeywords {
    private final Set<String> keywords;

    public static final SQLKeywords DEFAULT;

    static {
        Set<String> set = CollectionHelper.buildImmutableSet("ALL", "ALTER", "AND", "ANY", "AS", "ENABLE", "DISABLE",
                "ASC", "BETWEEN", "BY", "CASE", "CAST", "CHECK", "CONSTRAINT", "CREATE", "DATABASE", "DEFAULT",
                "COLUMN", "TABLESPACE", "PROCEDURE", "FUNCTION", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE",
                "EXPLAIN", "EXCEPT", "END", "ESCAPE", "EXISTS", "FOR", "FOREIGN", "FROM", "FULL", "GROUP", "HAVING",
                "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTERVAL", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE",
                "LOCK", "MINUS", "NOT", "NULL", "ON", "ORDER", "OUTER", "PRIMARY", "PREFERENCES", "RIGHT", "SCHEMA",
                "SELECT", "SET", "SOME", "TABLE", "THEN", "TRUNCATE", "UNION", "UNIQUE", "UPDATE", "VALUES", "VIEW",
                "SEQUENCE", "TRIGGER", "USER", "WHEN", "WHERE", "XOR", "OVER", "TO", "USE", "REPLACE", "COMMENT",
                "COMPUTE", "WITH", "GRANT", "REVOKE");

        DEFAULT = new SQLKeywords(set);
    }

    public SQLKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }

    public boolean isKeyword(String key) {
        key = key.toUpperCase();
        return keywords.contains(key);
    }

    public Set<String> getKeywords() {
        return keywords;
    }
}
