package io.nop.autotest.core.execute;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.autotest.core.AutoTestErrors.ARG_FILE;
import static io.nop.autotest.core.AutoTestErrors.ARG_TABLE_NAME;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_NO_DAO_FOR_TABLE;

public class AutoTestCaseDataBaseInitializer {
    static final Logger LOG = LoggerFactory.getLogger(AutoTestCaseDataBaseInitializer.class);

    private final boolean localDb;
    private final boolean tableInit;
    private final boolean sqlInit;
    private final AutoTestCaseData caseData;
    private final IDaoProvider daoProvider;
    private final IJdbcTemplate jdbcTemplate;
    private final String variant;

    public AutoTestCaseDataBaseInitializer(String variant, boolean localDb, boolean tableInit, boolean sqlInit,
                                           AutoTestCaseData caseData,
                                           IDaoProvider daoProvider,
                                           IJdbcTemplate jdbcTemplate) {
        this.variant = variant;
        this.localDb = localDb;
        this.tableInit = tableInit;
        this.sqlInit = sqlInit;
        this.caseData = caseData;
        this.daoProvider = daoProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initialize() {
        if (localDb) {
            // 1. 建表
            createTables();
        }

        if (tableInit) {
            // 2. 插入input/tables目录下的初始化数据
            loadInputData();
        }

        if (sqlInit) {
            // 3. 执行初始化sql语句
            executeInitSqls();
        }
    }

    void createTables() {
        LOG.info("nop.autotest.create-tables");

        Map<String, IEntityDao> daoMap = new TreeMap<>();
        // 输入和输出所涉及到的表都需要新建
        collectTableDao(caseData.getInputTableFiles(variant), daoMap);
        collectTableDao(caseData.getOutputTableFiles(variant), daoMap);

        for (IEntityDao dao : daoMap.values()) {
            if (dao instanceof IOrmEntityDao) {
                IOrmEntityDao ormEntityDao = (IOrmEntityDao) dao;
                IEntityModel entityModel = ormEntityDao.getEntityModel();
                IOrmTemplate orm = ormEntityDao.getOrmTemplate();
                IDialect dialect = orm.getDialectForQuerySpace(entityModel.getQuerySpace());
                if (dialect != null) {
                    createTable(dialect, entityModel);
                }
            }
        }
    }

    void createTable(IDialect dialect, IEntityModel entityModel) {
        DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect.getName());
        String sql = creator.createTable(entityModel, true);
        try {
            jdbcTemplate.executeUpdate(new SQL(sql));
        } catch (NopException e) {
            if (e.getErrorCode().equals(DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode())) {
                LOG.debug("nop.create-table-fail:{}", e);
                return;
            }
            LOG.error("nop.create-table-fail:{}", entityModel.getTableName(), e);
        }
    }

    void collectTableDao(List<File> files, Map<String, IEntityDao> daoMap) {
        for (File file : files) {
            String tableName = StringHelper.fileNameNoExt(file.getName());
            IEntityDao dao = requireDaoForTable(tableName, file);
            daoMap.put(dao.getTableName(), dao);
        }
    }

    private void loadInputData() {
        ITransactionTemplate txn = jdbcTemplate.txn();

        // 在事务中插入数据，确保整体成功或者失败
        txn.runInTransaction(null, TransactionPropagation.REQUIRED, t -> {
            List<File> files = caseData.getInputTableFiles(variant);
            for (File file : files) {
                String tableName = StringHelper.fileNameNoExt(file.getName());
                IEntityDao dao = requireDaoForTable(tableName, file);
                List<Map<String, Object>> data = caseData.readInputTableData(dao.getPkColumnNames(),tableName, variant);

                insertInputData(dao, data);
            }
            return null;
        });
    }

    private IEntityDao requireDaoForTable(String tableName, File file) {
        IEntityDao dao = daoProvider.daoForTable(tableName);
        if (dao == null)
            throw new AutoTestException(ERR_AUTOTEST_NO_DAO_FOR_TABLE)
                    .param(ARG_TABLE_NAME, tableName)
                    .param(ARG_FILE, file);
        return dao;
    }

    private void insertInputData(IEntityDao dao, List<Map<String, Object>> rows) {
        if (dao instanceof IOrmEntityDao) {
            IOrmEntityDao ormDao = (IOrmEntityDao) dao;

            IOrmTemplate orm = ormDao.getOrmTemplate();
            orm.runInSession(session -> {
                for (Map<String, Object> row : rows) {
                    IOrmEntity entity = newEntityFromRow(row, ormDao);
                    entity.orm_skipAutoStamp(true);
                    ormDao.saveEntity(entity);
                }
                session.flush();
                session.clear();
                return null;
            });
        }
    }

    private IOrmEntity newEntityFromRow(Map<String, Object> row, IOrmEntityDao<IOrmEntity> dao) {
        IEntityModel entityModel = dao.getEntityModel();
        IOrmEntity entity = dao.newEntity();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String colName = entry.getKey();
            IColumnModel col = entityModel.getColumnByCode(colName, false);
            entity.orm_propValue(col.getPropId(), entry.getValue());
        }
        return entity;
    }

    private void executeInitSqls() {
        List<File> files = caseData.getInputSqlFiles(variant);
        for (File file : files) {
            String sql = FileHelper.readText(file, null);
            if (StringHelper.isBlank(sql))
                continue;

            jdbcTemplate.executeMultiSql(new SQL(sql));
        }
    }
}