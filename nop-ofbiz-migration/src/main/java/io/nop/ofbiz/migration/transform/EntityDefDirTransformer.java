package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ofbiz.migration.OfbizMigrationConstants;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityDefDirTransformer {
    private final OrmModel baseModel = (OrmModel) DslModelHelper.loadDslModelFromPath(OfbizMigrationConstants.PATH_OFBIZ_BASE_ORM);

    private final Map<String, EntityDefModel> entityDefModels = new HashMap<>();

    private final Map<String, XNode> entityMap = new HashMap<>();

    public void transformDir(File dir, File targetDir) {
        loadFromDir(dir);

        entityDefModels.values().forEach(defModel -> {
            defModel.collectEntities(entityMap);
        });

        transformViewAlias();

        resolveRelations();

        addExternalEntities();

        saveOrmNodes(targetDir);
    }

    public void loadFromDir(File dir) {
        // 先加载所有的entitydef模型，记录未识别的relation实体引用
        for (File file : dir.listFiles()) {
            String name = file.getName();
            if (!name.endsWith("-entitymodel.xml")) {
                continue;
            }
            String moduleName = StringHelper.removeTail(name, "-entitymodel.xml");

            XNode node = XNodeParser.instance().parseFromResource(new FileResource(file));
            EntityDefModel defModel = new EntityDefModel(moduleName, node, baseModel);
            entityDefModels.put(moduleName, defModel);
        }
    }

    void transformViewAlias() {
        entityDefModels.values().forEach(defModel -> {
            defModel.transformViewAlias(this::resolveViewEntity);
        });
    }

    XNode resolveViewEntity(String entityName) {
        XNode node = resolveEntity(entityName);
        if (node.childByTag("columns") != null)
            return node;
        String name = StringHelper.simpleClassName(node.attrText("name"));
        for (EntityDefModel defModel : this.entityDefModels.values()) {
            if (defModel.transformViewAlias(name, this::resolveViewEntity))
                return node;
        }
        return node;
    }

    void resolveRelations() {
        this.entityDefModels.values().forEach(defModel -> {
            defModel.resolveRelations(this::resolveEntity);
        });
    }

    void addExternalEntities() {
        this.entityDefModels.values().forEach(EntityDefModel::addExternalEntities);
    }

    XNode resolveEntity(String entityName) {
        XNode node = entityMap.get(entityName);
        if (node == null)
            node = entityMap.get(StringHelper.simpleClassName(entityName));
        if (node == null)
            throw new IllegalArgumentException("nop.err.ofbiz.unresolved-relation-entity-name:" + entityName);
        return node;
    }

    void saveOrmNodes(File targetDir) {
        entityDefModels.forEach((name, node) -> {
            String modulePath = "nop-ofbiz-" + name;
            File file = new File(targetDir, modulePath + "/nop-ofbiz-" + name + "-dao/src/main/resources/_vfs/ofbiz/" + name + "/orm/_app.orm.xml");
            node.getOrmNode().saveToResource(new FileResource(file), null);
            OrmModel ormModel = (OrmModel) DslModelHelper.loadDslModel(new FileResource(file));
            FileResource modelFile = new FileResource(new File(targetDir, modulePath + "/model/ofbiz-" + name + ".orm.xlsx"));
            saveOrmModel(ormModel, modelFile);
        });
    }

    void saveOrmModel(OrmModel ormModel, IResource modelFile) {
        ExcelReportHelper.saveXlsxObject("/nop/orm/imp/orm.imp.xml", modelFile, ormModel);

        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(modelFile);
        addCatalog(wk, ormModel);
        ExcelReportHelper.saveExcel(modelFile,wk);
    }

    public static void addCatalog(ExcelWorkbook workbook, OrmModel ormModel) {
        ExcelSheet sheet = workbook.getSheet("目录");
        ExcelTable table = sheet.getTable();
        List<? extends IEntityModel> tables = ormModel.getEntityModels();
        int index = 1;
        String styleId0 = table.getCell(1, 0).getStyleId();
        String styleId = table.getCell(1, 1).getStyleId();
        String styleId2 = table.getCell(1, 2).getStyleId();
        String styleId3 = table.getCell(1,3).getStyleId();

        for (IEntityModel entityModel : tables) {
            ExcelRow row = table.makeRow(index++);
            row.makeCell(0).setValue(index);
            ((ExcelCell) row.makeCell(0)).setStyleId(styleId0);

            ExcelCell cell = (ExcelCell) row.makeCell(1);
            cell.setValue(entityModel.getTableName());
            cell.setLinkUrl("ref:" + entityModel.getTableName() + "!A1");
            cell.setStyleId(styleId);

            ExcelCell cell2 = (ExcelCell) row.makeCell(2);
            cell2.setStyleId(styleId2);
            cell2.setValue(entityModel.getDisplayName());

            ExcelCell cell3 = (ExcelCell) row.makeCell(3);
            cell3.setStyleId(styleId3);
            cell3.setValue(entityModel.getComment());
        }
    }
}
