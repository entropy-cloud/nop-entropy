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

import io.nop.sys.dao.entity.NopSysClusterLeader;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  集群选举: nop_sys_cluster_leader
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysClusterLeader extends DynamicOrmEntity{
    
    /* 集群ID: CLUSTER_ID VARCHAR */
    public static final String PROP_NAME_clusterId = "clusterId";
    public static final int PROP_ID_clusterId = 1;
    
    /* 主服务器ID: LEADER_ID VARCHAR */
    public static final String PROP_NAME_leaderId = "leaderId";
    public static final int PROP_ID_leaderId = 2;
    
    /* 主服务器地址: LEADER_ADDER VARCHAR */
    public static final String PROP_NAME_leaderAdder = "leaderAdder";
    public static final int PROP_ID_leaderAdder = 3;
    
    /* 选举世代: LEADER_EPOCH BIGINT */
    public static final String PROP_NAME_leaderEpoch = "leaderEpoch";
    public static final int PROP_ID_leaderEpoch = 4;
    
    /* 选举时间: ELECT_TIME TIMESTAMP */
    public static final String PROP_NAME_electTime = "electTime";
    public static final int PROP_ID_electTime = 5;
    
    /* 过期时间: EXPIRE_AT TIMESTAMP */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 6;
    
    /* 应用ID: APP_ID VARCHAR */
    public static final String PROP_NAME_appId = "appId";
    public static final int PROP_ID_appId = 7;
    

    private static int _PROP_ID_BOUND = 8;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_clusterId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_clusterId};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_clusterId] = PROP_NAME_clusterId;
          PROP_NAME_TO_ID.put(PROP_NAME_clusterId, PROP_ID_clusterId);
      
          PROP_ID_TO_NAME[PROP_ID_leaderId] = PROP_NAME_leaderId;
          PROP_NAME_TO_ID.put(PROP_NAME_leaderId, PROP_ID_leaderId);
      
          PROP_ID_TO_NAME[PROP_ID_leaderAdder] = PROP_NAME_leaderAdder;
          PROP_NAME_TO_ID.put(PROP_NAME_leaderAdder, PROP_ID_leaderAdder);
      
          PROP_ID_TO_NAME[PROP_ID_leaderEpoch] = PROP_NAME_leaderEpoch;
          PROP_NAME_TO_ID.put(PROP_NAME_leaderEpoch, PROP_ID_leaderEpoch);
      
          PROP_ID_TO_NAME[PROP_ID_electTime] = PROP_NAME_electTime;
          PROP_NAME_TO_ID.put(PROP_NAME_electTime, PROP_ID_electTime);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_appId] = PROP_NAME_appId;
          PROP_NAME_TO_ID.put(PROP_NAME_appId, PROP_ID_appId);
      
    }

    
    /* 集群ID: CLUSTER_ID */
    private java.lang.String _clusterId;
    
    /* 主服务器ID: LEADER_ID */
    private java.lang.String _leaderId;
    
    /* 主服务器地址: LEADER_ADDER */
    private java.lang.String _leaderAdder;
    
    /* 选举世代: LEADER_EPOCH */
    private java.lang.Long _leaderEpoch;
    
    /* 选举时间: ELECT_TIME */
    private java.sql.Timestamp _electTime;
    
    /* 过期时间: EXPIRE_AT */
    private java.sql.Timestamp _expireAt;
    
    /* 应用ID: APP_ID */
    private java.lang.String _appId;
    

    public _NopSysClusterLeader(){
        // for debug
    }

    protected NopSysClusterLeader newInstance(){
       return new NopSysClusterLeader();
    }

    @Override
    public NopSysClusterLeader cloneInstance() {
        NopSysClusterLeader entity = newInstance();
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
      return "io.nop.sys.dao.entity.NopSysClusterLeader";
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
    
        return buildSimpleId(PROP_ID_clusterId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_clusterId;
          
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
        
            case PROP_ID_clusterId:
               return getClusterId();
        
            case PROP_ID_leaderId:
               return getLeaderId();
        
            case PROP_ID_leaderAdder:
               return getLeaderAdder();
        
            case PROP_ID_leaderEpoch:
               return getLeaderEpoch();
        
            case PROP_ID_electTime:
               return getElectTime();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_appId:
               return getAppId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_clusterId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clusterId));
               }
               setClusterId(typedValue);
               break;
            }
        
            case PROP_ID_leaderId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leaderId));
               }
               setLeaderId(typedValue);
               break;
            }
        
            case PROP_ID_leaderAdder:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_leaderAdder));
               }
               setLeaderAdder(typedValue);
               break;
            }
        
            case PROP_ID_leaderEpoch:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_leaderEpoch));
               }
               setLeaderEpoch(typedValue);
               break;
            }
        
            case PROP_ID_electTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_electTime));
               }
               setElectTime(typedValue);
               break;
            }
        
            case PROP_ID_expireAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_expireAt));
               }
               setExpireAt(typedValue);
               break;
            }
        
            case PROP_ID_appId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appId));
               }
               setAppId(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_clusterId:{
               onInitProp(propId);
               this._clusterId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_leaderId:{
               onInitProp(propId);
               this._leaderId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leaderAdder:{
               onInitProp(propId);
               this._leaderAdder = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_leaderEpoch:{
               onInitProp(propId);
               this._leaderEpoch = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_electTime:{
               onInitProp(propId);
               this._electTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_appId:{
               onInitProp(propId);
               this._appId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 集群ID: CLUSTER_ID
     */
    public java.lang.String getClusterId(){
         onPropGet(PROP_ID_clusterId);
         return _clusterId;
    }

    /**
     * 集群ID: CLUSTER_ID
     */
    public void setClusterId(java.lang.String value){
        if(onPropSet(PROP_ID_clusterId,value)){
            this._clusterId = value;
            internalClearRefs(PROP_ID_clusterId);
            orm_id();
        }
    }
    
    /**
     * 主服务器ID: LEADER_ID
     */
    public java.lang.String getLeaderId(){
         onPropGet(PROP_ID_leaderId);
         return _leaderId;
    }

    /**
     * 主服务器ID: LEADER_ID
     */
    public void setLeaderId(java.lang.String value){
        if(onPropSet(PROP_ID_leaderId,value)){
            this._leaderId = value;
            internalClearRefs(PROP_ID_leaderId);
            
        }
    }
    
    /**
     * 主服务器地址: LEADER_ADDER
     */
    public java.lang.String getLeaderAdder(){
         onPropGet(PROP_ID_leaderAdder);
         return _leaderAdder;
    }

    /**
     * 主服务器地址: LEADER_ADDER
     */
    public void setLeaderAdder(java.lang.String value){
        if(onPropSet(PROP_ID_leaderAdder,value)){
            this._leaderAdder = value;
            internalClearRefs(PROP_ID_leaderAdder);
            
        }
    }
    
    /**
     * 选举世代: LEADER_EPOCH
     */
    public java.lang.Long getLeaderEpoch(){
         onPropGet(PROP_ID_leaderEpoch);
         return _leaderEpoch;
    }

    /**
     * 选举世代: LEADER_EPOCH
     */
    public void setLeaderEpoch(java.lang.Long value){
        if(onPropSet(PROP_ID_leaderEpoch,value)){
            this._leaderEpoch = value;
            internalClearRefs(PROP_ID_leaderEpoch);
            
        }
    }
    
    /**
     * 选举时间: ELECT_TIME
     */
    public java.sql.Timestamp getElectTime(){
         onPropGet(PROP_ID_electTime);
         return _electTime;
    }

    /**
     * 选举时间: ELECT_TIME
     */
    public void setElectTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_electTime,value)){
            this._electTime = value;
            internalClearRefs(PROP_ID_electTime);
            
        }
    }
    
    /**
     * 过期时间: EXPIRE_AT
     */
    public java.sql.Timestamp getExpireAt(){
         onPropGet(PROP_ID_expireAt);
         return _expireAt;
    }

    /**
     * 过期时间: EXPIRE_AT
     */
    public void setExpireAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_expireAt,value)){
            this._expireAt = value;
            internalClearRefs(PROP_ID_expireAt);
            
        }
    }
    
    /**
     * 应用ID: APP_ID
     */
    public java.lang.String getAppId(){
         onPropGet(PROP_ID_appId);
         return _appId;
    }

    /**
     * 应用ID: APP_ID
     */
    public void setAppId(java.lang.String value){
        if(onPropSet(PROP_ID_appId,value)){
            this._appId = value;
            internalClearRefs(PROP_ID_appId);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
