package io.nop.autotest.core.split;

import io.nop.commons.util.FileHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmEntity;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.apache.commons.csv.CSVFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.autotest.core.data.OrmModelHelper.getChgFileHeaders;
import static io.nop.autotest.core.data.OrmModelHelper.getColNames;

public class TestCaseJsonDataSplitter {
    static final Logger LOG = LoggerFactory.getLogger(TestCaseJsonDataSplitter.class);

    private final IDaoProvider daoProvider;
    private boolean silent;

    public TestCaseJsonDataSplitter(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public TestCaseJsonDataSplitter() {
        this(DaoProvider.instance());
    }

    public TestCaseJsonDataSplitter silent(boolean b) {
        this.silent = b;
        return this;
    }

    public void splitToDir(Map<String, Object> data, File dir) {
        Map<String, Object> inputData = (Map<String, Object>) data.get("input");
        if (inputData != null) {
            saveInputData(inputData, dir);
        }

        Map<String, Object> outputData = (Map<String, Object>) data.get("output");
        if (outputData != null) {
            saveOutputData(outputData, dir);
        }
    }

    private void saveOutputData(Map<String, Object> outputData, File dir) {
        outputData.forEach((name, value) -> {
            if (value == null)
                return;

            if (name.equals("entities")) {
                saveOutputEntities((Map<String, Object>) value, dir);
            } else if (name.equals("tables")) {
                saveOutputTables((Map<String, Object>) value, dir);
            } else {
                String text = JsonTool.serialize(value, true);
                FileHelper.writeText(getOutputFile(dir, name), text, null);
            }
        });
    }

    private void saveInputData(Map<String, Object> inputData, File dir) {
        inputData.forEach((name, value) -> {
            if (value == null)
                return;

            if (name.equals("entities")) {
                saveInputEntities((Map<String, Object>) value, dir);
            } else if (name.equals("tables")) {
                saveInputTables((Map<String, Object>) value, dir);
            } else {
                String text = JsonTool.serialize(value, true);
                FileHelper.writeText(getInputFile(dir, name), text, null);
            }
        });
    }

    private File getInputFile(File dir, String name) {
        File inputDir = new File(dir, "input");
        // 包含后缀名
        if (name.endsWith(".json") || name.endsWith(".json5") || name.endsWith(".csv")) {
            return new File(inputDir, name);
        }
        return new File(inputDir, name + ".json5");
    }

    private File getOutputFile(File dir, String name) {
        File outputDir = new File(dir, "output");
        if (name.endsWith(".json") || name.endsWith(".json5") || name.endsWith(".csv")) {
            return new File(outputDir, name);
        }
        return new File(outputDir, name + ".json5");
    }

    private void saveInputEntities(Map<String, Object> entities, File dir) {
        entities.forEach((name, data) -> {
            savaInputEntity(name, dir, (List<Map<String, Object>>) data);
        });
    }

    private void saveOutputEntities(Map<String, Object> entities, File dir) {
        entities.forEach((name, data) -> {
            savaOutputEntity(name, dir, (List<Map<String, Object>>) data);
        });
    }

    private void savaInputEntity(String name, File dir, List<Map<String, Object>> data) {
        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao) daoProvider.dao(name);
        if (dao == null) {
            if (!silent)
                throw new IllegalArgumentException("nop.err.autotest.invalid-dao:" + name);
            LOG.warn("nop.autotest.unknown-entity:{}", name);
            return;
        }
        IEntityModel entityModel = dao.getEntityModel();
        File entityFile = getInputFile(new File(dir, "tables"), entityModel.getTableName() + ".csv");
        List<String> headers = getColNames(entityModel);
        List<Map<String, Object>> rows = data.stream().map(item -> transformPropToCol(entityModel, item)).collect(Collectors.toList());
        CsvHelper.writeCsv(new FileResource(entityFile), CSVFormat.DEFAULT, headers, rows);
    }

