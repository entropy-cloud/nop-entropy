package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.ofbiz.migration.OfbizMigrationConstants;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.HashMap;
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

        resolveRelations();

        entityDefModels.values().forEach(EntityDefModel::addExternalEntities);

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
            EntityDefModel defModel = new EntityDefModel(node, baseModel);
            entityDefModels.put(moduleName, defModel);
        }
    }

    void resolveRelations() {
        this.entityDefModels.values().forEach(defModel -> {
            defModel.resolveRelations(refEntityName -> {
                XNode node = entityMap.get(refEntityName);
                if (node == null)
                    throw new IllegalArgumentException("nop.err.ofbiz.unresolved-relation-entity-name:" + refEntityName);
                return node;
            });
        });
    }

    void saveOrmNodes(File targetDir) {
        entityDefModels.forEach((name, node) -> {
            File file = new File(targetDir, "ofbiz/" + name + "/app.orm.xml");
            node.getOrmNode().saveToResource(new FileResource(file), null);
            DslModelHelper.loadDslModel(new FileResource(file));
        });
    }
}
