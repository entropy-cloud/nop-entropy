/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.query.OrderBySqlParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.DialectSelector;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.SQLDataType;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import io.nop.orm.model.*;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.*;
import static io.nop.orm.pdm.PdmModelConstants.*;

public class PdmModelParser extends AbstractResourceParser<OrmModel> {
    final Logger LOG = LoggerFactory.getLogger(PdmModelParser.class);

    private final Map<String, OrmColumnModel> columns = new HashMap<>();
    private final Map<String, OrmEntityModel> tables = new HashMap<>();
    private final Map<String, OrmEntityModel> tablesByCode = new CaseInsensitiveMap<>();
    private final Map<String, OrmPackageModel> packages = new HashMap<>();

    private final Map<String, OrmDomainModel> domains = new HashMap<>();
    private final Map<String, OrmDomainModel> domainsByName = new TreeMap<>();
    private IDialect dialect;

    private final Map<String, PdmShortcut> shortcuts = new HashMap<>();
    private final Map<String, OrmIndexModel> indexes = new HashMap<>();
    private final Map<String, OrmUniqueKeyModel> uniqueKeys = new HashMap<>();
    private final Map<String, OrmReferenceModel> references = new HashMap<>();

    @Override
    protected OrmModel doParseResource(IResource resource) {
        XNode node = XNodeParser.instance().parseFromResource(resource);
        this.dialect = getDialect(node);

        XNode modelNode = node.childByTag("o:RootObject").childByTag("c:Children").childByTag("o:Model");

        OrmModel model = new OrmModel();
        PdmElement elm = parseElement(modelNode);
        model.setDisplayName(elm.getName());
        model.setExtProps(elm.getExtProps());

        parseDomains(modelNode);
        parsePackages(modelNode, null);

        if (modelNode.element(TABLES_NAME) != null) {
            parseTables(modelNode, null);
        }

        // 删除没有定义主键的视图对象。所有实体都必须具有主键
        removeViewsNoPk();

        parseAllReferences(modelNode);

        Map<String, OrmEntityModel> sorted = new TreeMap<>(tablesByCode);
        model.setEntities(new ArrayList<>(sorted.values()));

        model.setDomains(new ArrayList<>(domainsByName.values()));

        model.init();
        return model;
    }

    void removeViewsNoPk() {
        List<OrmEntityModel> views = tables.values().stream()
                .filter(tbl -> tbl.isReadonly() && tbl.getPkColumns().isEmpty()).collect(Collectors.toList());

        for (OrmEntityModel view : views) {
            tablesByCode.remove(view.getTableName());
        }
    }

    String getTarget(XNode node) {
        String instruction = node.getInstruction();
        if (instruction == null) {
            return DaoConstants.DIALECT_MYSQL;
        }
        int pos = instruction.indexOf(TARGET_PREFIX);
        if (pos > 0) {
            int pos2 = instruction.indexOf("\"", pos + TARGET_PREFIX.length());
            if (pos2 > 0) {
                String target = instruction.substring(pos + TARGET_PREFIX.length(), pos2);
                Map<String, List<DialectSelector>> selectors = DialectManager.instance().getDialectSelectors();
                for (Map.Entry<String, List<DialectSelector>> entry : selectors.entrySet()) {
                    for (DialectSelector selector : entry.getValue()) {
                        if (selector.getPdmTargetType() != null && matchTargetType(target, selector.getPdmTargetType()))
                            return selector.getDialectName();
                    }
                }
            }
        }
        return DaoConstants.DIALECT_MYSQL;
    }

    boolean matchTargetType(String type, String targetType) {
        List<String> words = StringHelper.stripedSplit(type.toLowerCase(), ' ');
        List<String> targetWords = StringHelper.stripedSplit(targetType.toLowerCase(), ' ');
        return words.containsAll(targetWords);
    }

    IDialect getDialect(XNode node) {
        String target = getTarget(node);
        return DialectManager.instance().getDialect(target);
    }

    PdmElement parseElement(XNode node) {
        String id = node.attrText(ID_NAME);
        String code = node.elementText(CODE_NAME);

        // name有可能要继续解析，因此这里不作intern操作。
        String name = node.elementText(NAME_NAME);

        // 兼容以前的配置格式
        if (name.startsWith("*"))
            name = name.substring(1);

        String comment = node.elementText(COMMENT_NAME);

        String gen = node.elementText(GENERATED_NAME);

        Set<String> stereoType = ConvertHelper.toCsvSet(node.elementText(STEREOTYPE_NAME), NopException::new);

        PdmElement elm = new PdmElement();
        elm.setId(id);
        elm.setCode(code);
        elm.setName(name);
        elm.setNotGenCode("0".equals(gen));
        elm.setTagSet(stereoType);

        if (comment != null) {
            comment = comment.trim();
            List<String> lines = StringHelper.split(comment, '\n');
            for (int i = 0, n = lines.size(); i < n; i++) {
                String line = StringHelper.strip(lines.get(i));
                if (line == null)
                    continue;
                if (line.length() >= 3 && StringHelper.isAllChar(line, '-')) {
                    comment = StringHelper.join(lines.subList(0, i), "\r\n");
                    if (i != n - 1) {
                        String attrText = StringHelper.join(lines.subList(i + 1, lines.size()), "\n");
                        JsonParseOptions options = new JsonParseOptions();
                        options.setStrictMode(false);
                        Map<String, Object> attrs = (Map<String, Object>) JsonTool.instance().parseFromText(null,
                                attrText, options);
                        elm.setExtProps(attrs);
                    }
                }
            }
        }

        elm.setComment(comment);
        return elm;
    }