    private void savaOutputEntity(String name, File dir, List<Map<String, Object>> data) {
        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao) daoProvider.dao(name);
        if (dao == null) {
            if (!silent)
                throw new IllegalArgumentException("nop.err.autotest.invalid-dao:" + name);
            LOG.warn("nop.autotest.unknown-entity:{}", name);
            return;
        }
        IEntityModel entityModel = dao.getEntityModel();
        File entityFile = getOutputFile(new File(dir, "tables"), entityModel.getTableName() + ".csv");
        List<String> headers = getChgFileHeaders(entityModel);
        List<Map<String, Object>> rows = data.stream().map(item -> transformPropToCol(entityModel, item)).collect(Collectors.toList());
        CsvHelper.writeCsv(new FileResource(entityFile), CSVFormat.DEFAULT, headers, rows);
    }

    private Map<String, Object> transformPropToCol(IEntityModel entityModel, Map<String, Object> data) {
        Map<String, Object> ret = new LinkedHashMap<>();
        if (data.containsKey(DaoConstants.PROP_CHANGE_TYPE))
            ret.put(DaoConstants.PROP_CHANGE_TYPE, data.get(DaoConstants.PROP_CHANGE_TYPE));

        for (IColumnModel colModel : entityModel.getColumns()) {
            Object value = data.get(colModel.getName());
            if (value != null || data.containsKey(colModel.getName())) {
                ret.put(colModel.getCode(), value);
            }
        }
        return ret;
    }

    private void saveInputTables(Map<String, Object> tables, File dir) {
        tables.forEach((tableName, data) -> {
            if (data instanceof List) {
                saveInputTableData(tableName, dir, (List<Map<String, Object>>) data);
            }
        });
    }

    private void saveInputTableData(String tableName, File dir, List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return;
        }

        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao) daoProvider.daoForTable(tableName);
        if (dao == null) {
            if (!silent) {
                throw new IllegalArgumentException("nop.err.autotest.invalid-table:" + tableName);
            }
            LOG.warn("nop.autotest.unknown-table:{}", tableName);
            return;
        }

        IEntityModel entityModel = dao.getEntityModel();
        File tableFile = getInputFile(new File(dir, "tables"), tableName + ".csv");

        // 获取表头时只保留存在的列
        List<String> headers = getColNames(entityModel);

        // 转换数据时只保留存在的列
        List<Map<String, Object>> rows = data.stream()
                .map(item -> filterColumnsByEntityModel(entityModel, item))
                .collect(Collectors.toList());

        CsvHelper.writeCsv(new FileResource(tableFile), CSVFormat.DEFAULT, headers, rows);
    }

    private Map<String, Object> filterColumnsByEntityModel(IEntityModel entityModel, Map<String, Object> data) {
        Map<String, Object> ret = new LinkedHashMap<>();
        if (data.containsKey(DaoConstants.PROP_CHANGE_TYPE)) {
            ret.put(DaoConstants.PROP_CHANGE_TYPE, data.get(DaoConstants.PROP_CHANGE_TYPE));
        }

        data.forEach((colCode, value) -> {
            if (entityModel.getColumnByCode(colCode, true) != null) {
                ret.put(colCode, value);
            }
        });
        return ret;
    }

    private void saveOutputTables(Map<String, Object> tables, File dir) {
        tables.forEach((tableName, data) -> {
            if (data instanceof List) {
                saveOutputTableData(tableName, dir, (List<Map<String, Object>>) data);
            }
        });
    }

    private void saveOutputTableData(String tableName, File dir, List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return;
        }

        IOrmEntityDao<IOrmEntity> dao = (IOrmEntityDao) daoProvider.daoForTable(tableName);
        if (dao == null) {
            if (!silent) {
                throw new IllegalArgumentException("nop.err.autotest.invalid-table:" + tableName);
            }
            LOG.warn("nop.autotest.unknown-table:{}", tableName);
            return;
        }

        IEntityModel entityModel = dao.getEntityModel();
        File tableFile = getOutputFile(new File(dir, "tables"), tableName + ".csv");

        // 获取表头时只保留存在的列
        List<String> headers = getChgFileHeaders(entityModel);

        // 转换数据时只保留存在的列
        List<Map<String, Object>> rows = data.stream()
                .map(item -> filterColumnsByEntityModel(entityModel, item))
                .collect(Collectors.toList());

        CsvHelper.writeCsv(new FileResource(tableFile), CSVFormat.DEFAULT, headers, rows);
    }

}
