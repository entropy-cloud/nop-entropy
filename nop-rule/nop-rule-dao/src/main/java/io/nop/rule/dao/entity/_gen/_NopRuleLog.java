package io.nop.rule.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.rule.dao.entity.NopRuleLog;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  规则执行日志: nop_rule_log
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopRuleLog extends DynamicOrmEntity{
    
    /* 日志ID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 规则ID: RULE_ID VARCHAR */
    public static final String PROP_NAME_ruleId = "ruleId";
    public static final int PROP_ID_ruleId = 2;
    
    /* 日志级别: LOG_LEVEL INTEGER */
    public static final String PROP_NAME_logLevel = "logLevel";
    public static final int PROP_ID_logLevel = 3;
    
    /* 日志消息: LOG_MSG VARCHAR */
    public static final String PROP_NAME_logMsg = "logMsg";
    public static final int PROP_ID_logMsg = 4;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 5;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 6;
    

    private static int _PROP_ID_BOUND = 7;

    
    /* relation: 规则定义 */
    public static final String PROP_NAME_ruleDefinition = "ruleDefinition";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[7];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_ruleId] = PROP_NAME_ruleId;
          PROP_NAME_TO_ID.put(PROP_NAME_ruleId, PROP_ID_ruleId);
      
          PROP_ID_TO_NAME[PROP_ID_logLevel] = PROP_NAME_logLevel;
          PROP_NAME_TO_ID.put(PROP_NAME_logLevel, PROP_ID_logLevel);
      
          PROP_ID_TO_NAME[PROP_ID_logMsg] = PROP_NAME_logMsg;
          PROP_NAME_TO_ID.put(PROP_NAME_logMsg, PROP_ID_logMsg);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 日志ID: SID */
    private java.lang.String _sid;
    
    /* 规则ID: RULE_ID */
    private java.lang.String _ruleId;
    
    /* 日志级别: LOG_LEVEL */
    private java.lang.Integer _logLevel;
    
    /* 日志消息: LOG_MSG */
    private java.lang.String _logMsg;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopRuleLog(){
    }

    protected NopRuleLog newInstance(){
       return new NopRuleLog();
    }

    @Override
    public NopRuleLog cloneInstance() {
        NopRuleLog entity = newInstance();
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
      return "io.nop.rule.dao.entity.NopRuleLog";
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
        
            case PROP_ID_ruleId:
               return getRuleId();
        
            case PROP_ID_logLevel:
               return getLogLevel();
        
            case PROP_ID_logMsg:
               return getLogMsg();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
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
        
            case PROP_ID_ruleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ruleId));
               }
               setRuleId(typedValue);
               break;
            }
        
            case PROP_ID_logLevel:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_logLevel));
               }
               setLogLevel(typedValue);
               break;
            }
        
            case PROP_ID_logMsg:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logMsg));
               }
               setLogMsg(typedValue);
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
        
            case PROP_ID_ruleId:{
               onInitProp(propId);
               this._ruleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_logLevel:{
               onInitProp(propId);
               this._logLevel = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_logMsg:{
               onInitProp(propId);
               this._logMsg = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 日志ID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 日志ID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 规则ID: RULE_ID
     */
    public java.lang.String getRuleId(){
         onPropGet(PROP_ID_ruleId);
         return _ruleId;
    }

    /**
     * 规则ID: RULE_ID
     */
    public void setRuleId(java.lang.String value){
        if(onPropSet(PROP_ID_ruleId,value)){
            this._ruleId = value;
            internalClearRefs(PROP_ID_ruleId);
            
        }
    }
    
    /**
     * 日志级别: LOG_LEVEL
     */
    public java.lang.Integer getLogLevel(){
         onPropGet(PROP_ID_logLevel);
         return _logLevel;
    }

    /**
     * 日志级别: LOG_LEVEL
     */
    public void setLogLevel(java.lang.Integer value){
        if(onPropSet(PROP_ID_logLevel,value)){
            this._logLevel = value;
            internalClearRefs(PROP_ID_logLevel);
            
        }
    }
    
    /**
     * 日志消息: LOG_MSG
     */
    public java.lang.String getLogMsg(){
         onPropGet(PROP_ID_logMsg);
         return _logMsg;
    }

    /**
     * 日志消息: LOG_MSG
     */
    public void setLogMsg(java.lang.String value){
        if(onPropSet(PROP_ID_logMsg,value)){
            this._logMsg = value;
            internalClearRefs(PROP_ID_logMsg);
            
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
     * 规则定义
     */
    public io.nop.rule.dao.entity.NopRuleDefinition getRuleDefinition(){
       return (io.nop.rule.dao.entity.NopRuleDefinition)internalGetRefEntity(PROP_NAME_ruleDefinition);
    }

    public void setRuleDefinition(io.nop.rule.dao.entity.NopRuleDefinition refEntity){
       if(refEntity == null){
         
         this.setRuleId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_ruleDefinition, refEntity,()->{
             
                    this.setRuleId(refEntity.getRuleId());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
