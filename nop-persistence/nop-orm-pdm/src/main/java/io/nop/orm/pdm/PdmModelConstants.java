/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

interface PdmModelConstants {
    // char STD_DOMAIN_SEPARATOR = '@';
    char STD_TYPE_SEPARATOR = '|';

    // char COLLECTION_NAME_SEPARATOR = '@';

    String TARGET_PREFIX = "Target=\"";

    String ID_NAME = "Id";
    String NAME_NAME = "Name";
    String CODE_NAME = "Code";
    String COMMENT_NAME = "Comment";
    String GENERATED_NAME = "Generated";
    String STEREOTYPE_NAME = "Stereotype";

    String DOMAINS_NAME = "Domains";
    String DOMAIN_NAME = "Domain";
    String DATATYPE_NAME = "DataType";
    String LENGTH_NAME = "Length";
    String PRECISION_NAME = "Precision";
    String PHYSICAL_DOMAIN_NAME = "PhysicalDomain";

    String PACKAGES_NAME = "Packages";
    String PACKAGE_NAME = "Package";

    String TABLES_NAME = "Tables";
    String TABLE_NAME = "Table";
    String COLUMNS_NAME = "Columns";
    String COLUMN_NAME = "Column";
    String MANDATORY_NAME = "Mandatory";
    String COLUMN_MANDATORY_NAME = "Column.Mandatory";

    String FORMAT_NAME = "format";
    String COMPUTED_EXPRESSION = "ComputedExpression";

    String SHORTCUT_NAME = "Shortcut";
    String SUB_SHORTCUTS_NAME = "SubShortcuts";

    String DIMENSIONAL_TYPE_NAME = "DimensionalType";
    String DIMENSIONAL_TYPE_FACT = "1";
    String DIMENSIONAL_TYPE_DIMENSION = "2";

    String KEY_NAME = "Key";
    String KEYS_NAME = "Keys";
    String KEY_COLUMNS_NAME = "Key.Columns";
    String KEY_PRIMARY_NAME = "PrimaryKey";

    String REF_NAME = "Ref";

    String ASCENDING_NAME = "Ascending";

    String CLUSTER_NAME = "Cluster";

    String INDEXES_NAME = "Indexes";
    String INDEX_NAME = "Index";
    String UNIQUE_NAME = "Unique";
    String INDEX_COLUMNS_NAME = "IndexColumns";
    String INDEX_COLUMN_NAME = "IndexColumn";

    String VIEWS_NAME = "Views";
    String VIEW_NAME = "View";
    String VIEW_COLUMN_NAME = "ViewColumn";
    String VIEW_SQLQUERY_NAME = "View.SQLQuery";
    String VIEW_COLUMN_COLUMNS_NAME = "ViewColumn.Columns";

    String VIEW_REFERENCES_NAME = "ViewReferences";
    String VIEW_REFERENCE_NAME = "ViewReference";

    String CONSTRAINT_NAME_NAME = "ConstraintName";
    String DELETE_CONSTRAINT_NAME = "DeleteConstraint";
    String UPDATE_CONSTRAINT_NAME = "UpdateConstraint";

    String IMPLEMENTATION_TYPE_NAME = "ImplementationType";

    String DELETE_CONSTRAINT_CASCADE = "2";
    String DELETE_CONSTRAINT_NO_ACTION = "1";

    String UPDATE_CONSTRAINT_CASCADE = "2";
    String UPDATE_CONSTRAINT_NO_ACTION = "1";
    String FOREIGN_KEY_CONSTAINT_NAME_NAME = "ForeignKeyConstraintName";

    String REFERENCES_NAME = "References";
    String REFERENCE_NAME = "Reference";
    String CARDINALITY_NAME = "Cardinality";
    String OBJECT1_NAME = "Object1";
    String OBJECT2_NAME = "Object2";
    String PARENT_KEY_NAME = "ParentKey";
    String REFERENCE_JOIN_NAME = "ReferenceJoin";
    String JOINS_NAME = "Joins";

    String VIEW_REFERENCE_JOINS_NAME = "ViewReference.Joins";
    String VIEW_REFERENCE_JOIN_NAME = "ViewReferenceJoin";

    String PARENT_ROLE_NAME = "ParentRole";
    String CHILD_ROLE_NAME = "ChildRole";

    String ORDER_BY_NAME = "orderBy";
    String KEY_PROP_NAME = "keyProp";
    String PROP_PREFIX_NAME = "propPrefix";

    String CASCADE_DELETE_NAME = "cascadeDelete";

    String PARENT_TABLE_NAME = "ParentTable";
    String CHILD_TABLE_NAME = "ChildTable";

    String TABLE_VIEW1_NAME = "TableView1";
    String TABLE_VIEW2_NAME = "TableView2";

    String COLUMN1_NAME = "Column1";
    String COLUMN2_NAME = "Column2";

    String INTERNAL_ID_POSTFIX = "_id";
    String ID_POSTFIX = "id";

    String STEREOTYPE_BASIC = "basic";
    String STEREOTYPE_WF = "wf";
    String STEREOTYPE_REVISION = "revision";

    String STEREOTYPE_VERSION = "version";
    String STEREOTYPE_STATE = "state";
    String STEREOTYPE_PARENT = "parent";
    String STEREOTYPE_LAYER_CODE = "layerCode";
    String STEREOTYPE_PK = "pk";
    String STEREOTYPE_LAZY = "lazy";
    String STEREOTYPE_LABEL = "label";

    String STD_DOMAIN_UUID = "uuid";
}