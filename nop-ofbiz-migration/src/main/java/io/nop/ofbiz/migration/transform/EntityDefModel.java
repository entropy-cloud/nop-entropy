package io.nop.ofbiz.migration.transform;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.xml.XNode;
import io.nop.ofbiz.migration.OfbizMigrationConstants;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class EntityDefModel {
    static final Logger LOG = LoggerFactory.getLogger(EntityDefModel.class);

    private final OrmModel baseModel;
    private final XNode node;
    private final XNode ormNode;
    // entityName -> [ofbizNode, ormNode]
    private final Map<String, Pair<XNode, XNode>> entityNodes = new HashMap<>();
    private final Map<String, Pair<XNode, XNode>> viewEntityNodes = new HashMap<>();
    private final Map<String, XNode> externalNodes = new HashMap<>();

    public EntityDefModel(String name, XNode node, OrmModel baseModel) {
        this.node = node;
        this.baseModel = baseModel;
        this.ormNode = transform(name, node);
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

    private XNode transform(String modelName, XNode node) {
        XNode ret = XNode.make("orm");
        ret.setAttr("x:extends", OfbizMigrationConstants.PATH_OFBIZ_BASE_ORM);
        ret.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
        ret.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
        ret.setAttr("ext:mavenArtifactId", "nop-ofbiz-" + modelName);
        ret.setAttr("ext:registerShortName", true);
        ret.setAttr("ext:mavenGroupId", "io.nop.app");
        ret.setAttr("ext:appName", "ofbiz-" + modelName);
        ret.setAttr("ext:platformVersion", "2.0.0-SNAPSHOT");
        ret.setAttr("ext:dialect", "mysql,oracle,postgresql");
        ret.setAttr("ext:mavenVersion", "1.0.0-SNAPSHOT");
        ret.setAttr("xmlns:i18n-en", "i18n-en");
        ret.setAttr("xmlns:ext", "ext");

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
        ret.setAttr("tagSet", "view");

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

    public boolean transformViewAlias(String entityName, Function<String, XNode> entityResolver) {
        Pair<XNode, XNode> pair = viewEntityNodes.get(entityName);
        if (pair == null)
            return false;
        transformViewAlias(pair.getFirst(), pair.getSecond(), entityResolver);
        return true;
    }

    public void transformViewAlias(Function<String, XNode> entityResolver) {
        this.viewEntityNodes.values().forEach(pair -> {
            transformViewAlias(pair.getFirst(), pair.getSecond(), entityResolver);
        });
    }

    private void transformViewAlias(XNode viewEntityNode, XNode entityNode, Function<String, XNode> entityResolver) {
        XNode columns = entityNode.childByTag("columns");
        // 如果已经具有columns，则视图对应的alias已经被解析
        if (columns != null && columns.hasChild())
            return;

        Map<String, String> aliasMap = new HashMap<>();

        viewEntityNode.childrenByTag("member-entity").forEach(child -> {
            String alias = child.attrText("entity-alias");
            String entityName = child.attrText("entity-name");
            aliasMap.put(alias, entityName);
        });

        columns = entityNode.addChild("columns");

        for (XNode child : viewEntityNode.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals("alias")) {
                // <alias entity-alias="CA" name="contentIdStart" field="contentIdTo"/>
                XNode colNode = makeAliasColNode(child, columns, aliasMap, entityResolver);
                if (colNode != null) {
                    columns.appendChild(colNode);
                }
            } else if (tagName.equals("alias-all")) {
                //  <alias-all entity-alias="CA" prefix="ca"/>
                makeAliasAll(child, columns, aliasMap, entityResolver);
            }
        }

        for (int i = 0, n = columns.getChildCount(); i < n; i++) {
            XNode col = columns.child(i);
            col.setAttr("propId", i + 1);
        }
    }

    private XNode makeAliasColNode(XNode child, XNode columns, Map<String, String> aliasMap,
                                   Function<String, XNode> entityResolver) {
        String entityAlias = child.attrText("entity-alias");
        String entityName = aliasMap.get(entityAlias);
        if (entityName == null)
            throw new IllegalArgumentException("nop.err.ofbiz.unknown-member-alias:" + entityAlias + "," + child);

        String name = child.attrText("name");
        String field = child.attrText("field");
        if (field == null)
            field = name;

        // 如果已经存在同名的列，则跳过
        if (columns.childWithAttr("name", name) != null)
            return null;

        XNode entityNode = entityResolver.apply(entityName);
        if (entityNode == null)
            return null;

        String type = getAliasType(child);
        if (type != null) {
            XNode col = XNode.make("column");
            col.setAttr("name", name);
            if (!name.equals(field))
                col.setAttr("ext:baseName", field);
            col.setAttr("code", StringHelper.camelCaseToUnderscore(name, false));
            setType(col, type);
            return col;
        }

        XNode refCols = entityNode.childByTag("columns");
        XNode col = refCols.childWithAttr("name", field);
        if (col == null)
            col = refCols.childWithAttr("ext:baseName", field);

        if (col == null) {
            LOG.error("nop.err.ofbiz.ref-unknown-col:name={},alias={},refEntity={}", name, child, entityNode);
            return null;
        }

        col = col.cloneInstance();
        col.setAttr("name", name);
        col.setAttr("ext:viewAlias", entityAlias);
        col.setAttr("code", StringHelper.camelCaseToUnderscore(name, false));
        if (!name.equals(field))
            col.setAttr("ext:baseName", field);

        return col;
    }

    String getAliasType(XNode child) {
        String func = child.attrText("function");
        if (func != null) {
            if (func.equals("sum") || func.equals("avg")) {
                return "fixed-point";
            } else if (func.equals("count")) {
                return "numeric";
            }
        }

        if (child.hasChild("complex-alias")) {
            // 动态表达式计算得到字段

            String operator = getComplexAliasOperator(child);
            if ("-".equals(operator) || "+".equals(operator) || "*".equals(operator)) {
                return "fixed-point";
            } else if ("and".equals(operator) || "or".equals(operator) || "not".equals(operator)) {
                return "boolean";
            }
            return "long-varchar";
        }
        return null;
    }

    String getComplexAliasOperator(XNode node) {
        XNode complexAlias = node.childByTag("complex-alias");
        if (complexAlias == null)
            return null;
        return complexAlias.attrText("operator");
    }

    private void makeAliasAll(XNode aliasAll, XNode columns, Map<String, String> aliasMap,
                              Function<String, XNode> entityResolver) {
        String alias = aliasAll.attrText("entity-alias");
        String prefix = aliasAll.attrText("prefix");
        String entityName = aliasMap.get(alias);

        List<String> excludes = new ArrayList<>();
        for (XNode child : aliasAll.getChildren()) {
            if (child.getTagName().equals("exclude")) {
                String field = child.attrText("field");
                excludes.add(field);
            }
        }

        XNode entityNode = entityResolver.apply(entityName);
        XNode entityCols = entityNode.childByTag("columns");
        if (entityCols == null)
            throw new IllegalArgumentException("nop.err.ofbiz.entity-no-cols::" + entityName);

        for (XNode col : entityCols.getChildren()) {
            String name = col.attrText("name");
            if (excludes.contains(name))
                continue;
            if (name == null)
                throw new IllegalArgumentException("nop.err.null-col-name:" + col);

            col = col.cloneInstance();
            col.setAttr("ext:viewAlias", alias);
            if (prefix != null) {
                name = prefix + StringHelper.capitalize(name);
                col.setAttr("name", name);
                col.setAttr("code", StringHelper.camelCaseToUnderscore(name, false));
            }
            // 如果列已经存在，则跳过处理
            if (columns.childWithAttr("name", name) != null)
                continue;
            columns.appendChild(col);
        }
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

        if (primary)
            ret.setAttr("primary", primary);

        setType(ret, type);
        return ret;
    }

    private void setType(XNode ret, String type) {
        ret.setAttr("domain", type);
        OrmDomainModel domain = baseModel.getDomain(type);
        ret.setAttr("stdSqlType", domain.getStdSqlType());
        ret.setAttr("precision", domain.getPrecision());
        ret.setAttr("scale", domain.getScale());
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
        XNode columns = ret.childByTag("columns");
        XNode relations = ret.addChild("relations");
        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("relation")) {
                String type = child.attrText("type");
                String fkName = child.attrText("fk-name");
                String refEntityName = child.attrText("rel-entity-name");
                String title = child.attrText("title");

                String name = StringHelper.replace(title, " ", "_");
                if (name != null) {
                    name = name + StringHelper.simpleClassName(refEntityName);
                } else {
                    name = StringHelper.simpleClassName(refEntityName);
                }
                name = StringHelper.beanPropName(name);
                if (name.equals("class"))
                    name = "className";

                String fullName = resolveRefEntity(refEntityName, externalEntityResolver);

                String relType = type.equals("one") || type.equals("one-nopk") ? "to-one" : "to-many";
                XNode relation = relations.addChild(relType);

                // 如果关联的名称与字段名相同，则为关联属性增加额外后缀
                if (columns.childWithAttr("name", name) != null) {
                    name += "Obj";
                }
                relation.setAttr("name", name);
                relation.setAttr("refEntityName", fullName);
                if (relType.equals("to-one"))
                    relation.setAttr("constraint", fkName);

                // OFBiz的表之间存在循环依赖
                if ("to-one".equals(relType))
                    relation.setAttr("ignoreDepends", true);
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
                String refFieldName = child.attrText("rel-field-name");
                if (refFieldName == null)
                    refFieldName = fieldName;

                XNode on = join.addChild("on");
                on.setAttr("leftProp", fieldName);
                on.setAttr("rightProp", refFieldName);
            }
        }
    }
}