package io.nop.ofbiz.transform;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.ofbiz.OfbizConstants;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntityDefModelToOrmModel {
    private final OrmModel baseModel = (OrmModel) DslModelHelper.loadDslModelFromPath(OfbizConstants.PATH_OFBIZ_BASE_ORM);

    public static void transformDefFile(File defFile, File ormFile) {
        XNode node = XNodeParser.instance().parseFromResource(new FileResource(defFile));
        XNode ormNode = new EntityDefModelToOrmModel().transform(node);
        ormNode.saveToResource(new FileResource(ormFile), StringHelper.ENCODING_UTF8);
    }

    public XNode transform(XNode node) {
        XNode ret = XNode.make("orm");
        ret.setAttr("x:extends", OfbizConstants.PATH_OFBIZ_BASE_ORM);
        ret.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
        ret.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");

        XNode entities = ret.addChild("entities");

        Map<String, XNode> nodeMap = new HashMap<>();
        OrmModel ormModel = new OrmModel();
        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("entity")) {
                XNode entityNode = transformEntity(child, ormModel);
                String entityName = child.attrText("entity-name");
                entities.appendChild(entityNode);
                nodeMap.put(entityName, entityNode);
            }
        }

        Map<String, OrmEntityModel> shortNameMap = new HashMap<>();
        for (OrmEntityModel entityModel : ormModel.getEntities()) {
            shortNameMap.put(entityModel.getShortName(), entityModel);
        }

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("entity")) {
                String entityName = child.attrText("entity-name");
                XNode entityNode = nodeMap.get(entityName);
                transformRelation(entityNode, child, shortNameMap);
            }
        }

        return ret;
    }

    private XNode transformEntity(XNode node, OrmModel ormModel) {
        XNode ret = XNode.make("entity");
        String packageName = node.attrText("package-name");
        String entityName = node.attrText("entity-name");
        String name = StringHelper.fullClassName(entityName, packageName);
        String tableName = StringHelper.camelCaseToUnderscore(entityName, true);
        String title = node.attrText("title");
        ret.setAttr("name", name);
        ret.setAttr("className", name);
        ret.setAttr("tableName", tableName);
        ret.setAttr("displayName", title);

        XNode columns = ret.addChild("columns");

        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName(name);
        entityModel.setClassName(name);
        entityModel.setTableName(tableName);
        ormModel.addEntity(entityModel);

        Set<String> pkNames = getPrimaryKeys(node);

        int propId = 0;
        for (XNode child : node.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("field")) {
                XNode col = transformField(child, entityModel, pkNames);
                col.setAttr("propId", ++propId);
                columns.appendChild(col);
            }
        }

        return ret;
    }

    private XNode transformField(XNode node, OrmEntityModel entityModel, Set<String> pkNames) {
        String name = node.attrText("name");
        String type = node.attrText("type");
        String code = StringHelper.camelCaseToUnderscore(name, false);
        boolean primary = pkNames.contains(name);

        XNode ret = XNode.make("column");
        ret.setAttr("name", name);
        ret.setAttr("displayName", name);
        ret.setAttr("code", code);
        ret.setAttr("domain", type);
        ret.setAttr("primary", true);

        OrmDomainModel domain = baseModel.getDomain(type);
        ret.setAttr("stdSqlType", domain.getStdSqlType());
        ret.setAttr("precision", domain.getPrecision());
        ret.setAttr("scale", domain.getScale());

        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        col.setStdSqlType(domain.getStdSqlType());
        col.setPrecision(domain.getPrecision());
        col.setScale(domain.getScale());
        col.setPrimary(primary);

        entityModel.addColumn(col);
        return ret;
    }

    private Set<String> getPrimaryKeys(XNode node) {
        Set<String> ret = new HashSet<>();
        for (XNode child : node.getChildren()) {
            String field = child.attrText("field");
            ret.add(field);
        }
        return ret;
    }

    private void transformRelation(XNode ret, XNode node, Map<String, OrmEntityModel> shortMap) {
        XNode relations = ret.addChild("relations");
        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("relation")) {
                String type = child.attrText("type");
                String fkName = child.attrText("fk-name");
                String refEntityName = child.attrText("rel-entity-name");
                String title = child.attrText("title");

                if (title != null) {
                    title = title + StringHelper.simpleClassName(refEntityName);
                } else {
                    title = StringHelper.simpleClassName(refEntityName);
                }
                title = StringHelper.beanPropName(title);
                if (title.equals("class"))
                    title = "className";

                OrmEntityModel entityModel = shortMap.get(refEntityName);
                if (entityModel != null) {
                    refEntityName = entityModel.getName();
                }

                String relType = type.equals("one") || type.equals("one-nopk") ? "to-one" : "to-many";
                XNode relation = relations.addChild(relType);

                relation.setAttr("name", title);
                relation.setAttr("refEntityName", refEntityName);
                if (relType.equals("to-one"))
                    relation.setAttr("constraint", fkName);
                addJoin(relation, child);
            }
        }
    }

    private void addJoin(XNode relation, XNode bizRel) {
        XNode join = relation.addChild("join");
        for (XNode child : bizRel.getChildren()) {
            if (child.getTagName().equals("key-map")) {
                String fieldName = child.attrText("field-name");
                String refFieldName = child.attrText("ref-field-name", fieldName);

                XNode on = join.addChild("on");
                on.setAttr("leftProp", fieldName);
                on.setAttr("rightProp", refFieldName);
            }
        }
    }
}