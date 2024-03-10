/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.utils;

import io.nop.commons.text.CharacterCase;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.sql.AstToEqlGenerator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityPropModel;

import java.util.ArrayList;
import java.util.List;

public class EqlHelper {
    public static SQL.SqlBuilder getExprSql(SqlExpr expr) {
        AstToEqlGenerator gen = new AstToEqlGenerator();
        gen.setPretty(false);
        gen.visit(expr);
        return gen.getSql();
    }

    public static String getFieldName(SqlExprProjection exprProj) {
        String fieldName = exprProj.getFieldName();
        if (fieldName == null) {
            SqlAlias alias = exprProj.getAlias();

            if (alias == null || alias.isGenerated()) {
                SqlExpr expr = exprProj.getExpr();
                if (expr.getASTKind() == EqlASTKind.SqlColumnName) {
                    fieldName = ((SqlColumnName) expr).getName();
                } else {
                    fieldName = EqlHelper.getExprSql(expr).getText();
                }
            } else {
                fieldName = alias.getAlias();
            }
            exprProj.setFieldName(fieldName);
        }
        return fieldName;
    }

    public static String getAlias(String alias, int index, int count) {
        if (alias == null)
            return null;
        if (count == 1)
            return alias;
        return alias + '_' + index;
    }

    public static String replaceOwner(String owner, String sqlText) {
        // 如果指定了sqlText，则以sqlText为准，有可能是函数调用之类的。例如 my_func({prefix}colA,{prefix}colB)
        if (sqlText.contains(OrmEqlConstants.PREFIX_PLACEHOLDER)) {
            if (owner == null) {
                sqlText = StringHelper.replace(sqlText, OrmEqlConstants.PREFIX_PLACEHOLDER, "");
            } else {
                sqlText = StringHelper.replace(sqlText, OrmEqlConstants.PREFIX_PLACEHOLDER, owner + ".");
            }
        }
        return sqlText;
    }

    public static String getColumnName(IDialect dialect, IColumnModel col) {
        String sqlText = col.getSqlText();
        if (sqlText != null)
            return "@" + sqlText;
        sqlText = dialect.escapeSQLName(col.getCode());
        return sqlText;
    }

    public static void genColumnNames(IDialect dialect, IEntityPropModel propModel, List<String> colNames) {
        for (IColumnModel col : propModel.getColumns()) {
            String colName = getColumnName(dialect, col);
            colNames.add(colName);
        }
    }

    public static List<String> getPropColumnNames(IDialect dialect, IEntityPropModel propModel) {
        List<? extends IColumnModel> cols = propModel.getColumns();
        List<String> ret = new ArrayList<>(cols.size());
        for (IColumnModel col : cols) {
            ret.add(getColumnName(dialect, col));
        }
        return ret;
    }

    public static String normalizeTableName(IDialect dialect, String tableName) {
        CharacterCase characterCase = dialect.getTableNameCase();
        if (characterCase != null) {
            tableName = characterCase.normalize(tableName);
        }
        return dialect.escapeSQLName(tableName);
    }

    public static String normalizeColName(IDialect dialect, String tableName) {
        CharacterCase characterCase = dialect.getColumnNameCase();
        if (characterCase != null) {
            tableName = characterCase.normalize(tableName);
        }
        return dialect.escapeSQLName(tableName);
    }


    public static Object getBooleanValue(StdSqlType sqlType, IDialect dialect, boolean value) {
        if (sqlType == StdSqlType.BOOLEAN)
            return value;
        if (sqlType == StdSqlType.VARCHAR)
            return value ? "1" : "0";
        return value ? 1 : 0;
    }

    public static void appendCol(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col) {
        String sqlText = col.getSqlText();
        if (sqlText != null) {
            sqlText = replaceOwner(owner, sqlText);
            sb.append(sqlText);
        } else {
            String code = col.getCode();
            sb.owner(owner);
            code = dialect.escapeSQLName(code);
            sb.append(code);
        }
    }
}