    void parseDomains(XNode node) {
        XNode domainsN = node.element(DOMAINS_NAME);
        if (domainsN == null) {
            LOG.info("pdm.no_domains_node:node={}", node);
            // validateFail(newValidateError("pdm.err_missing_domains_node").param("node", node));
            return;
        }

        for (XNode domainN : domainsN.getChildren()) {
            String name = domainN.getTagNameWithoutNs();
            if (name.equals(PHYSICAL_DOMAIN_NAME)) {
                OrmDomainModel domain = new OrmDomainModel();
                PdmElement elm = this.parseElement(domainN);
                String code = elm.getCode();
                if (code.startsWith("@"))
                    code = code.substring(1);
                domain.setName(code);
                String displayName = elm.getName();
                if (displayName.startsWith("@"))
                    displayName = displayName.substring(1);

                domain.setDisplayName(displayName);
                domain.setExtProps(elm.getExtProps());
                domain.setStdDomain(elm.getTag());

                SQLDataType dataType = parseDataType(domainN);
                if (dataType != null) {
                    StdSqlType sqlType = dialect.getNativeType(dataType.getName()).getStdSqlType();
                    domain.setStdSqlType(sqlType);
                    domain.setStdDataType(sqlType.getStdDataType());
                    if (dataType.getPrecision() >= 0)
                        domain.setPrecision(dataType.getPrecision());
                    if (dataType.getScale() >= 0)
                        domain.setScale(dataType.getScale());
                }

                // java数据类型可能与SQL数据类型不一致。例如在数据库中为INTEGER类型，而在java中统一映射为string类型
                if (domain.getStdDomain() != null) {
                    IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(domain.getStdDomain());
                    if (handler != null) {
                        domain.setStdDataType(handler.getGenericType(false, null).getStdDataType());
                    }
                }
                domains.put(elm.getId(), domain);
                domainsByName.put(domain.getName(), domain);
            }
        }
    }

    SQLDataType parseDataType(XNode node) {
        String typeName = node.elementText(DATATYPE_NAME);
        if (typeName == null) {
            return null;
        }

        Integer scale = ConvertHelper.toInt(node.childValue(PRECISION_NAME));
        Integer precision = ConvertHelper.toInt(node.childValue(LENGTH_NAME));
        int pos = typeName.indexOf('(');
        if (pos > 0) {
            if (!typeName.endsWith(")")) {
                throw new NopException(ERR_ORM_INVALID_DATA_TYPE).param(ARG_DATA_TYPE, typeName);
            }
            int pos2 = typeName.indexOf(',', pos + 1);
            if (pos2 > 0) {
                precision = ConvertHelper.toInt(typeName.substring(pos + 1, pos2).trim());
                scale = ConvertHelper.toInt(typeName.substring(pos2 + 1, typeName.length() - 1).trim());
            } else {
                precision = ConvertHelper.toInt(typeName.substring(pos + 1, typeName.length() - 1).trim());
            }
            typeName = typeName.substring(0, pos);
        }
        if (scale == null)
            scale = -1;
        if (precision == null)
            precision = -1;

        SqlDataTypeModel dataType = dialect.getNativeType(typeName);
        if (precision < 0 && dataType.getPrecision() != null)
            precision = dataType.getPrecision();
        return new SQLDataType(dataType.getName(), precision, scale);
    }

    void parsePackages(XNode node, OrmPackageModel parent) {
        XNode pkgsN = node.element(PACKAGES_NAME);
        // 允许空模型的存在
        if (pkgsN == null)
            return;

        for (XNode pkgN : pkgsN.getChildren()) {
            if (!pkgN.getTagNameWithoutNs().equals(PACKAGE_NAME)) {
                continue;
            }
            parsePackage(pkgN, parent);
        }
    }

    void parsePackage(XNode node, OrmPackageModel parent) {
        OrmPackageModel pkg = new OrmPackageModel();
        PdmElement elm = this.parseElement(node);
        if (parent != null && !StringHelper.isEmpty(parent.getName())) {
            pkg.setName(parent.getName() + "." + elm.getCode());
        } else {
            pkg.setName(elm.getCode());
        }
        pkg.setDisplayName(pkg.getName());
        pkg.setExtProps(elm.getExtProps());

        parseTables(node, pkg);
        parseViews(node, pkg);
        packages.put(elm.getId(), pkg);

        this.parsePackages(node, pkg);
    }

