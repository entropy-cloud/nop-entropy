package io.nop.orm.initialize;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataInitInitializer {
    static final Logger LOG = LoggerFactory.getLogger(DataInitInitializer.class);

    private IOrmSessionFactory ormSessionFactory;
    private IDaoProvider daoProvider;
    private IOrmTemplate ormTemplate;
    private IJdbcTemplate jdbcTemplate;
    private String dataLocation;

    @Inject
    public void setOrmSessionFactory(IOrmSessionFactory ormSessionFactory) {
        this.ormSessionFactory = ormSessionFactory;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @InjectValue("@cfg:nop.orm.init-database-data-location|/_init-data/")
    public void setDataLocation(String dataLocation) {
        this.dataLocation = dataLocation;
    }

    @PostConstruct
    public void init() {
        LOG.info("nop.orm.init-database-data: location={}", dataLocation);

        IOrmModel ormModel = ormSessionFactory.getOrmModel();
        Collection<? extends IEntityModel> tables = ormModel.getEntityModelsInTopoOrder();

        ormTemplate.runInSession(() -> {
            for (IEntityModel entityModel : tables) {
                if (entityModel.isTableView())
                    continue;
                loadCsvData(entityModel);
            }
        });

        executeSqlFiles();
    }

    void loadCsvData(IEntityModel entityModel) {
        String tableName = entityModel.getTableName();
        String csvPath = StringHelper.appendPath(dataLocation, tableName + ".csv");
        IResource csvResource = ResourceHelper.resolve(csvPath);
        if (!csvResource.exists())
            return;

        LOG.info("nop.orm.load-csv-data: table={}, path={}", tableName, csvPath);

        List<Map<String, Object>> rows = CsvHelper.readCsv(csvResource);
        if (rows.isEmpty())
            return;

        IEntityDao<IOrmEntity> dao = daoProvider.daoForTable(tableName);

        for (Map<String, Object> row : rows) {
            IOrmEntity entity = dao.newEntity();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String colCode = entry.getKey();
                IColumnModel col = entityModel.getColumnByCode(colCode, false);
                entity.orm_propValue(col.getPropId(), entry.getValue());
            }
            dao.saveEntity(entity);
        }
    }

    void executeSqlFiles() {
        String dir = StringHelper.removeEnd(dataLocation, "/");
        if (dir.isEmpty())
            dir = "/";

        Collection<? extends IResource> sqlFiles = VirtualFileSystem.instance().getAllResources(dir, ".sql");
        if (sqlFiles == null || sqlFiles.isEmpty())
            return;

        List<? extends IResource> sorted = sqlFiles.stream()
                .sorted(Comparator.comparing(IResource::getName))
                .collect(Collectors.toList());

        jdbcTemplate.txn().runInTransaction(null, TransactionPropagation.REQUIRED, t -> {
            for (IResource sqlFile : sorted) {
                String sqlText = ResourceHelper.readText(sqlFile);
                if (StringHelper.isBlank(sqlText))
                    continue;
                LOG.info("nop.orm.execute-sql: path={}", sqlFile.getPath());
                jdbcTemplate.executeMultiSql(new SQL(sqlText));
            }
            return null;
        });
    }
}
