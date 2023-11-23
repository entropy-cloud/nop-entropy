/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.execute;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.data.AutoTestVars;
import io.nop.autotest.core.data.SqlCheck;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.orm.IOrmEntity;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.xlang.api.XLang;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.autotest.core.AutoTestErrors.ARG_FILE;
import static io.nop.autotest.core.AutoTestErrors.ARG_ID;
import static io.nop.autotest.core.AutoTestErrors.ARG_ROW_NUMBER;
import static io.nop.autotest.core.AutoTestErrors.ARG_SQL;
import static io.nop.autotest.core.AutoTestErrors.ARG_SQL_RESULT;
import static io.nop.autotest.core.AutoTestErrors.ARG_TABLE_NAME;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_CHECK_DELETED_ROW_FAIL;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_CHECK_SQL_RESULT_FAIL;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_NO_DAO_FOR_TABLE;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_OUTPUT_ROW_NOT_EXISTS;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_ROW_NO_ID;
import static io.nop.core.CoreErrors.ARG_ROW;

public class AutoTestCaseResultChecker {
    private final AutoTestCaseData caseData;
    private final IDaoProvider daoProvider;
    private final IJdbcTemplate jdbcTemplate;
    private final MatchPatternCompileConfig matchConfig;
    private final String variant;

    private final String testMethod;

    private IEvalScope scope;

    public AutoTestCaseResultChecker(String testMethod, String variant, AutoTestCaseData caseData, IDaoProvider daoProvider,
                                     IJdbcTemplate jdbcTemplate, MatchPatternCompileConfig matchConfig) {
        this.variant = variant;
        this.testMethod = testMethod;
        this.caseData = caseData;
        this.daoProvider = daoProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.matchConfig = matchConfig;
        this.scope = XLang.newEvalScope(AutoTestVars.getVars());
    }

    public void check() {
        checkOutputTables();
        runSqlCheck();
    }

    private void checkOutputTables() {
        List<File> files = caseData.getOutputTableFiles(variant);
        for (File file : files) {
            String tableName = StringHelper.fileNameNoExt(file.getName());
            IEntityDao dao = requireDaoForTable(tableName, file);
            if (dao instanceof IOrmEntityDao) {
                checkOutputTable((IOrmEntityDao) dao, file);
            }
        }
    }

    private IEntityDao requireDaoForTable(String tableName, File file) {
        IEntityDao dao = daoProvider.daoForTable(tableName);
        if (dao == null)
            throw new AutoTestException(ERR_AUTOTEST_NO_DAO_FOR_TABLE).param(ARG_TABLE_NAME, tableName).param(ARG_FILE,
                    file);
        return dao;
    }

    private void checkOutputTable(IOrmEntityDao<IOrmEntity> dao, File file) {
        List<Map<String, Object>> rows = CsvHelper.readCsv(new FileResource(file));
        int rowNumber = 1;
        for (Map<String, Object> row : rows) {
            String changeType = (String) row.remove(DaoConstants.PROP_CHANGE_TYPE);
            if (changeType == null)
                changeType = DaoConstants.CHANGE_TYPE_ADD;

            Map<String, Object> tpl = row;

            row = (Map<String, Object>) AutoTestVars.resolveVarName(row);

            Object id = getEntityId(row, dao);
            if (id == null)
                throw new AutoTestException(ERR_AUTOTEST_ROW_NO_ID).param(ARG_ROW, row).param(ARG_ROW_NUMBER, rowNumber)
                        .param(ARG_TABLE_NAME, dao.getTableName()).param(ARG_FILE, file);

            IOrmEntity entity = dao.getEntityById(id);
            if (changeType.equals(DaoConstants.CHANGE_TYPE_DELETE)) {
                if (entity != null)
                    throw new AutoTestException(ERR_AUTOTEST_CHECK_DELETED_ROW_FAIL).param(ARG_ID, id)
                            .param(ARG_ROW_NUMBER, rowNumber).param(ARG_TABLE_NAME, dao.getTableName())
                            .param(ARG_FILE, file);
            } else {
                if (entity == null)
                    throw new AutoTestException(ERR_AUTOTEST_OUTPUT_ROW_NOT_EXISTS).param(ARG_ID, id)
                            .param(ARG_ROW_NUMBER, rowNumber).param(ARG_TABLE_NAME, dao.getTableName())
                            .param(ARG_FILE, file);

                checkRowMatch(tpl, entity, dao);
            }

            rowNumber++;
        }
    }

    private Object getEntityId(Map<String, Object> row, IOrmEntityDao<IOrmEntity> dao) {
        IEntityModel entityModel = dao.getEntityModel();
        IOrmEntity entity = dao.newEntity();
        for (IColumnModel col : entityModel.getPkColumns()) {
            entity.orm_propValue(col.getPropId(), row.get(col.getCode()));
        }
        return entity.get_id();
    }

    private void checkRowMatch(Map<String, Object> row, IOrmEntity entity, IOrmEntityDao<IOrmEntity> dao) {
        IEntityModel entityModel = dao.getEntityModel();
        Map<String, Object> entityData = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String colName = entry.getKey();
            IColumnModel col = entityModel.getColumnByCode(colName, false);
            entityData.put(colName, entity.orm_propValue(col.getPropId()));
            Object value = entry.getValue();
            if (value instanceof String) {
                String str = value.toString();
                if (!str.startsWith("@") && !str.startsWith("*")) {
                    entry.setValue(ConvertHelper.convertTo(col.getJavaClass(), entry.getValue(), NopException::new));
                }
            }
        }

        try {
            AutoTestMatchChecker.checkMatch(testMethod, matchConfig, row, entityData, scope);
        } catch (NopException e) {
            e.param(ARG_TABLE_NAME, entityModel.getTableName()).param(ARG_ID, entity.orm_idString());
            throw e;
        }
    }

    private void runSqlCheck() {
        List<SqlCheck> checks = caseData.getSqlChecks(variant);
        for (SqlCheck check : checks) {
            String sql = check.getSql();
            SQL.SqlBuilder sb = SqlHelper.markNamedParam(sql);
            sb.transformMarker(maker -> {
                String name = maker.getName();
                Object value = AutoTestVars.getVar(name);
                return SQL.begin().param(value);
            });

            Object result = jdbcTemplate.findFirst(sb.end());
            try {
                AutoTestMatchChecker.checkMatch(testMethod,matchConfig, check.getResult(), result, scope);
            } catch (Exception e) {
                throw new NopException(ERR_AUTOTEST_CHECK_SQL_RESULT_FAIL, e).param(ARG_SQL, sql).param(ARG_SQL_RESULT,
                        result);
            }
        }
    }
}