    void parseAllReferences(XNode node) {
        parseReferences(node);
        parseViewReferences(node);

        XNode pkgsN = node.element(PACKAGES_NAME);
        // 允许空模型的存在
        if (pkgsN == null)
            return;

        for (XNode pkgN : pkgsN.getChildren()) {
            parseReferences(pkgN);
            parseViewReferences(pkgN);
            parseAllReferences(pkgN);
        }
    }

    void parseTables(XNode node, OrmPackageModel pkg) {
        XNode tablesN = node.element(TABLES_NAME);
        if (tablesN == null)
            return;

        for (XNode tableN : tablesN.getChildren()) {
            if (tableN.getTagNameWithoutNs().equals(TABLE_NAME)) {
                parseTable(tableN, pkg);
            } else if (tableN.getTagNameWithoutNs().equals(SHORTCUT_NAME)) {
                this.parseShortcut(tableN, PdmElementType.TABLE);
            }
        }
    }

    void parseViews(XNode node, OrmPackageModel pkg) {
        XNode viewsN = node.element(VIEWS_NAME);
        if (viewsN == null)
            return;

        for (XNode viewN : viewsN.getChildren()) {
            if (viewN.getTagNameWithoutNs().equals(VIEW_NAME)) {
                parseView(viewN, pkg);
            } else if (viewN.getTagNameWithoutNs().equals(SHORTCUT_NAME)) {
                this.parseShortcut(viewN, PdmElementType.VIEW);
            }
        }
    }

    void parseTable(XNode node, OrmPackageModel pkg) {
        OrmEntityModel table = new OrmEntityModel();
        table.setLocation(node.getLocation());
        PdmElement elm = parseElement(node);

        tables.put(elm.getId(), table);

        tablesByCode.put(elm.getCode(), table);

        table.setNotGenCode(elm.isNotGenCode());
        parseTableName(table, elm, pkg);

        if (elm.containsTag(STEREOTYPE_WF)) {
            table.setUseWorkflow(true);
        }
        if (elm.containsTag(STEREOTYPE_REVISION)) {
            table.setUseRevision(true);
        }

        parseDimension(node, table);
        parseColumns(node, table);
        parseIndexes(node, table);
        parseKeys(node, table);
        initPkColumns(table);
    }

    private void initPkColumns(OrmEntityModel table) {
        List<OrmColumnModel> cols = new ArrayList<>();
        for (OrmColumnModel col : table.getColumns()) {
            if (col.isPrimary())
                cols.add(col);
        }
        table.setPkColumns(cols);
    }

    private void parseTableName(OrmEntityModel table, PdmElement elm, OrmPackageModel pkg) {
        String className;
        int pos = elm.getName().indexOf('|');
        if (pos < 0) {
            table.setDisplayName(elm.getName());
            table.setTableName(elm.getCode().toLowerCase());
            className = StringHelper.camelCase(table.getTableName(), true);
        } else {
            table.setDisplayName(elm.getName().substring(0, pos));
            className = elm.getName().substring(pos + 1).trim();
        }
        if (pkg == null) {
            className = "app.entity." + className;
        } else {
            className = pkg.getName() + '.' + className;
        }
        LOG.trace("pdm.parse_table:{}", table);
        table.setName(className);
    }

    void parseDimension(XNode node, OrmEntityModel table) { //NOPMD
        String dimensionalType = node.elementText(DIMENSIONAL_TYPE_NAME);
        if (DIMENSIONAL_TYPE_FACT.equals(dimensionalType)) {
            table.setDimensionalType(PdmDimensionalType.fact.name());
        } else if (DIMENSIONAL_TYPE_DIMENSION.equals(dimensionalType)) {
            table.setDimensionalType(PdmDimensionalType.dimension.name());
        }
    }

    void parseColumns(XNode node, OrmEntityModel table) {
        boolean isView = table.isReadonly();

        XNode columnsN = node.element(COLUMNS_NAME); // isView ?
        // node.element(VIEW_COLUMN_COLUMNS_NAME)
        // :
        // node.element(COLUMNS_NAME);
        if (columnsN == null && isView)
            columnsN = node.element(VIEW_COLUMN_COLUMNS_NAME);
        if (columnsN == null) {
            return;
        }

        List<OrmColumnModel> cols = new ArrayList<>(columnsN.getChildCount());

        boolean insertable = !isView;
        boolean updatable = !isView;

        int nextId = 1;
        for (XNode columnN : columnsN.getChildren()) {
            OrmColumnModel col = new OrmColumnModel();
            col.setLocation(columnN.getLocation());
            col.setPropId(nextId++);
            col.setInsertable(insertable);
            col.setUpdatable(updatable);

            PdmElement elm = parseElement(columnN);
            columns.put(elm.getId(), col);

            col.setExtProps(elm.getExtProps());
            col.setCode(elm.getCode().toUpperCase());
            col.setDisplayName(elm.getName());
            col.setComment(elm.getComment());
            col.setTagSet(elm.getTagSet());

            parseColumnTag(col, table);

            try {
                // String format = columnN.elementText(FORMAT_NAME);
                String computedExpression = columnN.elementText(COMPUTED_EXPRESSION);
                // col.setFormat(format);
                col.setSqlText(computedExpression);

                String mandatory = columnN.elementText(MANDATORY_NAME);
                if (mandatory == null)
                    mandatory = columnN.elementText(COLUMN_MANDATORY_NAME);
                if ("1".equals(mandatory)) {
                    col.setMandatory(true);
                }

                SQLDataType dataType = parseDataType(columnN);
                if (dataType != null) {
                    StdSqlType sqlType = dialect.getNativeType(dataType.getName()).getStdSqlType();
                    col.setStdSqlType(sqlType);
                    col.setStdDataType(sqlType.getStdDataType());
                    if (dataType.getPrecision() >= 0)
                        col.setPrecision(dataType.getPrecision());
                    if (dataType.getScale() >= 0)
                        col.setScale(dataType.getScale());
                }

                OrmDomainModel domain = getColumnDomain(columnN);
                if (domain != null) {
                    col.setDomain(domain.getName());
                    col.setStdDomain(domain.getStdDomain());
                    // 强制以domain设置的数据类型为准
                    col.setStdSqlType(domain.getStdSqlType());
                    col.setStdDataType(domain.getStdDataType());
                }
            } catch (NopException e) {
                // e.param("colCode", col.getCode()).param("tableCode", table.getCode());
                e.addXplStack("parse_col:" + table.getTableName() + "." + col.getCode());
                throw e;
            }

            cols.add(col);
        }

        table.setColumns(cols);
    }

