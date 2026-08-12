package io.nop.credential.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.credential.dao.entity.NopCredentialUsage;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  凭证使用记录: nop_credential_usage
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCredentialUsage extends DynamicOrmEntity{
    
    /* 使用记录ID: USAGE_ID VARCHAR */
    public static final String PROP_NAME_usageId = "usageId";
    public static final int PROP_ID_usageId = 1;
    
    /* 凭证ID: CREDENTIAL_ID VARCHAR */
    public static final String PROP_NAME_credentialId = "credentialId";
    public static final int PROP_ID_credentialId = 2;
    
    /* 消费者引用: CONSUMER_REF VARCHAR */
    public static final String PROP_NAME_consumerRef = "consumerRef";
    public static final int PROP_ID_consumerRef = 3;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 4;
    

    private static int _PROP_ID_BOUND = 5;

    
    /* relation: 凭证 */
    public static final String PROP_NAME_credential = "credential";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_usageId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_usageId};

    private static final String[] PROP_ID_TO_NAME = new String[5];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_usageId] = PROP_NAME_usageId;
          PROP_NAME_TO_ID.put(PROP_NAME_usageId, PROP_ID_usageId);
      
          PROP_ID_TO_NAME[PROP_ID_credentialId] = PROP_NAME_credentialId;
          PROP_NAME_TO_ID.put(PROP_NAME_credentialId, PROP_ID_credentialId);
      
          PROP_ID_TO_NAME[PROP_ID_consumerRef] = PROP_NAME_consumerRef;
          PROP_NAME_TO_ID.put(PROP_NAME_consumerRef, PROP_ID_consumerRef);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
    }

    
    /* 使用记录ID: USAGE_ID */
    private java.lang.String _usageId;
    
    /* 凭证ID: CREDENTIAL_ID */
    private java.lang.String _credentialId;
    
    /* 消费者引用: CONSUMER_REF */
    private java.lang.String _consumerRef;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    

    public _NopCredentialUsage(){
        // for debug
    }

    protected NopCredentialUsage newInstance(){
        NopCredentialUsage entity = new NopCredentialUsage();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCredentialUsage cloneInstance() {
        NopCredentialUsage entity = newInstance();
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
      return "io.nop.credential.dao.entity.NopCredentialUsage";
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
    
        return buildSimpleId(PROP_ID_usageId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_usageId;
          
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
        
            case PROP_ID_usageId:
               return getUsageId();
        
            case PROP_ID_credentialId:
               return getCredentialId();
        
            case PROP_ID_consumerRef:
               return getConsumerRef();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_usageId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_usageId));
               }
               setUsageId(typedValue);
               break;
            }
        
            case PROP_ID_credentialId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_credentialId));
               }
               setCredentialId(typedValue);
               break;
            }
        
            case PROP_ID_consumerRef:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_consumerRef));
               }
               setConsumerRef(typedValue);
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
        
            case PROP_ID_usageId:{
               onInitProp(propId);
               this._usageId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_credentialId:{
               onInitProp(propId);
               this._credentialId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_consumerRef:{
               onInitProp(propId);
               this._consumerRef = (java.lang.String)value;
               
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
     * 使用记录ID: USAGE_ID
     */
    public final java.lang.String getUsageId(){
         onPropGet(PROP_ID_usageId);
         return _usageId;
    }

    /**
     * 使用记录ID: USAGE_ID
     */
    public final void setUsageId(java.lang.String value){
        if(onPropSet(PROP_ID_usageId,value)){
            this._usageId = value;
            internalClearRefs(PROP_ID_usageId);
            orm_id();
        }
    }
    
    /**
     * 凭证ID: CREDENTIAL_ID
     */
    public final java.lang.String getCredentialId(){
         onPropGet(PROP_ID_credentialId);
         return _credentialId;
    }

    /**
     * 凭证ID: CREDENTIAL_ID
     */
    public final void setCredentialId(java.lang.String value){
        if(onPropSet(PROP_ID_credentialId,value)){
            this._credentialId = value;
            internalClearRefs(PROP_ID_credentialId);
            
        }
    }
    
    /**
     * 消费者引用: CONSUMER_REF
     */
    public final java.lang.String getConsumerRef(){
         onPropGet(PROP_ID_consumerRef);
         return _consumerRef;
    }

    /**
     * 消费者引用: CONSUMER_REF
     */
    public final void setConsumerRef(java.lang.String value){
        if(onPropSet(PROP_ID_consumerRef,value)){
            this._consumerRef = value;
            internalClearRefs(PROP_ID_consumerRef);
            
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
     * 凭证
     */
    public final io.nop.credential.dao.entity.NopCredential getCredential(){
       return (io.nop.credential.dao.entity.NopCredential)internalGetRefEntity(PROP_NAME_credential);
    }

    public final void setCredential(io.nop.credential.dao.entity.NopCredential refEntity){
   
           if(refEntity == null){
           
                   this.setCredentialId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_credential, refEntity,()->{
           
                           this.setCredentialId(refEntity.getCredentialId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
