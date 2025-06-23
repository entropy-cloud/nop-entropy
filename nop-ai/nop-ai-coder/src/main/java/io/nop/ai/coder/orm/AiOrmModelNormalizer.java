package io.nop.ai.coder.orm;

import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.dao.dialect.SQLDataType;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
        fixId(entity);

        return entity;
    }

    protected void fixId(XNode entity) {
        XNode columns = entity.makeChild("columns");
        List<XNode> columnList = columns.getChildren();
        for (XNode col : columnList) {
            if (!"id".equals(col.attrText("name")))
                col.removeAttr("primary");
        }

        XNode idCol = columns.childWithAttr("name", "id");
        if (idCol == null) {
            idCol = XNode.make("column");
            idCol.setAttr("name", "id");
            idCol.setAttr("displayName", "ID");
            idCol.setAttr("primary", "true");
            idCol.setAttr("stdSqlType", StdSqlType.VARCHAR);
            idCol.setAttr("precision", 36);
            columns.prependChild(idCol);
        }
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
            node.setAttr("x:schema", OrmModelConstants.XDSL_SCHEMA_ORM);
        node.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
        node.setAttr(OrmModelConstants.EXT_ALLOW_ID_AS_COL_NAME, true);
        node.setAttr("ext:registerShortName", true);
        node.setAttr("ext:useStdSysFields", true);

        if (config.getBasePackageName() != null)
            node.setAttr(OrmModelConstants.EXT_BASE_PACKAGE_NAME, config.getBasePackageName());
        node.setAttr(OrmModelConstants.EXT_ENTITY_PACKAGE_NAME, config.getEntityPackageName());
        node.setAttr(OrmModelConstants.EXT_APP_NAME, config.getAppName());
        node.setAttr(OrmModelConstants.EXT_MAVEN_GROUP_ID, config.getMavenGroupId());
        node.setAttr(OrmModelConstants.EXT_MAVEN_ARTIFACT_ID, config.getMavenArtifactId());

        XNode entities = normalizeEntities(node);
        if (entities != null) {
            for (XNode entity : entities.getChildren()) {
                normalizeEntity(entity, config);
            }
        }

        AiCoderHelper.addXmlNs(node);

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

        String className = StringHelper.fullClassName(StringHelper.simpleClassName(name), config.getEntityPackageName());
        entity.setAttr("className", className);
        entity.setAttr("name", className);

        XNode columns = entity.makeChild("columns");
        int nextId = 1;
        for (XNode col : columns.getChildren()) {
            normalizeCol(col, nextId++);

            normalizeRelation(col, entity, config);
        }

        return entity;
    }

    void normalizeCol(XNode col, int propId) {
        String colName = col.attrText("name");
        col.setAttr("code", AiCoderHelper.underscoreName(colName, true));

        col.setAttr("propId", propId);
        String stdDomain = col.attrText("stdDomain");
        if ("image".equals(stdDomain) || "file".equals(stdDomain)) {
            col.setAttr("stdSqlType", StdSqlType.VARCHAR);
            col.setAttr("precision", 200);
        } else if ("imageList".equals(stdDomain) || "fileList".equals(stdDomain)) {
            col.setAttr("stdSqlType", StdSqlType.VARCHAR);
            col.setAttr("precision", 1000);
        }
    }

    void normalizeRelation(XNode col, XNode entity, AiOrmConfig config) {
        String refTable = col.attrText("orm:ref-table");
        if (refTable != null) {
            String refEntityName = StringHelper.simpleClassName(refTable);

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