    void parseColumnTag(OrmColumnModel col, OrmEntityModel table) {
        if (table.isReadonly()) {
            if (col.containsTag(STEREOTYPE_PK)) {
                col.setPrimary(true);
            }
        }

        if (col.containsTag(STEREOTYPE_LAZY))
            col.setLazy(true);

        String name = col.getDisplayName();
        String javaName;
        int pos = name.indexOf('|');
        if (pos >= 0) {
            String displayName = name.substring(0, pos).intern();
            col.setDisplayName(displayName);
            javaName = name.substring(pos + 1).trim();
            if (!StringHelper.isValidSimpleVarName(javaName))
                throw new NopException(OrmModelErrors.ERR_ORM_MODEL_INVALID_PROP_NAME).source(col)
                        .param(OrmModelErrors.ARG_ENTITY_NAME, table.getName()).param(ARG_PROP_NAME, javaName);
        } else {
            col.setDisplayName(name.intern());
            javaName = codeToPropName(col.getCode());
        }
        col.setName(javaName);

        if (col.containsTag(STEREOTYPE_LABEL)) {
            if (table.getLabelProp() != null) {
                throw new NopException(ERR_ORM_MODEL_MULTIPLE_LABEL_PROP).source(col)
                        .param(ARG_ENTITY_NAME, table.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_OTHER_PROP_NAME, table.getLabelProp());
            }
            table.setLabelProp(col.getName());
        }

        if (col.containsTag(STEREOTYPE_STATE)) {
            if (table.getStateProp() != null) {
                throw new NopException(ERR_ORM_MODEL_MULTIPLE_STATE_PROP).source(col)
                        .param(ARG_ENTITY_NAME, table.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_OTHER_PROP_NAME, table.getLabelProp());
            }
            table.setStateProp(col.getName());
        }
        if (col.containsTag(STEREOTYPE_VERSION)) {
            if (table.getVersionProp() != null) {
                throw new NopException(ERR_ORM_MODEL_MULTIPLE_VERSION_PROP).source(col)
                        .param(ARG_ENTITY_NAME, table.getName()).param(ARG_PROP_NAME, col.getName())
                        .param(ARG_OTHER_PROP_NAME, table.getLabelProp());
            }
            table.setVersionProp(col.getName());
        }
    }

    OrmDomainModel getColumnDomain(XNode node) {
        String domainId = null;
        XNode domainN = node.element(DOMAIN_NAME);
        if (domainN != null) {
            XNode dN = domainN.element(PHYSICAL_DOMAIN_NAME);
            if (dN != null) {
                domainId = dN.attrText(REF_NAME);
            }
        } else {
            XNode viewColumns = node.element(VIEW_COLUMN_COLUMNS_NAME);
            if (viewColumns == null)
                return null;
            // 如果存在多个列，必然无法找到唯一对应的domain
            if (viewColumns.getChildCount() != 1)
                return null;
            XNode colN = viewColumns.element(COLUMN_NAME);
            if (colN == null)
                return null;
            String refId = colN.attrText(REF_NAME);
            if (refId == null)
                return null;
            OrmColumnModel col = this.columns.get(refId);
            if (col == null) {
                return null;
            }
            if (col.getDomain() == null)
                return null;
            return domainsByName.get(col.getDomain());
        }
        if (domainId == null)
            return null;
        OrmDomainModel domain = domains.get(domainId);
        return domain;
    }

    void parseShortcut(XNode node, PdmElementType targetType) {
        PdmShortcut shortcut = new PdmShortcut();
        PdmElement elm = parseElement(node);
        shortcut.setCode(elm.getCode());
        shortcut.setElementType(targetType);
        shortcuts.put(elm.getId(), shortcut);
    }

