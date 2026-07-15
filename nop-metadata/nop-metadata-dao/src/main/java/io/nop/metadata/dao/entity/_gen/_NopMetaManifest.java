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

import io.nop.metadata.dao.entity.NopMetaManifest;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  元数据快照: nop_meta_manifest
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaManifest extends DynamicOrmEntity{
    
    /* 快照ID: MANIFEST_ID VARCHAR */
    public static final String PROP_NAME_manifestId = "manifestId";
    public static final int PROP_ID_manifestId = 1;
    
    /* 模块版本ID: META_MODULE_ID VARCHAR */
    public static final String PROP_NAME_metaModuleId = "metaModuleId";
    public static final int PROP_ID_metaModuleId = 2;
    
    /* 快照版本号: MANIFEST_VERSION BIGINT */
    public static final String PROP_NAME_manifestVersion = "manifestVersion";
    public static final int PROP_ID_manifestVersion = 3;
    
    /* 生成时间: GENERATED_AT TIMESTAMP */
    public static final String PROP_NAME_generatedAt = "generatedAt";
    public static final int PROP_ID_generatedAt = 4;
    
    /* 平台版本: NOP_METADATA_VERSION VARCHAR */
    public static final String PROP_NAME_nopMetadataVersion = "nopMetadataVersion";
    public static final int PROP_ID_nopMetadataVersion = 5;
    
    /* 快照内容: CONTENT VARCHAR */
    public static final String PROP_NAME_content = "content";
    public static final int PROP_ID_content = 6;
    
    /* 数据版本: DEL_VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 7;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 9;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 10;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 11;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 12;
    

    private static int _PROP_ID_BOUND = 13;

    
    /* relation: 元数据模块 */
    public static final String PROP_NAME_metaModule = "metaModule";
    
    /* component:  */
    public static final String PROP_NAME_contentComponent = "contentComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_manifestId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_manifestId};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_manifestId] = PROP_NAME_manifestId;
          PROP_NAME_TO_ID.put(PROP_NAME_manifestId, PROP_ID_manifestId);
      
          PROP_ID_TO_NAME[PROP_ID_metaModuleId] = PROP_NAME_metaModuleId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaModuleId, PROP_ID_metaModuleId);
      
          PROP_ID_TO_NAME[PROP_ID_manifestVersion] = PROP_NAME_manifestVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_manifestVersion, PROP_ID_manifestVersion);
      
          PROP_ID_TO_NAME[PROP_ID_generatedAt] = PROP_NAME_generatedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_generatedAt, PROP_ID_generatedAt);
      
          PROP_ID_TO_NAME[PROP_ID_nopMetadataVersion] = PROP_NAME_nopMetadataVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_nopMetadataVersion, PROP_ID_nopMetadataVersion);
      
          PROP_ID_TO_NAME[PROP_ID_content] = PROP_NAME_content;
          PROP_NAME_TO_ID.put(PROP_NAME_content, PROP_ID_content);
      
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

    
    /* 快照ID: MANIFEST_ID */
    private java.lang.String _manifestId;
    
    /* 模块版本ID: META_MODULE_ID */
    private java.lang.String _metaModuleId;
    
    /* 快照版本号: MANIFEST_VERSION */
    private java.lang.Long _manifestVersion;
    
    /* 生成时间: GENERATED_AT */
    private java.sql.Timestamp _generatedAt;
    
    /* 平台版本: NOP_METADATA_VERSION */
    private java.lang.String _nopMetadataVersion;
    
    /* 快照内容: CONTENT */
    private java.lang.String _content;
    
    /* 数据版本: DEL_VERSION */
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
    

    public _NopMetaManifest(){
        // for debug
    }

    protected NopMetaManifest newInstance(){
        NopMetaManifest entity = new NopMetaManifest();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaManifest cloneInstance() {
        NopMetaManifest entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaManifest";
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
    
        return buildSimpleId(PROP_ID_manifestId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_manifestId;
          
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
        
            case PROP_ID_manifestId:
               return getManifestId();
        
            case PROP_ID_metaModuleId:
               return getMetaModuleId();
        
            case PROP_ID_manifestVersion:
               return getManifestVersion();
        
            case PROP_ID_generatedAt:
               return getGeneratedAt();
        
            case PROP_ID_nopMetadataVersion:
               return getNopMetadataVersion();
        
            case PROP_ID_content:
               return getContent();
        
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
        
            case PROP_ID_manifestId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_manifestId));
               }
               setManifestId(typedValue);
               break;
            }
        
            case PROP_ID_metaModuleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaModuleId));
               }
               setMetaModuleId(typedValue);
               break;
            }
        
            case PROP_ID_manifestVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_manifestVersion));
               }
               setManifestVersion(typedValue);
               break;
            }
        
            case PROP_ID_generatedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_generatedAt));
               }
               setGeneratedAt(typedValue);
               break;
            }
        
            case PROP_ID_nopMetadataVersion:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopMetadataVersion));
               }
               setNopMetadataVersion(typedValue);
               break;
            }
        
            case PROP_ID_content:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_content));
               }
               setContent(typedValue);
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
        
            case PROP_ID_manifestId:{
               onInitProp(propId);
               this._manifestId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaModuleId:{
               onInitProp(propId);
               this._metaModuleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_manifestVersion:{
               onInitProp(propId);
               this._manifestVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_generatedAt:{
               onInitProp(propId);
               this._generatedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_nopMetadataVersion:{
               onInitProp(propId);
               this._nopMetadataVersion = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_content:{
               onInitProp(propId);
               this._content = (java.lang.String)value;
               
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
     * 快照ID: MANIFEST_ID
     */
    public final java.lang.String getManifestId(){
         onPropGet(PROP_ID_manifestId);
         return _manifestId;
    }

    /**
     * 快照ID: MANIFEST_ID
     */
    public final void setManifestId(java.lang.String value){
        if(onPropSet(PROP_ID_manifestId,value)){
            this._manifestId = value;
            internalClearRefs(PROP_ID_manifestId);
            orm_id();
        }
    }
    
    /**
     * 模块版本ID: META_MODULE_ID
     */
    public final java.lang.String getMetaModuleId(){
         onPropGet(PROP_ID_metaModuleId);
         return _metaModuleId;
    }

    /**
     * 模块版本ID: META_MODULE_ID
     */
    public final void setMetaModuleId(java.lang.String value){
        if(onPropSet(PROP_ID_metaModuleId,value)){
            this._metaModuleId = value;
            internalClearRefs(PROP_ID_metaModuleId);
            
        }
    }
    
    /**
     * 快照版本号: MANIFEST_VERSION
     */
    public final java.lang.Long getManifestVersion(){
         onPropGet(PROP_ID_manifestVersion);
         return _manifestVersion;
    }

    /**
     * 快照版本号: MANIFEST_VERSION
     */
    public final void setManifestVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_manifestVersion,value)){
            this._manifestVersion = value;
            internalClearRefs(PROP_ID_manifestVersion);
            
        }
    }
    
    /**
     * 生成时间: GENERATED_AT
     */
    public final java.sql.Timestamp getGeneratedAt(){
         onPropGet(PROP_ID_generatedAt);
         return _generatedAt;
    }

    /**
     * 生成时间: GENERATED_AT
     */
    public final void setGeneratedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_generatedAt,value)){
            this._generatedAt = value;
            internalClearRefs(PROP_ID_generatedAt);
            
        }
    }
    
    /**
     * 平台版本: NOP_METADATA_VERSION
     */
    public final java.lang.String getNopMetadataVersion(){
         onPropGet(PROP_ID_nopMetadataVersion);
         return _nopMetadataVersion;
    }

    /**
     * 平台版本: NOP_METADATA_VERSION
     */
    public final void setNopMetadataVersion(java.lang.String value){
        if(onPropSet(PROP_ID_nopMetadataVersion,value)){
            this._nopMetadataVersion = value;
            internalClearRefs(PROP_ID_nopMetadataVersion);
            
        }
    }
    
    /**
     * 快照内容: CONTENT
     */
    public final java.lang.String getContent(){
         onPropGet(PROP_ID_content);
         return _content;
    }

    /**
     * 快照内容: CONTENT
     */
    public final void setContent(java.lang.String value){
        if(onPropSet(PROP_ID_content,value)){
            this._content = value;
            internalClearRefs(PROP_ID_content);
            
        }
    }
    
    /**
     * 数据版本: DEL_VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: DEL_VERSION
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
     * 元数据模块
     */
    public final io.nop.metadata.dao.entity.NopMetaModule getMetaModule(){
       return (io.nop.metadata.dao.entity.NopMetaModule)internalGetRefEntity(PROP_NAME_metaModule);
    }

    public final void setMetaModule(io.nop.metadata.dao.entity.NopMetaModule refEntity){
   
           if(refEntity == null){
           
                   this.setMetaModuleId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaModule, refEntity,()->{
           
                           this.setMetaModuleId(refEntity.getMetaModuleId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _contentComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_contentComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_contentComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_content);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getContentComponent(){
      if(_contentComponent == null){
          _contentComponent = new io.nop.orm.component.JsonOrmComponent();
          _contentComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_contentComponent);
      }
      return _contentComponent;
   }

}
// resume CPD analysis - CPD-ON
