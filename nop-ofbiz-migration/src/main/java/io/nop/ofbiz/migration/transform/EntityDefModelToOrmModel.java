package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ofbiz.migration.OfbizMigrationConstants;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntityDefModelToOrmModel {
    private final OrmModel baseModel = (OrmModel) DslModelHelper.loadDslModelFromPath(OfbizMigrationConstants.PATH_OFBIZ_BASE_ORM);

    private final Map<String, XNode> entityNodes = new HashMap<>();
    private final Map<String, List<XNode>> unknownRelations = new HashMap<>();

    public Map<String, XNode> getEntityNodes() {
        return entityNodes;
    }

    public Map<String, List<XNode>> getUnknownRelations() {
        return unknownRelations;
    }

    public void mergeTo(Map<String, XNode> entityNodes, Map<String, List<XNode>> unknownRelations) {
        entityNodes.putAll(this.getEntityNodes());
        this.getUnknownRelations().forEach((name, list) -> {
            unknownRelations.computeIfAbsent(name, k -> new ArrayList<>()).addAll(list);
        });
    }

    public XNode transform(XNode node) {
        XNode ret = XNode.make("orm");
        ret.setAttr("x:extends", OfbizMigrationConstants.PATH_OFBIZ_BASE_ORM);
        ret.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
        ret.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");

        XNode entities = ret.addChild("entities");

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("entity")) {
                XNode entityNode = transformEntity(child);
                String entityName = child.attrText("entity-name");
                entities.appendChild(entityNode);
                if (entityNodes.containsKey(entityName))
                    throw new IllegalArgumentException("nop.err.ofbiz.duplicate-entity-name:" + entityName);
                entityNodes.put(entityName, entityNode);
            }
        }

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("entity")) {
                String entityName = child.attrText("entity-name");
                XNode entityNode = entityNodes.get(entityName);
                transformRelation(entityNode, child);
            }
        }

        return ret;
    }

    private XNode transformEntity(XNode node) {
        XNode ret = XNode.make("entity");
        String packageName = node.attrText("package-name");
        String entityName = node.attrText("entity-name");
        String name = StringHelper.fullClassName(entityName, packageName);
        String tableName = node.attrText("table-name");
        if (StringHelper.isEmpty(tableName)) {
            tableName = StringHelper.camelCaseToUnderscore(entityName, true);
        } else {
            tableName = tableName.toUpperCase(Locale.ROOT);
        }
        String title = node.attrText("title");
        ret.setAttr("name", name);
        ret.setAttr("className", name);
        ret.setAttr("tableName", tableName);
        ret.setAttr("displayName", title);

        XNode columns = ret.addChild("columns");

        Set<String> pkNames = getPrimaryKeys(node);

        int propId = 0;
        for (XNode child : node.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("field")) {
                XNode col = transformField(child, pkNames);
                col.setAttr("propId", ++propId);
                columns.appendChild(col);
            }
        }

        return ret;
    }

    private XNode transformField(XNode node, Set<String> pkNames) {
        String name = node.attrText("name");
        String type = node.attrText("type");
        String code = node.attrText("col-name");
        if (StringHelper.isEmpty(code)) {
            code = StringHelper.camelCaseToUnderscore(name, false);
        } else {
            code = code.toUpperCase(Locale.ROOT);
        }
        boolean primary = pkNames.contains(name);

        XNode ret = XNode.make("column");
        ret.setAttr("name", name);
        ret.setAttr("displayName", name);
        ret.setAttr("code", code);
        ret.setAttr("domain", type);
        if (primary)
            ret.setAttr("primary", primary);

        OrmDomainModel domain = baseModel.getDomain(type);
        ret.setAttr("stdSqlType", domain.getStdSqlType());
        ret.setAttr("precision", domain.getPrecision());
        ret.setAttr("scale", domain.getScale());

        return ret;
    }

    private Set<String> getPrimaryKeys(XNode node) {
        Set<String> ret = new HashSet<>();
        for (XNode child : node.getChildren()) {
            if (!child.getTagName().equals("prim-key"))
                continue;

            String field = child.attrText("field");
            ret.add(field);
        }
        return ret;
    }

    private void transformRelation(XNode ret, XNode node) {
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

                XNode entityModel = entityNodes.get(refEntityName);
                if (entityModel != null) {
                    refEntityName = entityModel.attrText("name");
                } else {
                    unknownRelations.computeIfAbsent(refEntityName, k -> new ArrayList<>()).add(child);
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