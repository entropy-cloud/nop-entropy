package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityDefDirTransformer {
    private final Map<String, XNode> ormNodes = new HashMap<>();
    private final Map<String, XNode> entityNodes = new HashMap<>();
    private final Map<String, List<XNode>> unknownRelations = new HashMap<>();

    // 记录哪些ORM模型增加了哪些外部实体定义
    private final Map<XNode, Set<String>> externalEntityMap = new HashMap<>();

    public void transformDir(File dir, File targetDir) {

        // 先加载所有的entitydef模型，记录未识别的relation实体引用
        for (File file : dir.listFiles()) {
            XNode node = XNodeParser.instance().parseFromResource(new FileResource(file));
            EntityDefModelToOrmModel trans = new EntityDefModelToOrmModel();
            XNode ormNode = trans.transform(node);
            ormNodes.put(StringHelper.fileNameNoExt(file.getName()), ormNode);

            trans.mergeTo(entityNodes, unknownRelations);
        }

        resolveUnknownRelations();

        saveOrmNodes(targetDir);
    }

    void resolveUnknownRelations() {
        unknownRelations.forEach((name, list) -> {
            XNode refEntityNode = entityNodes.get(name);
            if (refEntityNode != null) {
                String fullName = refEntityNode.attrText("name");
                // 将relation的refEntityName设置为全名
                for (XNode refNode : list) {
                    refNode.setAttr("refEntityName", fullName);

                    // relation -> relations -> entity
                    XNode ormNode = refNode.root();
                    Set<String> externals = externalEntityMap.computeIfAbsent(ormNode, k -> new HashSet<>());
                    if (externals.add(fullName)) {
                        // 缺少external定义
                        XNode entities = ormNode.makeChild("entities");
                        XNode externalNode = refEntityNode.cloneInstance();
                        externalNode.setAttr("notGenCode", true);
                        entities.appendChild(externalNode);
                    }
                }
            } else {
                throw new IllegalArgumentException("nop.err.ofbiz.unresolved-relation-entity-name:" + name + "," + list.get(0));
            }
        });
    }

    void saveOrmNodes(File targetDir) {
        ormNodes.forEach((name, node) -> {
            File file = new File(targetDir, name + ".orm.xml");
            node.saveToResource(new FileResource(file), null);

           // DslModelHelper.loadDslModel(new FileResource(file));
        });
    }
}
