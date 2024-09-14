package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.xml.XNode;
import io.nop.ofbiz.migration.OfbizMigrationConstants;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class EntityDefModel {
    private final OrmModel baseModel;
    private final XNode node;
    private final XNode ormNode;
    private final Map<String, Pair<XNode, XNode>> entityNodes = new HashMap<>();
    private final Map<String, Pair<XNode, XNode>> viewEntityNodes = new HashMap<>();
    private final Map<String, XNode> externalNodes = new HashMap<>();

    public EntityDefModel(XNode node, OrmModel baseModel) {
        this.node = node;
        this.baseModel = baseModel;
        this.ormNode = transform(node);
    }

    public XNode getDefNode() {
        return node;
    }

    public XNode getOrmNode() {
        return ormNode;
    }

    public void collectEntities(Map<String, XNode> map) {
        entityNodes.forEach((name, pair) -> {
            if (map.containsKey(name))
                throw new IllegalArgumentException("nop.err.ofbiz.duplicate-entity-name:name=" + name + "," + pair.getSecond());
            map.put(name, pair.getSecond());
        });

        viewEntityNodes.forEach((name, pair) -> {
            if (map.containsKey(name))
                throw new IllegalArgumentException("nop.err.ofbiz.duplicate-entity-name:name=" + name + "," + pair.getSecond());
            map.put(name, pair.getSecond());
        });
    }

    public void addExternalEntities() {
        // 缺少external定义
        XNode entities = ormNode.makeChild("entities");
        for (XNode node : this.externalNodes.values()) {
            XNode externalNode = node.cloneInstance();
            externalNode.getChildren().removeIf(child -> !child.getTagName().equals("columns"));
            externalNode.setAttr("notGenCode", true);
            entities.appendChild(externalNode);
        }
    }

    private XNode transform(XNode node) {
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
                entityNodes.put(entityName, Pair.of(child, entityNode));
            }
        }

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("view-entity")) {
                XNode entityNode = transformViewEntity(child);
                String entityName = child.attrText("entity-name");
                entities.appendChild(entityNode);
                if (entityNodes.containsKey(entityName))
                    throw new IllegalArgumentException("nop.err.ofbiz.duplicate-entity-name:" + entityName);
                viewEntityNodes.put(entityName, Pair.of(child, entityNode));
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

    private XNode transformViewEntity(XNode node) {
        XNode ret = XNode.make("entity");
        ret.setAttr("tableView", true);
        ret.setAttr("noPrimaryKey", true);
        ret.setAttr("readonly", true);

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

        return ret;
    }

    private void transformViewAlias(XNode viewEntityNode, XNode entityNode) {
        Map<String, String> aliasMap = new HashMap<>();

        viewEntityNode.childrenByTag("member-entity").forEach(child -> {
            String alias = child.attrText("entity-alias");
            String entityName = child.attrText("entity-name");
            aliasMap.put(alias, entityName);
        });


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

    public void resolveRelations(Function<String, XNode> refEntityResolver) {
        entityNodes.values().forEach(pair -> {
            transformRelation(pair.getSecond(), pair.getFirst(), refEntityResolver);
        });

        viewEntityNodes.values().forEach(pair -> {
            transformRelation(pair.getSecond(), pair.getFirst(), refEntityResolver);
        });
    }

    private void transformRelation(XNode ret, XNode node, Function<String, XNode> externalEntityResolver) {
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

                String fullName = resolveRefEntity(refEntityName, externalEntityResolver);

                String relType = type.equals("one") || type.equals("one-nopk") ? "to-one" : "to-many";
                XNode relation = relations.addChild(relType);

                relation.setAttr("name", title);
                relation.setAttr("refEntityName", fullName);
                if (relType.equals("to-one"))
                    relation.setAttr("constraint", fkName);
                addJoin(relation, child);
            }
        }
    }

    private String resolveRefEntity(String refEntityName, Function<String, XNode> externalEntityResolver) {
        Pair<XNode, XNode> pair = entityNodes.get(refEntityName);
        if (pair != null)
            return pair.getSecond().attrText("name");
        pair = viewEntityNodes.get(refEntityName);
        if (pair != null)
            return pair.getSecond().attrText("name");
        XNode node = externalEntityResolver.apply(refEntityName);
        externalNodes.put(refEntityName, node);
        return node.attrText("name");
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