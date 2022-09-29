package io.nop.autotest.core.execute;

import io.nop.autotest.core.data.AutoTestCaseData;
import io.nop.autotest.core.data.AutoTestDataHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvHelper;
import io.nop.dao.DaoConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.apache.commons.csv.CSVFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class AutoTestCaseDataSaver {
    private final AutoTestCaseData caseData;
    private final AutoTestOrmHook ormHook;
    private final String variant;

    public AutoTestCaseDataSaver(String variant, AutoTestCaseData caseData,
                                 AutoTestOrmHook ormHook) {
        this.variant = variant;
        this.caseData = caseData;
        this.ormHook = ormHook;
    }

    public void saveCollectedData() {
        Map<IEntityModel, Map<String, EntityRow>> dataMap = ormHook.getDataMap();

        TagVarCollector varCollector = new TagVarCollector();

        Map<IEntityModel, List<EntityRow>> changed = new HashMap<>();

        for (Map.Entry<IEntityModel, Map<String, EntityRow>> entry : dataMap.entrySet()) {
            IEntityModel entityModel = entry.getKey();
            Collection<EntityRow> rows = new TreeMap<>(entry.getValue()).values();

            List<EntityRow> loadedRows = getLoadedRows(rows);
            List<EntityRow> changedRows = getChangedRows(rows);

            if (!loadedRows.isEmpty() || changedRows.isEmpty()) {
                // 即使没有装载到数据，也需要保留空文件记录。这样数据初始化的时候可以知道需要新建对应的表。
                // 有可能是执行查询但是没有查询到数据记录。
                saveInputTable(entityModel, loadedRows);
            }

            if (!changedRows.isEmpty()) {
                varCollector.add(entityModel);
                changed.put(entityModel, changedRows);
            }
        }

        for (Map.Entry<IEntityModel, List<EntityRow>> entry : changed.entrySet()) {
            saveOutputTable(entry.getKey(), entry.getValue(), varCollector);
        }
    }

    private void saveInputTable(IEntityModel entityModel, List<EntityRow> rows) {
        // variant下的input数据目前需要手工编写，不会自动生成
        if (AutoTestDataHelper.isDefaultVariant(variant)) {
            File file = caseData.getInputTableFile(entityModel.getTableName(), variant);

            List<Map<String, Object>> data = rows.stream().map(EntityRow::getInitData).collect(Collectors.toList());
            CsvHelper.writeCsv(new FileResource(file), CSVFormat.DEFAULT, getColNames(entityModel), data);
        }
    }

    private List<String> getColNames(IEntityModel entityModel) {
        List<String> ret = new ArrayList<>(entityModel.getColumns().size());
        for (IColumnModel col : entityModel.getColumns()) {
            ret.add(col.getCode());
        }
        return ret;
    }

    private void saveOutputTable(IEntityModel entityModel, List<EntityRow> rows,
                                 TagVarCollector varCollector) {
        File file = caseData.getOutputTableFile(entityModel.getTableName(), variant);

        List<Map<String, Object>> data = rows.stream().map(r -> {
            Map<String, Object> row = new HashMap<>(r.getChangedData());
            row.putAll(r.getChangedData());
            row = varCollector.replaceVars(entityModel.getTableName(), row);
            row.put(DaoConstants.PROP_CHANGE_TYPE, r.getChangeType().toString());
            return row;
        }).collect(Collectors.toList());
        List<String> headers = new ArrayList<>();
        headers.add(DaoConstants.PROP_CHANGE_TYPE);
        headers.addAll(getColNames(entityModel));
        CsvHelper.writeCsv(new FileResource(file), CSVFormat.DEFAULT, headers, data);
    }

    List<EntityRow> getLoadedRows(Collection<EntityRow> rows) {
        return rows.stream().filter(EntityRow::isLoadData).collect(Collectors.toList());
    }

    List<EntityRow> getChangedRows(Collection<EntityRow> rows) {
        return rows.stream().filter(EntityRow::hasChangedData).collect(Collectors.toList());
    }
}