    void parseView(XNode node, OrmPackageModel pkg) {
        OrmEntityModel table = new OrmEntityModel();
        table.setLocation(node.getLocation());
        table.setReadonly(true);
        PdmElement elm = parseElement(node);

        tables.put(elm.getId(), table);

        tablesByCode.put(elm.getCode(), table);

        parseTableName(table, elm, pkg);

        parseColumns(node, table);
        initPkColumns(table);

        String query = node.elementText(VIEW_SQLQUERY_NAME);
        table.setSqlText(query);
    }

    void parseReferences(XNode node) {
        XNode refsN = node.element(REFERENCES_NAME);
        // 可以不存在引用
        if (refsN == null)
            return;

        List<RefInfo> refInfos = new ArrayList<>();
        for (XNode refN : refsN.getChildren()) {
            if (!refN.getTagNameWithoutNs().equals(REFERENCE_NAME)) {
                continue;
            }
            RefInfo refInfo = collectRefInfo(refN);
            if (refInfo != null)
                refInfos.add(refInfo);
        }

        refInfos = normalizeRefInfo(refInfos);

        for (RefInfo refInfo : refInfos) {
            this.addRelation(refInfo);
        }
    }

    RefInfo collectRefInfo(XNode node) {
        OrmToOneReferenceModel rel = new OrmToOneReferenceModel();
        rel.setLocation(node.getLocation());
        rel.setQueryable(true);
        PdmElement elm = parseElement(node);
        rel.setExtProps(elm.getExtProps());
        rel.setComment(elm.getComment());
        rel.setTagSet(elm.getTagSet());

        if (elm.getTagSet() == null || elm.getTagSet().isEmpty()) {
            rel.setTagSet(new HashSet<>(Arrays.asList("pub")));
        }

        references.put(elm.getId(), rel);

        // String cardinality = node.elementText(CARDINALITY_NAME);
        // boolean toOne = "0..0".equals(cardinality);

        // String parentRole = node.elementText(PARENT_ROLE_NAME);
        // String childRole = node.elementText(CHILD_ROLE_NAME);

        String deleteConstraint = node.elementText(DELETE_CONSTRAINT_NAME);
        // String updateConstraint = node.elementText(UPDATE_CONSTRAINT_NAME);
        // String implementationType =
        // node.elementText(IMPLEMENTATION_TYPE_NAME);
        String constraintName = node.elementText(FOREIGN_KEY_CONSTAINT_NAME_NAME);

        rel.setConstraint(constraintName);
        rel.setAutoCascadeDelete(PdmModelConstants.DELETE_CONSTRAINT_CASCADE.equals(deleteConstraint));

        XNode firstObject = node.element(OBJECT1_NAME);
        if (firstObject == null) {
            firstObject = node.element(PARENT_TABLE_NAME);
            if (firstObject == null) {
                firstObject = node.element(TABLE_VIEW1_NAME);
            }
            if (firstObject == null) {
                return null;
            }
        }
        XNode secondObject = node.element(OBJECT2_NAME);
        if (secondObject == null) {
            secondObject = node.element(CHILD_TABLE_NAME);
            if (secondObject == null) {
                secondObject = node.element(TABLE_VIEW2_NAME);
            }
            if (secondObject == null) {
                return null;
            }
        }

        OrmEntityModel parentTableInfo = getReferenceTable(firstObject);
        OrmEntityModel childTableInfo = getReferenceTable(secondObject);

        if (parentTableInfo == null) {
            return null;
        }

        if (childTableInfo == null) {
            return null;
        }

        rel.setRefEntityModel(parentTableInfo);
        rel.setOwnerEntityModel(childTableInfo);
        addJoin(rel, node, false);

        RefInfo refInfo = parseRefInfo(rel, node);

        boolean oneToOne = isAllPrimaryCol(rel.getColumns());

        if (refInfo.childDesc.propName == null) {
            refInfo.childDesc.propName = oneToOne ? getTablePropName(parentTableInfo)
                    : getRefColPropName(rel.getColumns().get(0), rel);
        }
        if (refInfo.childDesc.displayName == null) {
            refInfo.childDesc.displayName = oneToOne ? parentTableInfo.getDisplayName()
                    : getRefColDisplayName(rel.getColumns().get(0));
        }

        if (refInfo.parentDesc.propName == null) {
            refInfo.parentDesc.propName = oneToOne ? getTablePropName(childTableInfo)
                    : getChildrenPropName(childTableInfo, oneToOne);
        }
        if (refInfo.parentDesc.displayName == null) {
            refInfo.parentDesc.displayName = childTableInfo.getDisplayName();
            // : childTableInfo.getDisplayName();
        }

        refInfo.relation = rel;
        refInfo.parentTable = parentTableInfo;
        refInfo.childTable = childTableInfo;
        refInfo.oneToOne = oneToOne;
        return refInfo;
    }

    private String getTablePropName(OrmEntityModel entityModel) {
        String tableName = entityModel.getTableName();
        String objName = entityModel.getShortName();
        if (StringHelper.startsWithIgnoreCase(tableName, "t_") && objName.startsWith("T")) {
            objName = objName.substring(1);
        }
        return StringHelper.beanPropName(objName);
    }

