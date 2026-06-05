package io.nop.code.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.code.dao.entity.NopCodeSymbol;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  符号: nop_code_symbol
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCodeSymbol extends DynamicOrmEntity{
    
    /* 符号ID: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 索引ID: INDEX_ID VARCHAR */
    public static final String PROP_NAME_indexId = "indexId";
    public static final int PROP_ID_indexId = 2;
    
    /* 文件ID: FILE_ID VARCHAR */
    public static final String PROP_NAME_fileId = "fileId";
    public static final int PROP_ID_fileId = 3;
    
    /* 符号类型: KIND VARCHAR */
    public static final String PROP_NAME_kind = "kind";
    public static final int PROP_ID_kind = 4;
    
    /* 名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 5;
    
    /* 全限定名: QUALIFIED_NAME VARCHAR */
    public static final String PROP_NAME_qualifiedName = "qualifiedName";
    public static final int PROP_ID_qualifiedName = 6;
    
    /* 访问修饰符: ACCESS_MODIFIER VARCHAR */
    public static final String PROP_NAME_accessModifier = "accessModifier";
    public static final int PROP_ID_accessModifier = 7;
    
    /* 已废弃: DEPRECATED BOOLEAN */
    public static final String PROP_NAME_deprecated = "deprecated";
    public static final int PROP_ID_deprecated = 8;
    
    /* 文档注释: DOCUMENTATION VARCHAR */
    public static final String PROP_NAME_documentation = "documentation";
    public static final int PROP_ID_documentation = 9;
    
    /* 起始行: LINE INTEGER */
    public static final String PROP_NAME_line = "line";
    public static final int PROP_ID_line = 10;
    
    /* 起始列: COLUMN INTEGER */
    public static final String PROP_NAME_column = "column";
    public static final int PROP_ID_column = 11;
    
    /* 结束行: END_LINE INTEGER */
    public static final String PROP_NAME_endLine = "endLine";
    public static final int PROP_ID_endLine = 12;
    
    /* 结束列: END_COLUMN INTEGER */
    public static final String PROP_NAME_endColumn = "endColumn";
    public static final int PROP_ID_endColumn = 13;
    
    /* 使用次数: USAGE_COUNT INTEGER */
    public static final String PROP_NAME_usageCount = "usageCount";
    public static final int PROP_ID_usageCount = 14;
    
    /* 父符号ID: PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 15;
    
    /* 所属类型ID: DECLARING_SYMBOL_ID VARCHAR */
    public static final String PROP_NAME_declaringSymbolId = "declaringSymbolId";
    public static final int PROP_ID_declaringSymbolId = 16;
    
    /* 父类名: SUPER_CLASS_NAME VARCHAR */
    public static final String PROP_NAME_superClassName = "superClassName";
    public static final int PROP_ID_superClassName = 17;
    
    /* 修饰符: MODIFIERS INTEGER */
    public static final String PROP_NAME_modifiers = "modifiers";
    public static final int PROP_ID_modifiers = 18;
    
    /* 签名: SIGNATURE VARCHAR */
    public static final String PROP_NAME_signature = "signature";
    public static final int PROP_ID_signature = 19;
    
    /* 返回类型: RETURN_TYPE VARCHAR */
    public static final String PROP_NAME_returnType = "returnType";
    public static final int PROP_ID_returnType = 20;
    
    /* 字段类型: FIELD_TYPE VARCHAR */
    public static final String PROP_NAME_fieldType = "fieldType";
    public static final int PROP_ID_fieldType = 21;
    
    /* 扩展数据: EXT_DATA VARCHAR */
    public static final String PROP_NAME_extData = "extData";
    public static final int PROP_ID_extData = 22;
    
    /* 原始返回类型: RAW_RETURN_TYPE VARCHAR */
    public static final String PROP_NAME_rawReturnType = "rawReturnType";
    public static final int PROP_ID_rawReturnType = 23;
    
    /* 原始字段类型: RAW_FIELD_TYPE VARCHAR */
    public static final String PROP_NAME_rawFieldType = "rawFieldType";
    public static final int PROP_ID_rawFieldType = 24;
    
    /* 文件路径(反规范化): FILE_PATH VARCHAR */
    public static final String PROP_NAME_filePath = "filePath";
    public static final int PROP_ID_filePath = 25;
    
    /* 语言(反规范化): LANGUAGE VARCHAR */
    public static final String PROP_NAME_language = "language";
    public static final int PROP_ID_language = 26;
    

    private static int _PROP_ID_BOUND = 27;

    
    /* relation:  */
    public static final String PROP_NAME_index = "index";
    
    /* relation:  */
    public static final String PROP_NAME_file = "file";
    
    /* relation:  */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation:  */
    public static final String PROP_NAME_declaringSymbol = "declaringSymbol";
    
    /* relation: 子符号 */
    public static final String PROP_NAME_children = "children";
    
    /* relation: 成员 */
    public static final String PROP_NAME_members = "members";
    
    /* relation: 引用 */
    public static final String PROP_NAME_usages = "usages";
    
    /* relation: 注解使用 */
    public static final String PROP_NAME_annotations = "annotations";
    
    /* relation: 入口执行流 */
    public static final String PROP_NAME_entryFlows = "entryFlows";
    
    /* relation: 流成员 */
    public static final String PROP_NAME_flowMemberships = "flowMemberships";
    
    /* relation: 被调用 */
    public static final String PROP_NAME_callees = "callees";
    
    /* relation: 调用 */
    public static final String PROP_NAME_callers = "callers";
    
    /* relation: 父类型 */
    public static final String PROP_NAME_superTypes = "superTypes";
    
    /* relation: 子类型 */
    public static final String PROP_NAME_subTypes = "subTypes";
    
    /* relation: 引用 */
    public static final String PROP_NAME_enclosingUsages = "enclosingUsages";
    
    /* relation: 注解使用 */
    public static final String PROP_NAME_annotationUsages = "annotationUsages";
    
    /* component:  */
    public static final String PROP_NAME_extDataComponent = "extDataComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[27];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_indexId] = PROP_NAME_indexId;
          PROP_NAME_TO_ID.put(PROP_NAME_indexId, PROP_ID_indexId);
      
          PROP_ID_TO_NAME[PROP_ID_fileId] = PROP_NAME_fileId;
          PROP_NAME_TO_ID.put(PROP_NAME_fileId, PROP_ID_fileId);
      
          PROP_ID_TO_NAME[PROP_ID_kind] = PROP_NAME_kind;
          PROP_NAME_TO_ID.put(PROP_NAME_kind, PROP_ID_kind);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_qualifiedName] = PROP_NAME_qualifiedName;
          PROP_NAME_TO_ID.put(PROP_NAME_qualifiedName, PROP_ID_qualifiedName);
      
          PROP_ID_TO_NAME[PROP_ID_accessModifier] = PROP_NAME_accessModifier;
          PROP_NAME_TO_ID.put(PROP_NAME_accessModifier, PROP_ID_accessModifier);
      
          PROP_ID_TO_NAME[PROP_ID_deprecated] = PROP_NAME_deprecated;
          PROP_NAME_TO_ID.put(PROP_NAME_deprecated, PROP_ID_deprecated);
      
          PROP_ID_TO_NAME[PROP_ID_documentation] = PROP_NAME_documentation;
          PROP_NAME_TO_ID.put(PROP_NAME_documentation, PROP_ID_documentation);
      
          PROP_ID_TO_NAME[PROP_ID_line] = PROP_NAME_line;
          PROP_NAME_TO_ID.put(PROP_NAME_line, PROP_ID_line);
      
          PROP_ID_TO_NAME[PROP_ID_column] = PROP_NAME_column;
          PROP_NAME_TO_ID.put(PROP_NAME_column, PROP_ID_column);
      
          PROP_ID_TO_NAME[PROP_ID_endLine] = PROP_NAME_endLine;
          PROP_NAME_TO_ID.put(PROP_NAME_endLine, PROP_ID_endLine);
      
          PROP_ID_TO_NAME[PROP_ID_endColumn] = PROP_NAME_endColumn;
          PROP_NAME_TO_ID.put(PROP_NAME_endColumn, PROP_ID_endColumn);
      
          PROP_ID_TO_NAME[PROP_ID_usageCount] = PROP_NAME_usageCount;
          PROP_NAME_TO_ID.put(PROP_NAME_usageCount, PROP_ID_usageCount);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_declaringSymbolId] = PROP_NAME_declaringSymbolId;
          PROP_NAME_TO_ID.put(PROP_NAME_declaringSymbolId, PROP_ID_declaringSymbolId);
      
          PROP_ID_TO_NAME[PROP_ID_superClassName] = PROP_NAME_superClassName;
          PROP_NAME_TO_ID.put(PROP_NAME_superClassName, PROP_ID_superClassName);
      
          PROP_ID_TO_NAME[PROP_ID_modifiers] = PROP_NAME_modifiers;
          PROP_NAME_TO_ID.put(PROP_NAME_modifiers, PROP_ID_modifiers);
      
          PROP_ID_TO_NAME[PROP_ID_signature] = PROP_NAME_signature;
          PROP_NAME_TO_ID.put(PROP_NAME_signature, PROP_ID_signature);
      
          PROP_ID_TO_NAME[PROP_ID_returnType] = PROP_NAME_returnType;
          PROP_NAME_TO_ID.put(PROP_NAME_returnType, PROP_ID_returnType);
      
          PROP_ID_TO_NAME[PROP_ID_fieldType] = PROP_NAME_fieldType;
          PROP_NAME_TO_ID.put(PROP_NAME_fieldType, PROP_ID_fieldType);
      
          PROP_ID_TO_NAME[PROP_ID_extData] = PROP_NAME_extData;
          PROP_NAME_TO_ID.put(PROP_NAME_extData, PROP_ID_extData);
      
          PROP_ID_TO_NAME[PROP_ID_rawReturnType] = PROP_NAME_rawReturnType;
          PROP_NAME_TO_ID.put(PROP_NAME_rawReturnType, PROP_ID_rawReturnType);
      
          PROP_ID_TO_NAME[PROP_ID_rawFieldType] = PROP_NAME_rawFieldType;
          PROP_NAME_TO_ID.put(PROP_NAME_rawFieldType, PROP_ID_rawFieldType);
      
          PROP_ID_TO_NAME[PROP_ID_filePath] = PROP_NAME_filePath;
          PROP_NAME_TO_ID.put(PROP_NAME_filePath, PROP_ID_filePath);
      
          PROP_ID_TO_NAME[PROP_ID_language] = PROP_NAME_language;
          PROP_NAME_TO_ID.put(PROP_NAME_language, PROP_ID_language);
      
    }

    
    /* 符号ID: ID */
    private java.lang.String _id;
    
    /* 索引ID: INDEX_ID */
    private java.lang.String _indexId;
    
    /* 文件ID: FILE_ID */
    private java.lang.String _fileId;
    
    /* 符号类型: KIND */
    private java.lang.String _kind;
    
    /* 名称: NAME */
    private java.lang.String _name;
    
    /* 全限定名: QUALIFIED_NAME */
    private java.lang.String _qualifiedName;
    
    /* 访问修饰符: ACCESS_MODIFIER */
    private java.lang.String _accessModifier;
    
    /* 已废弃: DEPRECATED */
    private java.lang.Boolean _deprecated;
    
    /* 文档注释: DOCUMENTATION */
    private java.lang.String _documentation;
    
    /* 起始行: LINE */
    private java.lang.Integer _line;
    
    /* 起始列: COLUMN */
    private java.lang.Integer _column;
    
    /* 结束行: END_LINE */
    private java.lang.Integer _endLine;
    
    /* 结束列: END_COLUMN */
    private java.lang.Integer _endColumn;
    
    /* 使用次数: USAGE_COUNT */
    private java.lang.Integer _usageCount;
    
    /* 父符号ID: PARENT_ID */
    private java.lang.String _parentId;
    
    /* 所属类型ID: DECLARING_SYMBOL_ID */
    private java.lang.String _declaringSymbolId;
    
    /* 父类名: SUPER_CLASS_NAME */
    private java.lang.String _superClassName;
    
    /* 修饰符: MODIFIERS */
    private java.lang.Integer _modifiers;
    
    /* 签名: SIGNATURE */
    private java.lang.String _signature;
    
    /* 返回类型: RETURN_TYPE */
    private java.lang.String _returnType;
    
    /* 字段类型: FIELD_TYPE */
    private java.lang.String _fieldType;
    
    /* 扩展数据: EXT_DATA */
    private java.lang.String _extData;
    
    /* 原始返回类型: RAW_RETURN_TYPE */
    private java.lang.String _rawReturnType;
    
    /* 原始字段类型: RAW_FIELD_TYPE */
    private java.lang.String _rawFieldType;
    
    /* 文件路径(反规范化): FILE_PATH */
    private java.lang.String _filePath;
    
    /* 语言(反规范化): LANGUAGE */
    private java.lang.String _language;
    

    public _NopCodeSymbol(){
        // for debug
    }

    protected NopCodeSymbol newInstance(){
        NopCodeSymbol entity = new NopCodeSymbol();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCodeSymbol cloneInstance() {
        NopCodeSymbol entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.orm_propValue(propId,value);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.code.dao.entity.NopCodeSymbol";
    }

    @Override
    public int orm_propIdBound(){
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getPropIdBound();
      return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {
    
        return buildSimpleId(PROP_ID_id);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_id;
          
    }

    @Override
    public String orm_propName(int propId) {
        if(propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if(propName == null)
           return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if(propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch(propId){
        
            case PROP_ID_id:
               return getId();
        
            case PROP_ID_indexId:
               return getIndexId();
        
            case PROP_ID_fileId:
               return getFileId();
        
            case PROP_ID_kind:
               return getKind();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_qualifiedName:
               return getQualifiedName();
        
            case PROP_ID_accessModifier:
               return getAccessModifier();
        
            case PROP_ID_deprecated:
               return getDeprecated();
        
            case PROP_ID_documentation:
               return getDocumentation();
        
            case PROP_ID_line:
               return getLine();
        
            case PROP_ID_column:
               return getColumn();
        
            case PROP_ID_endLine:
               return getEndLine();
        
            case PROP_ID_endColumn:
               return getEndColumn();
        
            case PROP_ID_usageCount:
               return getUsageCount();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_declaringSymbolId:
               return getDeclaringSymbolId();
        
            case PROP_ID_superClassName:
               return getSuperClassName();
        
            case PROP_ID_modifiers:
               return getModifiers();
        
            case PROP_ID_signature:
               return getSignature();
        
            case PROP_ID_returnType:
               return getReturnType();
        
            case PROP_ID_fieldType:
               return getFieldType();
        
            case PROP_ID_extData:
               return getExtData();
        
            case PROP_ID_rawReturnType:
               return getRawReturnType();
        
            case PROP_ID_rawFieldType:
               return getRawFieldType();
        
            case PROP_ID_filePath:
               return getFilePath();
        
            case PROP_ID_language:
               return getLanguage();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_indexId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_indexId));
               }
               setIndexId(typedValue);
               break;
            }
        
            case PROP_ID_fileId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileId));
               }
               setFileId(typedValue);
               break;
            }
        
            case PROP_ID_kind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_kind));
               }
               setKind(typedValue);
               break;
            }
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
               break;
            }
        
            case PROP_ID_qualifiedName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualifiedName));
               }
               setQualifiedName(typedValue);
               break;
            }
        
            case PROP_ID_accessModifier:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessModifier));
               }
               setAccessModifier(typedValue);
               break;
            }
        
            case PROP_ID_deprecated:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_deprecated));
               }
               setDeprecated(typedValue);
               break;
            }
        
            case PROP_ID_documentation:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_documentation));
               }
               setDocumentation(typedValue);
               break;
            }
        
            case PROP_ID_line:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_line));
               }
               setLine(typedValue);
               break;
            }
        
            case PROP_ID_column:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_column));
               }
               setColumn(typedValue);
               break;
            }
        
            case PROP_ID_endLine:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_endLine));
               }
               setEndLine(typedValue);
               break;
            }
        
            case PROP_ID_endColumn:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_endColumn));
               }
               setEndColumn(typedValue);
               break;
            }
        
            case PROP_ID_usageCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_usageCount));
               }
               setUsageCount(typedValue);
               break;
            }
        
            case PROP_ID_parentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentId));
               }
               setParentId(typedValue);
               break;
            }
        
            case PROP_ID_declaringSymbolId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_declaringSymbolId));
               }
               setDeclaringSymbolId(typedValue);
               break;
            }
        
            case PROP_ID_superClassName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_superClassName));
               }
               setSuperClassName(typedValue);
               break;
            }
        
            case PROP_ID_modifiers:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_modifiers));
               }
               setModifiers(typedValue);
               break;
            }
        
            case PROP_ID_signature:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_signature));
               }
               setSignature(typedValue);
               break;
            }
        
            case PROP_ID_returnType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_returnType));
               }
               setReturnType(typedValue);
               break;
            }
        
            case PROP_ID_fieldType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fieldType));
               }
               setFieldType(typedValue);
               break;
            }
        
            case PROP_ID_extData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extData));
               }
               setExtData(typedValue);
               break;
            }
        
            case PROP_ID_rawReturnType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rawReturnType));
               }
               setRawReturnType(typedValue);
               break;
            }
        
            case PROP_ID_rawFieldType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_rawFieldType));
               }
               setRawFieldType(typedValue);
               break;
            }
        
            case PROP_ID_filePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_filePath));
               }
               setFilePath(typedValue);
               break;
            }
        
            case PROP_ID_language:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_language));
               }
               setLanguage(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_id:{
               onInitProp(propId);
               this._id = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_indexId:{
               onInitProp(propId);
               this._indexId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileId:{
               onInitProp(propId);
               this._fileId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_kind:{
               onInitProp(propId);
               this._kind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_qualifiedName:{
               onInitProp(propId);
               this._qualifiedName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_accessModifier:{
               onInitProp(propId);
               this._accessModifier = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deprecated:{
               onInitProp(propId);
               this._deprecated = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_documentation:{
               onInitProp(propId);
               this._documentation = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_line:{
               onInitProp(propId);
               this._line = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_column:{
               onInitProp(propId);
               this._column = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_endLine:{
               onInitProp(propId);
               this._endLine = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_endColumn:{
               onInitProp(propId);
               this._endColumn = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_usageCount:{
               onInitProp(propId);
               this._usageCount = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_declaringSymbolId:{
               onInitProp(propId);
               this._declaringSymbolId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_superClassName:{
               onInitProp(propId);
               this._superClassName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_modifiers:{
               onInitProp(propId);
               this._modifiers = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_signature:{
               onInitProp(propId);
               this._signature = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_returnType:{
               onInitProp(propId);
               this._returnType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fieldType:{
               onInitProp(propId);
               this._fieldType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extData:{
               onInitProp(propId);
               this._extData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rawReturnType:{
               onInitProp(propId);
               this._rawReturnType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_rawFieldType:{
               onInitProp(propId);
               this._rawFieldType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_filePath:{
               onInitProp(propId);
               this._filePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_language:{
               onInitProp(propId);
               this._language = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 符号ID: ID
     */
    public final java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * 符号ID: ID
     */
    public final void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 索引ID: INDEX_ID
     */
    public final java.lang.String getIndexId(){
         onPropGet(PROP_ID_indexId);
         return _indexId;
    }

    /**
     * 索引ID: INDEX_ID
     */
    public final void setIndexId(java.lang.String value){
        if(onPropSet(PROP_ID_indexId,value)){
            this._indexId = value;
            internalClearRefs(PROP_ID_indexId);
            
        }
    }
    
    /**
     * 文件ID: FILE_ID
     */
    public final java.lang.String getFileId(){
         onPropGet(PROP_ID_fileId);
         return _fileId;
    }

    /**
     * 文件ID: FILE_ID
     */
    public final void setFileId(java.lang.String value){
        if(onPropSet(PROP_ID_fileId,value)){
            this._fileId = value;
            internalClearRefs(PROP_ID_fileId);
            
        }
    }
    
    /**
     * 符号类型: KIND
     */
    public final java.lang.String getKind(){
         onPropGet(PROP_ID_kind);
         return _kind;
    }

    /**
     * 符号类型: KIND
     */
    public final void setKind(java.lang.String value){
        if(onPropSet(PROP_ID_kind,value)){
            this._kind = value;
            internalClearRefs(PROP_ID_kind);
            
        }
    }
    
    /**
     * 名称: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 名称: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 全限定名: QUALIFIED_NAME
     */
    public final java.lang.String getQualifiedName(){
         onPropGet(PROP_ID_qualifiedName);
         return _qualifiedName;
    }

    /**
     * 全限定名: QUALIFIED_NAME
     */
    public final void setQualifiedName(java.lang.String value){
        if(onPropSet(PROP_ID_qualifiedName,value)){
            this._qualifiedName = value;
            internalClearRefs(PROP_ID_qualifiedName);
            
        }
    }
    
    /**
     * 访问修饰符: ACCESS_MODIFIER
     */
    public final java.lang.String getAccessModifier(){
         onPropGet(PROP_ID_accessModifier);
         return _accessModifier;
    }

    /**
     * 访问修饰符: ACCESS_MODIFIER
     */
    public final void setAccessModifier(java.lang.String value){
        if(onPropSet(PROP_ID_accessModifier,value)){
            this._accessModifier = value;
            internalClearRefs(PROP_ID_accessModifier);
            
        }
    }
    
    /**
     * 已废弃: DEPRECATED
     */
    public final java.lang.Boolean getDeprecated(){
         onPropGet(PROP_ID_deprecated);
         return _deprecated;
    }

    /**
     * 已废弃: DEPRECATED
     */
    public final void setDeprecated(java.lang.Boolean value){
        if(onPropSet(PROP_ID_deprecated,value)){
            this._deprecated = value;
            internalClearRefs(PROP_ID_deprecated);
            
        }
    }
    
    /**
     * 文档注释: DOCUMENTATION
     */
    public final java.lang.String getDocumentation(){
         onPropGet(PROP_ID_documentation);
         return _documentation;
    }

    /**
     * 文档注释: DOCUMENTATION
     */
    public final void setDocumentation(java.lang.String value){
        if(onPropSet(PROP_ID_documentation,value)){
            this._documentation = value;
            internalClearRefs(PROP_ID_documentation);
            
        }
    }
    
    /**
     * 起始行: LINE
     */
    public final java.lang.Integer getLine(){
         onPropGet(PROP_ID_line);
         return _line;
    }

    /**
     * 起始行: LINE
     */
    public final void setLine(java.lang.Integer value){
        if(onPropSet(PROP_ID_line,value)){
            this._line = value;
            internalClearRefs(PROP_ID_line);
            
        }
    }
    
    /**
     * 起始列: COLUMN
     */
    public final java.lang.Integer getColumn(){
         onPropGet(PROP_ID_column);
         return _column;
    }

    /**
     * 起始列: COLUMN
     */
    public final void setColumn(java.lang.Integer value){
        if(onPropSet(PROP_ID_column,value)){
            this._column = value;
            internalClearRefs(PROP_ID_column);
            
        }
    }
    
    /**
     * 结束行: END_LINE
     */
    public final java.lang.Integer getEndLine(){
         onPropGet(PROP_ID_endLine);
         return _endLine;
    }

    /**
     * 结束行: END_LINE
     */
    public final void setEndLine(java.lang.Integer value){
        if(onPropSet(PROP_ID_endLine,value)){
            this._endLine = value;
            internalClearRefs(PROP_ID_endLine);
            
        }
    }
    
    /**
     * 结束列: END_COLUMN
     */
    public final java.lang.Integer getEndColumn(){
         onPropGet(PROP_ID_endColumn);
         return _endColumn;
    }

    /**
     * 结束列: END_COLUMN
     */
    public final void setEndColumn(java.lang.Integer value){
        if(onPropSet(PROP_ID_endColumn,value)){
            this._endColumn = value;
            internalClearRefs(PROP_ID_endColumn);
            
        }
    }
    
    /**
     * 使用次数: USAGE_COUNT
     */
    public final java.lang.Integer getUsageCount(){
         onPropGet(PROP_ID_usageCount);
         return _usageCount;
    }

    /**
     * 使用次数: USAGE_COUNT
     */
    public final void setUsageCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_usageCount,value)){
            this._usageCount = value;
            internalClearRefs(PROP_ID_usageCount);
            
        }
    }
    
    /**
     * 父符号ID: PARENT_ID
     */
    public final java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父符号ID: PARENT_ID
     */
    public final void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 所属类型ID: DECLARING_SYMBOL_ID
     */
    public final java.lang.String getDeclaringSymbolId(){
         onPropGet(PROP_ID_declaringSymbolId);
         return _declaringSymbolId;
    }

    /**
     * 所属类型ID: DECLARING_SYMBOL_ID
     */
    public final void setDeclaringSymbolId(java.lang.String value){
        if(onPropSet(PROP_ID_declaringSymbolId,value)){
            this._declaringSymbolId = value;
            internalClearRefs(PROP_ID_declaringSymbolId);
            
        }
    }
    
    /**
     * 父类名: SUPER_CLASS_NAME
     */
    public final java.lang.String getSuperClassName(){
         onPropGet(PROP_ID_superClassName);
         return _superClassName;
    }

    /**
     * 父类名: SUPER_CLASS_NAME
     */
    public final void setSuperClassName(java.lang.String value){
        if(onPropSet(PROP_ID_superClassName,value)){
            this._superClassName = value;
            internalClearRefs(PROP_ID_superClassName);
            
        }
    }
    
    /**
     * 修饰符: MODIFIERS
     */
    public final java.lang.Integer getModifiers(){
         onPropGet(PROP_ID_modifiers);
         return _modifiers;
    }

    /**
     * 修饰符: MODIFIERS
     */
    public final void setModifiers(java.lang.Integer value){
        if(onPropSet(PROP_ID_modifiers,value)){
            this._modifiers = value;
            internalClearRefs(PROP_ID_modifiers);
            
        }
    }
    
    /**
     * 签名: SIGNATURE
     */
    public final java.lang.String getSignature(){
         onPropGet(PROP_ID_signature);
         return _signature;
    }

    /**
     * 签名: SIGNATURE
     */
    public final void setSignature(java.lang.String value){
        if(onPropSet(PROP_ID_signature,value)){
            this._signature = value;
            internalClearRefs(PROP_ID_signature);
            
        }
    }
    
    /**
     * 返回类型: RETURN_TYPE
     */
    public final java.lang.String getReturnType(){
         onPropGet(PROP_ID_returnType);
         return _returnType;
    }

    /**
     * 返回类型: RETURN_TYPE
     */
    public final void setReturnType(java.lang.String value){
        if(onPropSet(PROP_ID_returnType,value)){
            this._returnType = value;
            internalClearRefs(PROP_ID_returnType);
            
        }
    }
    
    /**
     * 字段类型: FIELD_TYPE
     */
    public final java.lang.String getFieldType(){
         onPropGet(PROP_ID_fieldType);
         return _fieldType;
    }

    /**
     * 字段类型: FIELD_TYPE
     */
    public final void setFieldType(java.lang.String value){
        if(onPropSet(PROP_ID_fieldType,value)){
            this._fieldType = value;
            internalClearRefs(PROP_ID_fieldType);
            
        }
    }
    
    /**
     * 扩展数据: EXT_DATA
     */
    public final java.lang.String getExtData(){
         onPropGet(PROP_ID_extData);
         return _extData;
    }

    /**
     * 扩展数据: EXT_DATA
     */
    public final void setExtData(java.lang.String value){
        if(onPropSet(PROP_ID_extData,value)){
            this._extData = value;
            internalClearRefs(PROP_ID_extData);
            
        }
    }
    
    /**
     * 原始返回类型: RAW_RETURN_TYPE
     */
    public final java.lang.String getRawReturnType(){
         onPropGet(PROP_ID_rawReturnType);
         return _rawReturnType;
    }

    /**
     * 原始返回类型: RAW_RETURN_TYPE
     */
    public final void setRawReturnType(java.lang.String value){
        if(onPropSet(PROP_ID_rawReturnType,value)){
            this._rawReturnType = value;
            internalClearRefs(PROP_ID_rawReturnType);
            
        }
    }
    
    /**
     * 原始字段类型: RAW_FIELD_TYPE
     */
    public final java.lang.String getRawFieldType(){
         onPropGet(PROP_ID_rawFieldType);
         return _rawFieldType;
    }

    /**
     * 原始字段类型: RAW_FIELD_TYPE
     */
    public final void setRawFieldType(java.lang.String value){
        if(onPropSet(PROP_ID_rawFieldType,value)){
            this._rawFieldType = value;
            internalClearRefs(PROP_ID_rawFieldType);
            
        }
    }
    
    /**
     * 文件路径(反规范化): FILE_PATH
     */
    public final java.lang.String getFilePath(){
         onPropGet(PROP_ID_filePath);
         return _filePath;
    }

    /**
     * 文件路径(反规范化): FILE_PATH
     */
    public final void setFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_filePath,value)){
            this._filePath = value;
            internalClearRefs(PROP_ID_filePath);
            
        }
    }
    
    /**
     * 语言(反规范化): LANGUAGE
     */
    public final java.lang.String getLanguage(){
         onPropGet(PROP_ID_language);
         return _language;
    }

    /**
     * 语言(反规范化): LANGUAGE
     */
    public final void setLanguage(java.lang.String value){
        if(onPropSet(PROP_ID_language,value)){
            this._language = value;
            internalClearRefs(PROP_ID_language);
            
        }
    }
    
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeIndex getIndex(){
       return (io.nop.code.dao.entity.NopCodeIndex)internalGetRefEntity(PROP_NAME_index);
    }

    public final void setIndex(io.nop.code.dao.entity.NopCodeIndex refEntity){
   
           if(refEntity == null){
           
                   this.setIndexId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_index, refEntity,()->{
           
                           this.setIndexId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeFile getFile(){
       return (io.nop.code.dao.entity.NopCodeFile)internalGetRefEntity(PROP_NAME_file);
    }

    public final void setFile(io.nop.code.dao.entity.NopCodeFile refEntity){
   
           if(refEntity == null){
           
                   this.setFileId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_file, refEntity,()->{
           
                           this.setFileId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getParent(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_parent);
    }

    public final void setParent(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setParentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
           
                           this.setParentId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 
     */
    public final io.nop.code.dao.entity.NopCodeSymbol getDeclaringSymbol(){
       return (io.nop.code.dao.entity.NopCodeSymbol)internalGetRefEntity(PROP_NAME_declaringSymbol);
    }

    public final void setDeclaringSymbol(io.nop.code.dao.entity.NopCodeSymbol refEntity){
   
           if(refEntity == null){
           
                   this.setDeclaringSymbolId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_declaringSymbol, refEntity,()->{
           
                           this.setDeclaringSymbolId(refEntity.getId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        io.nop.code.dao.entity.NopCodeSymbol.PROP_NAME_parent, null,io.nop.code.dao.entity.NopCodeSymbol.class);

    /**
     * 子符号。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> getChildren(){
       return _children;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> _members = new OrmEntitySet<>(this, PROP_NAME_members,
        io.nop.code.dao.entity.NopCodeSymbol.PROP_NAME_declaringSymbol, null,io.nop.code.dao.entity.NopCodeSymbol.class);

    /**
     * 成员。 refPropName: declaringSymbol, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeSymbol> getMembers(){
       return _members;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> _usages = new OrmEntitySet<>(this, PROP_NAME_usages,
        io.nop.code.dao.entity.NopCodeUsage.PROP_NAME_symbol, null,io.nop.code.dao.entity.NopCodeUsage.class);

    /**
     * 引用。 refPropName: symbol, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> getUsages(){
       return _usages;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> _annotations = new OrmEntitySet<>(this, PROP_NAME_annotations,
        io.nop.code.dao.entity.NopCodeAnnotationUsage.PROP_NAME_annotatedSymbol, null,io.nop.code.dao.entity.NopCodeAnnotationUsage.class);

    /**
     * 注解使用。 refPropName: annotatedSymbol, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> getAnnotations(){
       return _annotations;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeFlow> _entryFlows = new OrmEntitySet<>(this, PROP_NAME_entryFlows,
        io.nop.code.dao.entity.NopCodeFlow.PROP_NAME_entryPoint, null,io.nop.code.dao.entity.NopCodeFlow.class);

    /**
     * 入口执行流。 refPropName: entryPoint, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeFlow> getEntryFlows(){
       return _entryFlows;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeFlowMembership> _flowMemberships = new OrmEntitySet<>(this, PROP_NAME_flowMemberships,
        io.nop.code.dao.entity.NopCodeFlowMembership.PROP_NAME_symbol, null,io.nop.code.dao.entity.NopCodeFlowMembership.class);

    /**
     * 流成员。 refPropName: symbol, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeFlowMembership> getFlowMemberships(){
       return _flowMemberships;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeCall> _callees = new OrmEntitySet<>(this, PROP_NAME_callees,
        io.nop.code.dao.entity.NopCodeCall.PROP_NAME_caller, null,io.nop.code.dao.entity.NopCodeCall.class);

    /**
     * 被调用。 refPropName: caller, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeCall> getCallees(){
       return _callees;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeCall> _callers = new OrmEntitySet<>(this, PROP_NAME_callers,
        io.nop.code.dao.entity.NopCodeCall.PROP_NAME_callee, null,io.nop.code.dao.entity.NopCodeCall.class);

    /**
     * 调用。 refPropName: callee, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeCall> getCallers(){
       return _callers;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> _superTypes = new OrmEntitySet<>(this, PROP_NAME_superTypes,
        io.nop.code.dao.entity.NopCodeInheritance.PROP_NAME_subType, null,io.nop.code.dao.entity.NopCodeInheritance.class);

    /**
     * 父类型。 refPropName: subType, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> getSuperTypes(){
       return _superTypes;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> _subTypes = new OrmEntitySet<>(this, PROP_NAME_subTypes,
        io.nop.code.dao.entity.NopCodeInheritance.PROP_NAME_superType, null,io.nop.code.dao.entity.NopCodeInheritance.class);

    /**
     * 子类型。 refPropName: superType, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeInheritance> getSubTypes(){
       return _subTypes;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> _enclosingUsages = new OrmEntitySet<>(this, PROP_NAME_enclosingUsages,
        io.nop.code.dao.entity.NopCodeUsage.PROP_NAME_enclosingSymbol, null,io.nop.code.dao.entity.NopCodeUsage.class);

    /**
     * 引用。 refPropName: enclosingSymbol, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeUsage> getEnclosingUsages(){
       return _enclosingUsages;
    }
       
    private final OrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> _annotationUsages = new OrmEntitySet<>(this, PROP_NAME_annotationUsages,
        io.nop.code.dao.entity.NopCodeAnnotationUsage.PROP_NAME_annotationType, null,io.nop.code.dao.entity.NopCodeAnnotationUsage.class);

    /**
     * 注解使用。 refPropName: annotationType, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.code.dao.entity.NopCodeAnnotationUsage> getAnnotationUsages(){
       return _annotationUsages;
    }
       
   private io.nop.orm.component.JsonOrmComponent _extDataComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extDataComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extDataComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extData);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtDataComponent(){
      if(_extDataComponent == null){
          _extDataComponent = new io.nop.orm.component.JsonOrmComponent();
          _extDataComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extDataComponent);
      }
      return _extDataComponent;
   }

}
// resume CPD analysis - CPD-ON
