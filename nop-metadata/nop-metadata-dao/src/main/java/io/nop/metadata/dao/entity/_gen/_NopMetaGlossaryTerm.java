package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  词汇表术语: nop_meta_glossary_term
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaGlossaryTerm extends DynamicOrmEntity{
    
    /* 术语ID: GLOSSARY_TERM_ID VARCHAR */
    public static final String PROP_NAME_glossaryTermId = "glossaryTermId";
    public static final int PROP_ID_glossaryTermId = 1;
    
    /* 词汇表ID: GLOSSARY_ID VARCHAR */
    public static final String PROP_NAME_glossaryId = "glossaryId";
    public static final int PROP_ID_glossaryId = 2;
    
    /* 父术语ID: PARENT_TERM_ID VARCHAR */
    public static final String PROP_NAME_parentTermId = "parentTermId";
    public static final int PROP_ID_parentTermId = 3;
    
    /* 术语名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 4;
    
    /* 全限定名: FULLY_QUALIFIED_NAME VARCHAR */
    public static final String PROP_NAME_fullyQualifiedName = "fullyQualifiedName";
    public static final int PROP_ID_fullyQualifiedName = 5;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 6;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 7;
    
    /* 同义词: SYNONYMS VARCHAR */
    public static final String PROP_NAME_synonyms = "synonyms";
    public static final int PROP_ID_synonyms = 8;
    
    /* 相关术语: RELATED_TERMS VARCHAR */
    public static final String PROP_NAME_relatedTerms = "relatedTerms";
    public static final int PROP_ID_relatedTerms = 9;
    
    /* 引用信息: REFERENCES VARCHAR */
    public static final String PROP_NAME_references = "references";
    public static final int PROP_ID_references = 10;
    
    /* 概念映射: CONCEPT_MAPPINGS VARCHAR */
    public static final String PROP_NAME_conceptMappings = "conceptMappings";
    public static final int PROP_ID_conceptMappings = 11;
    
    /* IRI标识: IRI VARCHAR */
    public static final String PROP_NAME_iri = "iri";
    public static final int PROP_ID_iri = 12;
    
    /* 是否互斥: MUTUALLY_EXCLUSIVE TINYINT */
    public static final String PROP_NAME_mutuallyExclusive = "mutuallyExclusive";
    public static final int PROP_ID_mutuallyExclusive = 13;
    
    /* 标签: TAGS VARCHAR */
    public static final String PROP_NAME_tags = "tags";
    public static final int PROP_ID_tags = 14;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 15;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 16;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 17;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 18;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 19;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 20;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 21;
    

    private static int _PROP_ID_BOUND = 22;

    
    /* relation: 所属词汇表 */
    public static final String PROP_NAME_glossary = "glossary";
    
    /* relation: 父术语 */
    public static final String PROP_NAME_parentTerm = "parentTerm";
    
    /* relation: 子术语集 */
    public static final String PROP_NAME_childTerms = "childTerms";
    
    /* relation:  */
    public static final String PROP_NAME_tagLabels = "tagLabels";
    
    /* component:  */
    public static final String PROP_NAME_synonymsComponent = "synonymsComponent";
    
    /* component:  */
    public static final String PROP_NAME_relatedTermsComponent = "relatedTermsComponent";
    
    /* component:  */
    public static final String PROP_NAME_referencesComponent = "referencesComponent";
    
    /* component:  */
    public static final String PROP_NAME_conceptMappingsComponent = "conceptMappingsComponent";
    
    /* component:  */
    public static final String PROP_NAME_tagsComponent = "tagsComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_glossaryTermId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_glossaryTermId};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_glossaryTermId] = PROP_NAME_glossaryTermId;
          PROP_NAME_TO_ID.put(PROP_NAME_glossaryTermId, PROP_ID_glossaryTermId);
      
          PROP_ID_TO_NAME[PROP_ID_glossaryId] = PROP_NAME_glossaryId;
          PROP_NAME_TO_ID.put(PROP_NAME_glossaryId, PROP_ID_glossaryId);
      
          PROP_ID_TO_NAME[PROP_ID_parentTermId] = PROP_NAME_parentTermId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentTermId, PROP_ID_parentTermId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_fullyQualifiedName] = PROP_NAME_fullyQualifiedName;
          PROP_NAME_TO_ID.put(PROP_NAME_fullyQualifiedName, PROP_ID_fullyQualifiedName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_synonyms] = PROP_NAME_synonyms;
          PROP_NAME_TO_ID.put(PROP_NAME_synonyms, PROP_ID_synonyms);
      
          PROP_ID_TO_NAME[PROP_ID_relatedTerms] = PROP_NAME_relatedTerms;
          PROP_NAME_TO_ID.put(PROP_NAME_relatedTerms, PROP_ID_relatedTerms);
      
          PROP_ID_TO_NAME[PROP_ID_references] = PROP_NAME_references;
          PROP_NAME_TO_ID.put(PROP_NAME_references, PROP_ID_references);
      
          PROP_ID_TO_NAME[PROP_ID_conceptMappings] = PROP_NAME_conceptMappings;
          PROP_NAME_TO_ID.put(PROP_NAME_conceptMappings, PROP_ID_conceptMappings);
      
          PROP_ID_TO_NAME[PROP_ID_iri] = PROP_NAME_iri;
          PROP_NAME_TO_ID.put(PROP_NAME_iri, PROP_ID_iri);
      
          PROP_ID_TO_NAME[PROP_ID_mutuallyExclusive] = PROP_NAME_mutuallyExclusive;
          PROP_NAME_TO_ID.put(PROP_NAME_mutuallyExclusive, PROP_ID_mutuallyExclusive);
      
          PROP_ID_TO_NAME[PROP_ID_tags] = PROP_NAME_tags;
          PROP_NAME_TO_ID.put(PROP_NAME_tags, PROP_ID_tags);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 术语ID: GLOSSARY_TERM_ID */
    private java.lang.String _glossaryTermId;
    
    /* 词汇表ID: GLOSSARY_ID */
    private java.lang.String _glossaryId;
    
    /* 父术语ID: PARENT_TERM_ID */
    private java.lang.String _parentTermId;
    
    /* 术语名: NAME */
    private java.lang.String _name;
    
    /* 全限定名: FULLY_QUALIFIED_NAME */
    private java.lang.String _fullyQualifiedName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 同义词: SYNONYMS */
    private java.lang.String _synonyms;
    
    /* 相关术语: RELATED_TERMS */
    private java.lang.String _relatedTerms;
    
    /* 引用信息: REFERENCES */
    private java.lang.String _references;
    
    /* 概念映射: CONCEPT_MAPPINGS */
    private java.lang.String _conceptMappings;
    
    /* IRI标识: IRI */
    private java.lang.String _iri;
    
    /* 是否互斥: MUTUALLY_EXCLUSIVE */
    private java.lang.Byte _mutuallyExclusive;
    
    /* 标签: TAGS */
    private java.lang.String _tags;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopMetaGlossaryTerm(){
        // for debug
    }

    protected NopMetaGlossaryTerm newInstance(){
        NopMetaGlossaryTerm entity = new NopMetaGlossaryTerm();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaGlossaryTerm cloneInstance() {
        NopMetaGlossaryTerm entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaGlossaryTerm";
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
    
        return buildSimpleId(PROP_ID_glossaryTermId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_glossaryTermId;
          
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
        
            case PROP_ID_glossaryTermId:
               return getGlossaryTermId();
        
            case PROP_ID_glossaryId:
               return getGlossaryId();
        
            case PROP_ID_parentTermId:
               return getParentTermId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_fullyQualifiedName:
               return getFullyQualifiedName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_synonyms:
               return getSynonyms();
        
            case PROP_ID_relatedTerms:
               return getRelatedTerms();
        
            case PROP_ID_references:
               return getReferences();
        
            case PROP_ID_conceptMappings:
               return getConceptMappings();
        
            case PROP_ID_iri:
               return getIri();
        
            case PROP_ID_mutuallyExclusive:
               return getMutuallyExclusive();
        
            case PROP_ID_tags:
               return getTags();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_glossaryTermId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_glossaryTermId));
               }
               setGlossaryTermId(typedValue);
               break;
            }
        
            case PROP_ID_glossaryId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_glossaryId));
               }
               setGlossaryId(typedValue);
               break;
            }
        
            case PROP_ID_parentTermId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentTermId));
               }
               setParentTermId(typedValue);
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
        
            case PROP_ID_fullyQualifiedName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fullyQualifiedName));
               }
               setFullyQualifiedName(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
               break;
            }
        
            case PROP_ID_synonyms:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_synonyms));
               }
               setSynonyms(typedValue);
               break;
            }
        
            case PROP_ID_relatedTerms:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relatedTerms));
               }
               setRelatedTerms(typedValue);
               break;
            }
        
            case PROP_ID_references:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_references));
               }
               setReferences(typedValue);
               break;
            }
        
            case PROP_ID_conceptMappings:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_conceptMappings));
               }
               setConceptMappings(typedValue);
               break;
            }
        
            case PROP_ID_iri:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_iri));
               }
               setIri(typedValue);
               break;
            }
        
            case PROP_ID_mutuallyExclusive:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_mutuallyExclusive));
               }
               setMutuallyExclusive(typedValue);
               break;
            }
        
            case PROP_ID_tags:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tags));
               }
               setTags(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
               break;
            }
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_glossaryTermId:{
               onInitProp(propId);
               this._glossaryTermId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_glossaryId:{
               onInitProp(propId);
               this._glossaryId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentTermId:{
               onInitProp(propId);
               this._parentTermId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fullyQualifiedName:{
               onInitProp(propId);
               this._fullyQualifiedName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_synonyms:{
               onInitProp(propId);
               this._synonyms = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_relatedTerms:{
               onInitProp(propId);
               this._relatedTerms = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_references:{
               onInitProp(propId);
               this._references = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_conceptMappings:{
               onInitProp(propId);
               this._conceptMappings = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_iri:{
               onInitProp(propId);
               this._iri = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mutuallyExclusive:{
               onInitProp(propId);
               this._mutuallyExclusive = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_tags:{
               onInitProp(propId);
               this._tags = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 术语ID: GLOSSARY_TERM_ID
     */
    public final java.lang.String getGlossaryTermId(){
         onPropGet(PROP_ID_glossaryTermId);
         return _glossaryTermId;
    }

    /**
     * 术语ID: GLOSSARY_TERM_ID
     */
    public final void setGlossaryTermId(java.lang.String value){
        if(onPropSet(PROP_ID_glossaryTermId,value)){
            this._glossaryTermId = value;
            internalClearRefs(PROP_ID_glossaryTermId);
            orm_id();
        }
    }
    
    /**
     * 词汇表ID: GLOSSARY_ID
     */
    public final java.lang.String getGlossaryId(){
         onPropGet(PROP_ID_glossaryId);
         return _glossaryId;
    }

    /**
     * 词汇表ID: GLOSSARY_ID
     */
    public final void setGlossaryId(java.lang.String value){
        if(onPropSet(PROP_ID_glossaryId,value)){
            this._glossaryId = value;
            internalClearRefs(PROP_ID_glossaryId);
            
        }
    }
    
    /**
     * 父术语ID: PARENT_TERM_ID
     */
    public final java.lang.String getParentTermId(){
         onPropGet(PROP_ID_parentTermId);
         return _parentTermId;
    }

    /**
     * 父术语ID: PARENT_TERM_ID
     */
    public final void setParentTermId(java.lang.String value){
        if(onPropSet(PROP_ID_parentTermId,value)){
            this._parentTermId = value;
            internalClearRefs(PROP_ID_parentTermId);
            
        }
    }
    
    /**
     * 术语名: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 术语名: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 全限定名: FULLY_QUALIFIED_NAME
     */
    public final java.lang.String getFullyQualifiedName(){
         onPropGet(PROP_ID_fullyQualifiedName);
         return _fullyQualifiedName;
    }

    /**
     * 全限定名: FULLY_QUALIFIED_NAME
     */
    public final void setFullyQualifiedName(java.lang.String value){
        if(onPropSet(PROP_ID_fullyQualifiedName,value)){
            this._fullyQualifiedName = value;
            internalClearRefs(PROP_ID_fullyQualifiedName);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 同义词: SYNONYMS
     */
    public final java.lang.String getSynonyms(){
         onPropGet(PROP_ID_synonyms);
         return _synonyms;
    }

    /**
     * 同义词: SYNONYMS
     */
    public final void setSynonyms(java.lang.String value){
        if(onPropSet(PROP_ID_synonyms,value)){
            this._synonyms = value;
            internalClearRefs(PROP_ID_synonyms);
            
        }
    }
    
    /**
     * 相关术语: RELATED_TERMS
     */
    public final java.lang.String getRelatedTerms(){
         onPropGet(PROP_ID_relatedTerms);
         return _relatedTerms;
    }

    /**
     * 相关术语: RELATED_TERMS
     */
    public final void setRelatedTerms(java.lang.String value){
        if(onPropSet(PROP_ID_relatedTerms,value)){
            this._relatedTerms = value;
            internalClearRefs(PROP_ID_relatedTerms);
            
        }
    }
    
    /**
     * 引用信息: REFERENCES
     */
    public final java.lang.String getReferences(){
         onPropGet(PROP_ID_references);
         return _references;
    }

    /**
     * 引用信息: REFERENCES
     */
    public final void setReferences(java.lang.String value){
        if(onPropSet(PROP_ID_references,value)){
            this._references = value;
            internalClearRefs(PROP_ID_references);
            
        }
    }
    
    /**
     * 概念映射: CONCEPT_MAPPINGS
     */
    public final java.lang.String getConceptMappings(){
         onPropGet(PROP_ID_conceptMappings);
         return _conceptMappings;
    }

    /**
     * 概念映射: CONCEPT_MAPPINGS
     */
    public final void setConceptMappings(java.lang.String value){
        if(onPropSet(PROP_ID_conceptMappings,value)){
            this._conceptMappings = value;
            internalClearRefs(PROP_ID_conceptMappings);
            
        }
    }
    
    /**
     * IRI标识: IRI
     */
    public final java.lang.String getIri(){
         onPropGet(PROP_ID_iri);
         return _iri;
    }

    /**
     * IRI标识: IRI
     */
    public final void setIri(java.lang.String value){
        if(onPropSet(PROP_ID_iri,value)){
            this._iri = value;
            internalClearRefs(PROP_ID_iri);
            
        }
    }
    
    /**
     * 是否互斥: MUTUALLY_EXCLUSIVE
     */
    public final java.lang.Byte getMutuallyExclusive(){
         onPropGet(PROP_ID_mutuallyExclusive);
         return _mutuallyExclusive;
    }

    /**
     * 是否互斥: MUTUALLY_EXCLUSIVE
     */
    public final void setMutuallyExclusive(java.lang.Byte value){
        if(onPropSet(PROP_ID_mutuallyExclusive,value)){
            this._mutuallyExclusive = value;
            internalClearRefs(PROP_ID_mutuallyExclusive);
            
        }
    }
    
    /**
     * 标签: TAGS
     */
    public final java.lang.String getTags(){
         onPropGet(PROP_ID_tags);
         return _tags;
    }

    /**
     * 标签: TAGS
     */
    public final void setTags(java.lang.String value){
        if(onPropSet(PROP_ID_tags,value)){
            this._tags = value;
            internalClearRefs(PROP_ID_tags);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 所属词汇表
     */
    public final io.nop.metadata.dao.entity.NopMetaGlossary getGlossary(){
       return (io.nop.metadata.dao.entity.NopMetaGlossary)internalGetRefEntity(PROP_NAME_glossary);
    }

    public final void setGlossary(io.nop.metadata.dao.entity.NopMetaGlossary refEntity){
   
           if(refEntity == null){
           
                   this.setGlossaryId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_glossary, refEntity,()->{
           
                           this.setGlossaryId(refEntity.getGlossaryId());
                       
           });
           }
       
    }
       
    /**
     * 父术语
     */
    public final io.nop.metadata.dao.entity.NopMetaGlossaryTerm getParentTerm(){
       return (io.nop.metadata.dao.entity.NopMetaGlossaryTerm)internalGetRefEntity(PROP_NAME_parentTerm);
    }

    public final void setParentTerm(io.nop.metadata.dao.entity.NopMetaGlossaryTerm refEntity){
   
           if(refEntity == null){
           
                   this.setParentTermId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentTerm, refEntity,()->{
           
                           this.setParentTermId(refEntity.getGlossaryTermId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaGlossaryTerm> _childTerms = new OrmEntitySet<>(this, PROP_NAME_childTerms,
        io.nop.metadata.dao.entity.NopMetaGlossaryTerm.PROP_NAME_parentTerm, null,io.nop.metadata.dao.entity.NopMetaGlossaryTerm.class);

    /**
     * 子术语集。 refPropName: parentTerm, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaGlossaryTerm> getChildTerms(){
       return _childTerms;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaTagLabel> _tagLabels = new OrmEntitySet<>(this, PROP_NAME_tagLabels,
        io.nop.metadata.dao.entity.NopMetaTagLabel.PROP_NAME_glossaryTerm, null,io.nop.metadata.dao.entity.NopMetaTagLabel.class);

    /**
     * 。 refPropName: glossaryTerm, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaTagLabel> getTagLabels(){
       return _tagLabels;
    }
       
   private io.nop.orm.component.JsonOrmComponent _synonymsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_synonymsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_synonymsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_synonyms);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSynonymsComponent(){
      if(_synonymsComponent == null){
          _synonymsComponent = new io.nop.orm.component.JsonOrmComponent();
          _synonymsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_synonymsComponent);
      }
      return _synonymsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _relatedTermsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_relatedTermsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_relatedTermsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_relatedTerms);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getRelatedTermsComponent(){
      if(_relatedTermsComponent == null){
          _relatedTermsComponent = new io.nop.orm.component.JsonOrmComponent();
          _relatedTermsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_relatedTermsComponent);
      }
      return _relatedTermsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _referencesComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_referencesComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_referencesComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_references);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getReferencesComponent(){
      if(_referencesComponent == null){
          _referencesComponent = new io.nop.orm.component.JsonOrmComponent();
          _referencesComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_referencesComponent);
      }
      return _referencesComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _conceptMappingsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_conceptMappingsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_conceptMappingsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_conceptMappings);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getConceptMappingsComponent(){
      if(_conceptMappingsComponent == null){
          _conceptMappingsComponent = new io.nop.orm.component.JsonOrmComponent();
          _conceptMappingsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_conceptMappingsComponent);
      }
      return _conceptMappingsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _tagsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_tagsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_tagsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_tags);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getTagsComponent(){
      if(_tagsComponent == null){
          _tagsComponent = new io.nop.orm.component.JsonOrmComponent();
          _tagsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_tagsComponent);
      }
      return _tagsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