    List<RefInfo> normalizeRefInfo(List<RefInfo> refInfos) {
        if (refInfos.isEmpty())
            return refInfos;

        List<RefInfo> ret = new ArrayList<>(refInfos.size());

        do {
            RefInfo refInfo = refInfos.remove(refInfos.size() - 1);
            ret.add(refInfo);
            String propName = refInfo.childDesc.propName;
            if (propName != null) {
                boolean conflict = false;
                // 如果有多个关联具有相同的主从表，则children属性名修改为 xxx+"By"+parentPropName
                for (int i = 0, n = refInfos.size(); i < n; i++) {
                    RefInfo other = refInfos.get(i);
                    if (other.parentTable == refInfo.parentTable && other.childTable == refInfo.childTable
                            && propName.equals(other.childDesc.propName)) {
                        conflict = true;
                        refInfos.remove(i);
                        ret.add(other);
                        i--;
                        n--;
                        if (other.childDesc.propName != null && !other.childDesc.propNameAssigned) {
                            other.childDesc.propName = other.childDesc.propName + "By"
                                    + StringHelper.capitalize(other.parentDesc.propName);
                        }
                    }
                }
                if (conflict) {
                    if (refInfo.childDesc.propName != null && !refInfo.childDesc.propNameAssigned) {
                        refInfo.childDesc.propName = refInfo.childDesc.propName + "By"
                                + StringHelper.capitalize(refInfo.parentDesc.propName);
                    }
                }

            }
        } while (!refInfos.isEmpty());

        return ret;
    }

    void addRelation(RefInfo refInfo) {
        OrmEntityModel parentTableInfo = refInfo.parentTable;
        OrmEntityModel childTableInfo = refInfo.childTable;
        OrmToOneReferenceModel rel = refInfo.relation;

        // 如果明确指定忽略，或者如果是引用的外部表，或者如果是引用的是基础表且没有明确指定属性名
        boolean ignoreChildren = refInfo.parentDesc.ignore || parentTableInfo.isNotGenCode()
                || (parentTableInfo.containsTag(STEREOTYPE_BASIC) && !refInfo.parentDesc.propNameAssigned);

        rel.setDisplayName(refInfo.childDesc.displayName);
        rel.setName(refInfo.childDesc.propName);
        rel.setRefPropName(ignoreChildren ? null : refInfo.parentDesc.propName);
        rel.setRefEntityName(parentTableInfo.getName());
        rel.setRefDisplayName(refInfo.parentDesc.displayName);

        if (!refInfo.childDesc.ignore) {
            childTableInfo.addRelation(rel);
        }

        if (!ignoreChildren) {
            boolean bOneToOne = refInfo.oneToOne;
            if (!bOneToOne) {
                OrmRefSetModel refSet = new OrmRefSetModel();
                String keyProp = (String) refInfo.parentDesc.getAttr(PdmModelConstants.KEY_PROP_NAME);
                refSet.setKeyProp(keyProp);

                String orderBy = (String) refInfo.parentDesc.getAttr(PdmModelConstants.ORDER_BY_NAME);
                if (orderBy != null) {
                    List<OrderFieldBean> orderByObj = OrderBySqlParser.INSTANCE.parseFromText(null, orderBy);
                    refSet.setSort(orderByObj);
                    rel.setRefSet(refSet);
                }
            }
        }
    }

    boolean isAllPrimaryCol(List<OrmColumnModel> cols) {
        for (OrmColumnModel col : cols) {
            if (!col.isPrimary())
                return false;
        }
        return true;
    }

    String getChildrenPropName(OrmEntityModel childTable, boolean oneToOne) {
        String code = childTable.getTableName();
        if (StringHelper.startsWithIgnoreCase(code, "t_"))
            code = code.substring(2);

        // int pos = code.indexOf('_');
        // if (pos > 0) {
        // code = code.substring(pos + 1);
        // }
        String name = codeToPropName(code);
        if (oneToOne)
            return name;
        return name + "s";
    }

    // 根据外键列名猜测引用属性名
    String getRefColPropName(OrmColumnModel col, OrmReferenceModel rel) {
        if (col == null)
            return null;

        String name = col.getCode();
        if (name.equalsIgnoreCase("id") || name.equalsIgnoreCase("sid")) {
            return getTablePropName(rel.getRefEntityModel());
        }
        String refName;
        if (StringHelper.endsWithIgnoreCase(name, _ID_POSTFIX) && name.length() > _ID_POSTFIX.length()) {
            refName = name.substring(0, name.length() - _ID_POSTFIX.length());
        } else {
            refName = name + "_obj";
        }
        refName = codeToPropName(refName);
        return refName;
    }

    String codeToPropName(String name) {
        name = StringHelper.camelCase(name, false);
        return StringHelper.beanPropName(StringHelper.capitalize(name));
    }

    // 根据外键显示名猜测引用属性的显示名
    String getRefColDisplayName(OrmColumnModel col) {
        if (col == null)
            return null;

        String name = col.getDisplayName();
        if (!name.equalsIgnoreCase("sid")) {
            if (StringHelper.endsWithIgnoreCase(name, ID_POSTFIX) && name.length() > ID_POSTFIX.length()) {
                name = name.substring(0, name.length() - ID_POSTFIX.length());
            }
        }
        return StringHelper.camelCase(name, false);
    }

