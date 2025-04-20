package io.nop.ai.coder.orm;

import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.dao.dialect.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiOrmModelNormalizer {
    static final Logger LOG = LoggerFactory.getLogger(AiOrmModelNormalizer.class);

    public XNode normalizeOrm(XNode node, AiOrmConfig config) {
        if (!node.hasAttr("x:schema"))
            node.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
        node.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");

        if (config.getBasePackageName() != null)
            node.setAttr("ext:basePackageName", config.getBasePackageName());

        XNode entities = normalizeEntities(node);
        if (entities != null) {
            for (XNode entity : entities.getChildren()) {
                normalizeEntity(entity, config);
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("nop.ai.normalize:node=\n{}", node.xml());

        return node;
    }

    private XNode normalizeEntities(XNode node) {
        XNode entities = node.childByTag("entities");
        if (entities == null) {
            if (node.childByTag("entity") != null) {
                entities = XNode.make("entities");
                node.wrapChildren(entities);
            }
        }
        return entities;
    }


    public XNode normalizeEntity(XNode entity, AiOrmConfig config) {
        String entityName = entity.attrText("name");
        String code = StringHelper.camelCaseToUnderscore(StringHelper.firstPart(entityName, '.'), true);
        entity.setAttr("tableName", code);

        String name = StringHelper.camelCase(code, true);
        entity.setAttr("name", name);
        if (StringHelper.isEmpty(entity.attrText("displayName")))
            entity.setAttr("displayName", name);

        String className = StringHelper.simpleClassName(name);
        entity.setAttr("className", StringHelper.fullClassName(className, config.getBasePackageName()));

        XNode columns = entity.makeChild("columns");
        int nextId = 1;
        for (XNode col : columns.getChildren()) {
            normalizeCol(col);
            col.setAttr("propId", nextId++);
        }

        normalizeRelations(entity, config);
        return entity;
    }

    void normalizeRelations(XNode entity, AiOrmConfig config) {
        XNode columns = entity.makeChild("columns");

        for (XNode col : columns.getChildren()) {
            String refTable = col.attrText("orm:ref-table");
            if (refTable != null) {
                String refEntityName = StringHelper.camelCaseToUnderscore(StringHelper.firstPart(refTable, '.'), true);
                refEntityName = StringHelper.camelCase(refEntityName, true);
                refEntityName = StringHelper.fullClassName(refEntityName, config.getBasePackageName());

                String refProp = col.attrText("orm:ref-prop");
                if (refProp != null) {
                    refProp = StringHelper.camelCaseToUnderscore(refProp, false);
                    refProp = StringHelper.camelCase(refProp, false);
                }
                String refPropDisplayName = col.attrText("orm:ref-prop-display-name");
                if (refPropDisplayName == null)
                    refPropDisplayName = refProp;

                XNode relations = entity.makeChild("relations");
                String colCode = col.attrText("code");
                String relName = getRelationNameFromColCode(colCode, refEntityName);
                XNode relation = relations.makeChildWithAttr("to-one", "name", relName);
                relation.setAttr("refEntityName", refEntityName);
                relation.setAttr("refPropName", refProp);
                relation.setAttr("refDisplayName", refPropDisplayName);

                XNode on = relation.makeChild("join").makeChild("on");
                on.setAttr("leftProp", col.attrText("name"));
                on.setAttr("rightProp", "id");
            }
        }
    }

    public XNode normalizeCol(XNode col) {
        String colName = col.attrText("name");
        String code = StringHelper.camelCaseToUnderscore(colName, false);
        String name = StringHelper.camelCase(code, false);
        String sqlType = col.attrText("sqlType");
        if (!StringHelper.isEmpty(sqlType)) {
            sqlType = sqlType.trim().toUpperCase();
            sqlType = StringHelper.replace(sqlType, " AUTO_INCREMENT", "");
            SQLDataType dataType = SQLDataType.parse(sqlType);
            col.setAttr("stdSqlType", AiOrmSqlType.getStdSqlType(dataType.getName()));
            if (dataType.isAllowPrecision())
                col.setAttr("precision", dataType.getPrecision());
            if (dataType.isAllowScale())
                col.setAttr("scale", dataType.getScale());
        } else {
            col.setAttr("stdSqlType", StdSqlType.VARCHAR);
            if (!col.hasAttr("precision"))
                col.setAttr("precision", 50);
        }
        col.setAttr("name", name);
        col.setAttr("code", code);

        return col;
    }

    private String getRelationNameFromColCode(String colCode, String refEntityName) {
        if (colCode.equalsIgnoreCase("_id") || colCode.endsWith("id"))
            return StringHelper.firstPart(refEntityName, '.');

        if (StringHelper.endsWithIgnoreCase(colCode, "_id")) {
            return StringHelper.camelCase(colCode.substring(0, colCode.length() - "_id".length()), false);
        }
        return StringHelper.camelCase(colCode, false) + "Obj";
    }
}