/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.ddl;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.xlang.api.XLang;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DdlSqlCreator {
    private final String dmlLibPath;
    private final IDialect dialect;

    public static DdlSqlCreator forDialect(String dialectName) {
        return new DdlSqlCreator(dialectName);
    }

    public static DdlSqlCreator forDialect(IDialect dialect) {
        return new DdlSqlCreator(dialect);
    }

    public DdlSqlCreator(IDialect dialect) {
        this(dialect.getName(), dialect);
    }

    public DdlSqlCreator(String dialectName) {
        this.dmlLibPath = getDmlLibPath(dialectName);
        this.dialect = DialectManager.instance().getDialect(dialectName);
    }

    public DdlSqlCreator(String dialectName, IDialect dialect) {
        this.dmlLibPath = getDmlLibPath(dialectName);
        this.dialect = dialect;
    }

    static String getDmlLibPath(String dialectName) {
        String path = "/nop/orm/xlib/ddl/ddl_" + dialectName + ".xlib";
        if (VirtualFileSystem.instance().getResource(path).exists()) {
            return path;
        }
        if (dialectName.indexOf('.') > 0) {
            String basePath = "/nop/orm/xlib/ddl/ddl_" + StringHelper.firstPart(dialectName, '.') + ".xlib";
            if (VirtualFileSystem.instance().getResource(basePath).exists())
                return basePath;
        }
        return path;
    }

    public String createTable(IEntityModel table) {
        return createTable(table, false);
    }

    public String createTables(Collection<? extends IEntityModel> tables, boolean includeComments) {
        return createTables(tables, false, includeComments);
    }

    public String createTable(IEntityModel table, boolean allNullable) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("dialect", dialect);
        args.put("allNullable", allNullable);
        return XLang.getTagAction(dmlLibPath, "CreateTable").generateText(XLang.newEvalScope(args));
    }

    public String createTables(Collection<? extends IEntityModel> tables, boolean allNullable,
                               boolean includeComments) {
        Map<String, Object> args = new HashMap<>();
        args.put("tables", tables);
        args.put("dialect", dialect);
        args.put("allNullable", allNullable);
        args.put("includeComments", includeComments);
        return XLang.getTagAction(dmlLibPath, "CreateTables").generateText(XLang.newEvalScope(args));
    }

    public String dropTable(IEntityModel table, boolean ifExists) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("dialect", dialect);
        args.put("ifExists", ifExists);
        return XLang.getTagAction(dmlLibPath, "DropTable").generateText(XLang.newEvalScope(args));
    }

    public String dropTables(Collection<? extends IEntityModel> tables, boolean ifExists) {
        Map<String, Object> args = new HashMap<>();
        args.put("tables", tables);
        args.put("dialect", dialect);
        args.put("ifExists", ifExists);
        return XLang.getTagAction(dmlLibPath, "DropTables").generateText(XLang.newEvalScope(args));
    }

    public String addTenantIdForTables(Collection<? extends IEntityModel> tables) {
        Map<String, Object> args = new HashMap<>();
        args.put("tables", tables);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "AddTenantIdForTables").generateText(XLang.newEvalScope(args));
    }

    public String addTenantIdForTable(IEntityModel table, boolean addToPk) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("dialect", dialect);
        args.put("addToPk", addToPk);
        return XLang.getTagAction(dmlLibPath, "AddTenantIdForTable").generateText(XLang.newEvalScope(args));
    }

    public String addTenantIdToPrimaryKey(IEntityModel table) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "AddTenantIdToPrimaryKey").generateText(XLang.newEvalScope(args));
    }

    public String addColumn(IColumnModel col) {
        Map<String, Object> args = new HashMap<>();
        args.put("col", col);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "AddColumn").generateText(XLang.newEvalScope(args));
    }

    public String dropColumn(IColumnModel col) {
        Map<String, Object> args = new HashMap<>();
        args.put("col", col);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "DropColumn").generateText(XLang.newEvalScope(args));
    }

    public String modifyColumn(IColumnModel col, IColumnModel oldCol) {
        Map<String, Object> args = new HashMap<>();
        args.put("col", col);
        args.put("oldCol", oldCol);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "ModifyColumn").generateText(XLang.newEvalScope(args));
    }

    public String addUniqueKey(IEntityModel table, OrmUniqueKeyModel uniqueKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("uniqueKey", uniqueKey);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "AddUniqueKey").generateText(XLang.newEvalScope(args));
    }

    public String dropUniqueKey(IEntityModel table, OrmUniqueKeyModel uniqueKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("uniqueKey", uniqueKey);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "DropUniqueKey").generateText(XLang.newEvalScope(args));
    }

    public String addIndex(IEntityModel table, OrmIndexModel index) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("index", index);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "AddIndex").generateText(XLang.newEvalScope(args));
    }

    public String dropIndex(IEntityModel table, OrmIndexModel index) {
        Map<String, Object> args = new HashMap<>();
        args.put("table", table);
        args.put("index", index);
        args.put("dialect", dialect);
        return XLang.getTagAction(dmlLibPath, "DropIndex").generateText(XLang.newEvalScope(args));
    }
}