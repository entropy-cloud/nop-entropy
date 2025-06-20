package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysObjTag;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  对象标签: nop_sys_obj_tag
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysObjTag extends DynamicOrmEntity{
    
    /* 对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 1;
    
    /* 对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 2;
    
    /* 标签ID: TAG_ID BIGINT */
    public static final String PROP_NAME_tagId = "tagId";
    public static final int PROP_ID_tagId = 3;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 4;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 5;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 6;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 7;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 8;
    

    private static int _PROP_ID_BOUND = 9;

    
    /* relation: 标签 */
    public static final String PROP_NAME_tag = "tag";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_bizObjId,PROP_NAME_bizObjName,PROP_NAME_tagId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_bizObjId,PROP_ID_bizObjName,PROP_ID_tagId};

    private static final String[] PROP_ID_TO_NAME = new String[9];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_bizObjId] = PROP_NAME_bizObjId;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjId, PROP_ID_bizObjId);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_tagId] = PROP_NAME_tagId;
          PROP_NAME_TO_ID.put(PROP_NAME_tagId, PROP_ID_tagId);
      
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
      
    }

    
    /* 对象ID: BIZ_OBJ_ID */
    private java.lang.String _bizObjId;
    
    /* 对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 标签ID: TAG_ID */
    private java.lang.Long _tagId;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopSysObjTag(){
        // for debug
    }

    protected NopSysObjTag newInstance(){
        NopSysObjTag entity = new NopSysObjTag();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopSysObjTag cloneInstance() {
        NopSysObjTag entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysObjTag";
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
    
        return buildCompositeId(PK_PROP_NAMES,PK_PROP_IDS);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_bizObjId || propId == PROP_ID_bizObjName || propId == PROP_ID_tagId;
          
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
        
            case PROP_ID_bizObjId:
               return getBizObjId();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_tagId:
               return getTagId();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_bizObjId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjId));
               }
               setBizObjId(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_tagId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_tagId));
               }
               setTagId(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_bizObjId:{
               onInitProp(propId);
               this._bizObjId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_tagId:{
               onInitProp(propId);
               this._tagId = (java.lang.Long)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 对象ID: BIZ_OBJ_ID
     */
    public final java.lang.String getBizObjId(){
         onPropGet(PROP_ID_bizObjId);
         return _bizObjId;
    }

    /**
     * 对象ID: BIZ_OBJ_ID
     */
    public final void setBizObjId(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjId,value)){
            this._bizObjId = value;
            internalClearRefs(PROP_ID_bizObjId);
            orm_id();
        }
    }
    
    /**
     * 对象名: BIZ_OBJ_NAME
     */
    public final java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 对象名: BIZ_OBJ_NAME
     */
    public final void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            orm_id();
        }
    }
    
    /**
     * 标签ID: TAG_ID
     */
    public final java.lang.Long getTagId(){
         onPropGet(PROP_ID_tagId);
         return _tagId;
    }

    /**
     * 标签ID: TAG_ID
     */
    public final void setTagId(java.lang.Long value){
        if(onPropSet(PROP_ID_tagId,value)){
            this._tagId = value;
            internalClearRefs(PROP_ID_tagId);
            orm_id();
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
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
     * 标签
     */
    public final io.nop.sys.dao.entity.NopSysTag getTag(){
       return (io.nop.sys.dao.entity.NopSysTag)internalGetRefEntity(PROP_NAME_tag);
    }

    public final void setTag(io.nop.sys.dao.entity.NopSysTag refEntity){
   
           if(refEntity == null){
           
                   this.setTagId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_tag, refEntity,()->{
           
                           this.setTagId(refEntity.getSid());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