    static class RefInfo {
        RelationDesc parentDesc;
        RelationDesc childDesc;

        OrmToOneReferenceModel relation;
        OrmEntityModel parentTable;
        OrmEntityModel childTable;
        boolean oneToOne;
    }

    static class RelationDesc {
        boolean ignore;
        String propName;
        String displayName;
        Map<String, Object> attrs;
        boolean propNameAssigned;

        Object getAttr(String name) {
            if (attrs == null)
                return null;
            return attrs.get(name);
        }
    }

    public RelationDesc parseRelationDesc(String str) {
        Map<String, Object> attrs = null;
        boolean ignore = false;
        String propName = null;
        String displayName = null;

        boolean propNameAssigned = false;

        int pos = str.indexOf('{');
        if (pos >= 0) {
            String attrStr = str.substring(pos);
            str = str.substring(0, pos);
            attrs = (Map<String, Object>) JsonTool.parseNonStrict(attrStr);
        }
        if (str.startsWith("~")) {
            ignore = true;
        } else {
            pos = str.indexOf('|');
            if (pos >= 0) {
                displayName = StringHelper.strip(str.substring(pos + 1));
                propName = StringHelper.strip(str.substring(0, pos));
            } else {
                propName = str;
            }
            propName = StringHelper.strip(propName);
            if (propName != null)
                propNameAssigned = true;
        }

        RelationDesc rel = new RelationDesc();
        rel.ignore = ignore;
        rel.attrs = attrs;
        rel.propName = propName;
        rel.displayName = StringHelper.strip(displayName);
        rel.propNameAssigned = propNameAssigned;
        return rel;
    }

    RefInfo parseRefInfo(OrmReferenceModel rel, XNode node) {
        // parentRole为父实体上对应子实体的属性名
        String parentRole = StringHelper.strip(node.elementText(PARENT_ROLE_NAME));
        String childRole = StringHelper.strip(node.elementText(CHILD_ROLE_NAME));

        RefInfo info = new RefInfo();
        if (parentRole != null) {
            info.parentDesc = parseRelationDesc(parentRole);
        } else {
            info.parentDesc = new RelationDesc();
            info.parentDesc.ignore = true;
        }

        if (childRole != null) {
            info.childDesc = parseRelationDesc(childRole);
        } else {
            info.childDesc = new RelationDesc();
        }

        return info;
    }

    OrmEntityModel getReferenceTable(XNode node) {
        String tableId = ConvertHelper.toString(node.elementAttr(TABLE_NAME, REF_NAME), "");
        if (tableId.isEmpty()) {
            OrmEntityModel tableM = getShortcutTable(ConvertHelper.toString(node.elementAttr(SHORTCUT_NAME, REF_NAME)));
            if (tableM != null)
                return tableM;
            tableId = ConvertHelper.toString(node.elementAttr(VIEW_NAME, REF_NAME));
        }
        if (tableId == null)
            return null;
        OrmEntityModel table = tables.get(tableId);
        return table;
    }

    void parseViewReferences(XNode node) {
        XNode refsN = node.element(VIEW_REFERENCES_NAME);
        // 可以不存在引用
        if (refsN == null)
            return;

        for (XNode refN : refsN.getChildren()) {
            if (!refN.getTagNameWithoutNs().equals(VIEW_REFERENCE_NAME)) {
                continue;
            }
            parseViewReference(refN);
        }
    }

    void parseViewReference(XNode node) {

    }

    void parseIndexes(XNode node, OrmEntityModel table) {
        XNode indexes = node.element(INDEXES_NAME);
        if (indexes == null)
            return;

        for (XNode index : indexes.getChildren()) {
            table.addIndex(parseIndex(index, table));
        }
    }

    OrmIndexModel parseIndex(XNode node, OrmEntityModel table) {
        OrmIndexModel index = new OrmIndexModel();
        index.setLocation(node.getLocation());

        PdmElement elm = parseElement(node);
        index.setExtProps(elm.getExtProps());
        index.setComment(elm.getComment());
        index.setName(elm.getCode());
        index.setDisplayName(elm.getName());

        if ("1".equals(node.elementText(UNIQUE_NAME))) {
            index.setUnique(true);
        }

        XNode colsN = node.element(INDEX_COLUMNS_NAME);
        if (colsN == null || colsN.getChildCount() <= 0) {
            return index;
        }

        List<OrmIndexColumnModel> cols = new ArrayList<>(colsN.getChildCount());

        for (XNode indexColN : colsN.getChildren()) {
            String colId = null;
            XNode colN = indexColN.element(COLUMN_NAME);
            if (colN != null) {
                XNode colRefN = colN.element(COLUMN_NAME);
                if (colRefN != null)
                    colId = colRefN.attrText(REF_NAME);
            }
            OrmColumnModel col = null;
            if (colId != null) {
                col = columns.get(colId);
            }
            if (col == null) {
                continue;
            }
            boolean desc = "0".equals(colN.elementText(ASCENDING_NAME));

            OrmIndexColumnModel indexCol = new OrmIndexColumnModel();
            indexCol.setName(col.getName());
            indexCol.setDesc(desc);
            cols.add(indexCol);
        }
        index.setColumns(cols);
        indexes.put(elm.getId(), index);
        return index;
    }

