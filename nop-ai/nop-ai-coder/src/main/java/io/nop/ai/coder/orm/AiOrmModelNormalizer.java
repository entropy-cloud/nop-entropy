package io.nop.ai.coder.orm;

import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.dao.dialect.SQLDataType;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class AiOrmModelNormalizer {
    static final Logger LOG = LoggerFactory.getLogger(AiOrmModelNormalizer.class);

    public XNode fixNameForOrmNode(XNode node) {
        if (node == null)
            return null;
        XNode entities = normalizeEntities(node);
        if (entities != null) {
            for (XNode entity : entities.getChildren()) {
                fixNameForEntityNode(entity);
            }
        }
        return node;
    }

    protected XNode fixNameForEntityNode(XNode entity) {
        String entityName = entity.attrText("name");
        String name = AiCoderHelper.camelCaseName(StringHelper.lastPart(entityName, '.'), true);

        entity.setAttr("name", name);

        XNode columns = entity.makeChild("columns");
        for (XNode col : columns.getChildren()) {
            fixNameForColNode(col);
            fixRefName(col);
        }

        return entity;
    }

    protected XNode fixNameForColNode(XNode col) {
        String colName = col.attrText("name");
        String name = AiCoderHelper.camelCaseName(colName, false);
        String stdDomain = col.attrText("stdDomain");
        if (stdDomain != null) {
            stdDomain = stdDomain.trim().toLowerCase(Locale.ROOT);
            IStdDomainHandler domainHandler = getStdDomainHandler(stdDomain);
            if (domainHandler != null) {
                col.setAttr("stdDomain", stdDomain);
            } else {
                col.removeAttr("stdDomain");
            }
        }
        String sqlType = col.attrText("sqlType");
        if (sqlType == null) {
            sqlType = col.attrText("stdSqlType");
        }
        col.removeAttr("sqlType");

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
        return col;
    }

    protected IStdDomainHandler getStdDomainHandler(String stdDomain) {
        return StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
    }

    void fixRefName(XNode col) {
        String refTable = col.attrText("orm:ref-table");
        if (refTable != null) {
            String refEntityName = AiCoderHelper.camelCaseName(StringHelper.lastPart(refTable, '.'), true);
            col.setAttr("orm:ref-table", refEntityName);

            String refProp = col.attrText("orm:ref-prop");
            if (refProp != null) {
                refProp = AiCoderHelper.camelCaseName(refProp, false);
                col.setAttr("orm:ref-prop", refProp);
            }
        }
    }

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
        String code = AiCoderHelper.underscoreName(StringHelper.lastPart(entityName, '.'), true);
        entity.setAttr("tableName", code);
        entity.setAttr("registerShortName", true);

        String name = AiCoderHelper.camelCaseName(code, true);
        if (StringHelper.isEmpty(entity.attrText("displayName")))
            entity.setAttr("displayName", name);

        String className = StringHelper.fullClassName(StringHelper.simpleClassName(name), config.getBasePackageName());
        entity.setAttr("className", className);
        entity.setAttr("name", className);

        XNode columns = entity.makeChild("columns");
        int nextId = 1;
        for (XNode col : columns.getChildren()) {
            String colName = col.attrText("name");
            col.setAttr("code", AiCoderHelper.underscoreName(colName, true));

            col.setAttr("propId", nextId++);

            normalizeRelation(col, entity, config);
        }

        return entity;
    }

    void normalizeRelation(XNode col, XNode entity, AiOrmConfig config) {
        String refTable = col.attrText("orm:ref-table");
        if (refTable != null) {
            String refEntityName = StringHelper.fullClassName(refTable, config.getBasePackageName());

            String refProp = col.attrText("orm:ref-prop");
            String refPropDisplayName = col.attrText("orm:ref-prop-display-name");
            if (refPropDisplayName == null)
                refPropDisplayName = refProp;

            // 新建relation节点但是并没有删除column上的orm:ref-table等属性
            XNode relations = entity.makeChild("relations");
            String colCode = col.attrText("code");
            String relName = AiCoderHelper.getRelationNameFromColCode(colCode, refEntityName);
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