package io.nop.dyn.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.dyn.dao.entity.NopDynEntityRelation;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体关联: nop_dyn_entity_relation
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynEntityRelation extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 关联名称: RELATION_NAME VARCHAR */
    public static final String PROP_NAME_relationName = "relationName";
    public static final int PROP_ID_relationName = 2;
    
    /* 实体名称1: ENTITY_NAME1 VARCHAR */
    public static final String PROP_NAME_entityName1 = "entityName1";
    public static final int PROP_ID_entityName1 = 3;
    
    /* 实体ID1: ENTITY_ID1 VARCHAR */
    public static final String PROP_NAME_entityId1 = "entityId1";
    public static final int PROP_ID_entityId1 = 4;
    
    /* 实体名称2: ENTITY_NAME2 VARCHAR */
    public static final String PROP_NAME_entityName2 = "entityName2";
    public static final int PROP_ID_entityName2 = 5;
    
    /* 实体ID2: ENTITY_ID2 VARCHAR */
    public static final String PROP_NAME_entityId2 = "entityId2";
    public static final int PROP_ID_entityId2 = 6;
    
    /* 数据版本: VERSION INTEGER */
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

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_relationName] = PROP_NAME_relationName;
          PROP_NAME_TO_ID.put(PROP_NAME_relationName, PROP_ID_relationName);
      
          PROP_ID_TO_NAME[PROP_ID_entityName1] = PROP_NAME_entityName1;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName1, PROP_ID_entityName1);
      
          PROP_ID_TO_NAME[PROP_ID_entityId1] = PROP_NAME_entityId1;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId1, PROP_ID_entityId1);
      
          PROP_ID_TO_NAME[PROP_ID_entityName2] = PROP_NAME_entityName2;
          PROP_NAME_TO_ID.put(PROP_NAME_entityName2, PROP_ID_entityName2);
      
          PROP_ID_TO_NAME[PROP_ID_entityId2] = PROP_NAME_entityId2;
          PROP_NAME_TO_ID.put(PROP_NAME_entityId2, PROP_ID_entityId2);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 关联名称: RELATION_NAME */
    private java.lang.String _relationName;
    
    /* 实体名称1: ENTITY_NAME1 */
    private java.lang.String _entityName1;
    
    /* 实体ID1: ENTITY_ID1 */
    private java.lang.String _entityId1;
    
    /* 实体名称2: ENTITY_NAME2 */
    private java.lang.String _entityName2;
    
    /* 实体ID2: ENTITY_ID2 */
    private java.lang.String _entityId2;
    
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
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopDynEntityRelation(){
        // for debug
    }

    protected NopDynEntityRelation newInstance(){
       return new NopDynEntityRelation();
    }

    @Override
    public NopDynEntityRelation cloneInstance() {
        NopDynEntityRelation entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.dyn.dao.entity.NopDynEntityRelation";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_relationName:
               return getRelationName();
        
            case PROP_ID_entityName1:
               return getEntityName1();
        
            case PROP_ID_entityId1:
               return getEntityId1();
        
            case PROP_ID_entityName2:
               return getEntityName2();
        
            case PROP_ID_entityId2:
               return getEntityId2();
        
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
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_relationName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_relationName));
               }
               setRelationName(typedValue);
               break;
            }
        
            case PROP_ID_entityName1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName1));
               }
               setEntityName1(typedValue);
               break;
            }
        
            case PROP_ID_entityId1:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityId1));
               }
               setEntityId1(typedValue);
               break;
            }
        
            case PROP_ID_entityName2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityName2));
               }
               setEntityName2(typedValue);
               break;
            }
        
            case PROP_ID_entityId2:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityId2));
               }
               setEntityId2(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_relationName:{
               onInitProp(propId);
               this._relationName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityName1:{
               onInitProp(propId);
               this._entityName1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityId1:{
               onInitProp(propId);
               this._entityId1 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityName2:{
               onInitProp(propId);
               this._entityName2 = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityId2:{
               onInitProp(propId);
               this._entityId2 = (java.lang.String)value;
               
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
     * 主键: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 关联名称: RELATION_NAME
     */
    public java.lang.String getRelationName(){
         onPropGet(PROP_ID_relationName);
         return _relationName;
    }

    /**
     * 关联名称: RELATION_NAME
     */
    public void setRelationName(java.lang.String value){
        if(onPropSet(PROP_ID_relationName,value)){
            this._relationName = value;
            internalClearRefs(PROP_ID_relationName);
            
        }
    }
    
    /**
     * 实体名称1: ENTITY_NAME1
     */
    public java.lang.String getEntityName1(){
         onPropGet(PROP_ID_entityName1);
         return _entityName1;
    }

    /**
     * 实体名称1: ENTITY_NAME1
     */
    public void setEntityName1(java.lang.String value){
        if(onPropSet(PROP_ID_entityName1,value)){
            this._entityName1 = value;
            internalClearRefs(PROP_ID_entityName1);
            
        }
    }
    
    /**
     * 实体ID1: ENTITY_ID1
     */
    public java.lang.String getEntityId1(){
         onPropGet(PROP_ID_entityId1);
         return _entityId1;
    }

    /**
     * 实体ID1: ENTITY_ID1
     */
    public void setEntityId1(java.lang.String value){
        if(onPropSet(PROP_ID_entityId1,value)){
            this._entityId1 = value;
            internalClearRefs(PROP_ID_entityId1);
            
        }
    }
    
    /**
     * 实体名称2: ENTITY_NAME2
     */
    public java.lang.String getEntityName2(){
         onPropGet(PROP_ID_entityName2);
         return _entityName2;
    }

    /**
     * 实体名称2: ENTITY_NAME2
     */
    public void setEntityName2(java.lang.String value){
        if(onPropSet(PROP_ID_entityName2,value)){
            this._entityName2 = value;
            internalClearRefs(PROP_ID_entityName2);
            
        }
    }
    
    /**
     * 实体ID2: ENTITY_ID2
     */
    public java.lang.String getEntityId2(){
         onPropGet(PROP_ID_entityId2);
         return _entityId2;
    }

    /**
     * 实体ID2: ENTITY_ID2
     */
    public void setEntityId2(java.lang.String value){
        if(onPropSet(PROP_ID_entityId2,value)){
            this._entityId2 = value;
            internalClearRefs(PROP_ID_entityId2);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