    void parseKeys(XNode node, OrmEntityModel table) {
        String primaryKeyId = null;
        XNode primaryKey = node.element(KEY_PRIMARY_NAME);
        if (primaryKey != null) {
            primaryKeyId = primaryKey.element(KEY_NAME).attrText("Ref");
        }

        XNode keys = node.element(KEYS_NAME);
        if (keys == null)
            return;

        for (XNode key : keys.getChildren()) {
            OrmUniqueKeyModel keyModel = parseKey(key, table, primaryKeyId);
            if (keyModel == null)
                continue;
            table.addUniqueKey(keyModel);
        }
    }

    OrmUniqueKeyModel parseKey(XNode node, OrmEntityModel table, String primaryKeyId) {
        OrmUniqueKeyModel key = new OrmUniqueKeyModel();
        PdmElement elm = parseElement(node);
        key.setExtProps(elm.getExtProps());
        key.setName(elm.getCode());
        key.setDisplayName(elm.getName());
        key.setComment(elm.getComment());

        String constraintName = node.elementText(CONSTRAINT_NAME_NAME);
        key.setConstraint(constraintName);

        uniqueKeys.put(elm.getId(), key);

        boolean primary = primaryKeyId != null && primaryKeyId.equals(elm.getId());

        XNode colsN = node.element(KEY_COLUMNS_NAME);
        if (colsN == null || colsN.getChildCount() <= 0) {
            return key;
        }

        List<String> colNames = new ArrayList<>(colsN.getChildCount());

        for (XNode colN : colsN.getChildren()) {
            String colId = colN.attrText(REF_NAME);
            OrmColumnModel col = null;
            if (colId != null) {
                col = columns.get(colId);
            }
            if (col == null) {
                continue;
            }
            if (primary)
                col.setPrimary(primary);

            colNames.add(col.getName());
        }

        // 忽略主键对应的key
        if (primary)
            return null;

        key.setColumns(colNames);
        return key;
    }

    OrmEntityModel getShortcutTable(String shortcutId) {
        if (shortcutId == null)
            return null;
        PdmShortcut shortcut = shortcuts.get(shortcutId);
        if (shortcut == null)
            return null;
        OrmEntityModel table = tablesByCode.get(shortcut.getCode());
        return table;
    }

    OrmColumnModel getJoinColumn(XNode node, OrmReferenceModel rel) {
        String colId = ConvertHelper.toString(node.elementAttr(COLUMN_NAME, REF_NAME));
        if (colId == null) {
//            String shortcutId = ConvertHelper.toString(node.elementAttr(SHORTCUT_NAME, REF_NAME));
//            if (shortcutId != null) {
//                OrmColumnModel col = getShortcutColumn(shortcutId);
//                if (col != null)
//                    return col;
//            }
//            if (colId == null)
            colId = (String) node.elementAttr(VIEW_COLUMN_NAME, REF_NAME);
        }
        if (colId == null) {
            return null;
        }
        OrmColumnModel col = columns.get(colId);
        if (col == null) {
            return null;
        }
        return col;
    }

    void addJoin(OrmReferenceModel rel, XNode node, boolean view) {
        String JOINS_KEY = view ? VIEW_REFERENCE_JOINS_NAME : JOINS_NAME;
        String JOIN_KEY = view ? VIEW_REFERENCE_JOIN_NAME : REFERENCE_JOIN_NAME;

        XNode joinsN = node.element(JOINS_KEY);
        if (joinsN == null) {
            return;
        }

        List<OrmJoinOnModel> joins = new ArrayList<>(joinsN.getChildCount());

        List<OrmColumnModel> relCols = new ArrayList<>(joinsN.getChildCount());

        for (XNode refJoinN : joinsN.elements(JOIN_KEY)) {
            XNode objectOne = refJoinN.element(OBJECT1_NAME);
            if (objectOne == null)
                objectOne = refJoinN.element(COLUMN1_NAME);
            if (objectOne == null) {
                return;
            }
            XNode objectTwo = refJoinN.element(OBJECT2_NAME);
            if (objectTwo == null)
                objectTwo = refJoinN.element(COLUMN2_NAME);
            if (objectTwo == null) {
                return;
            }

            OrmColumnModel parentColumn = getJoinColumn(objectOne, rel);
            OrmColumnModel childColumn = getJoinColumn(objectTwo, rel);

            if (parentColumn == null || childColumn == null)
                continue;

            OrmJoinOnModel join = new OrmJoinOnModel();
            join.setLeftProp(childColumn.getName());
            join.setRightProp(parentColumn.getName());
            joins.add(join);
            relCols.add(childColumn);
        }
        rel.setJoin(joins);
        rel.setColumns(relCols);
    }
}
