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

import io.nop.credential.dao.entity.NopCredentialAuth;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  凭证取用授权: nop_credential_auth
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCredentialAuth extends DynamicOrmEntity{
    
    /* 授权ID: AUTH_ID VARCHAR */
    public static final String PROP_NAME_authId = "authId";
    public static final int PROP_ID_authId = 1;
    
    /* 凭证ID: CREDENTIAL_ID VARCHAR */
    public static final String PROP_NAME_credentialId = "credentialId";
    public static final int PROP_ID_credentialId = 2;
    
    /* 角色ID: ROLE_ID VARCHAR */
    public static final String PROP_NAME_roleId = "roleId";
    public static final int PROP_ID_roleId = 3;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 4;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation: 凭证 */
    public static final String PROP_NAME_credential = "credential";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_authId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_authId};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_authId] = PROP_NAME_authId;
          PROP_NAME_TO_ID.put(PROP_NAME_authId, PROP_ID_authId);
      
          PROP_ID_TO_NAME[PROP_ID_credentialId] = PROP_NAME_credentialId;
          PROP_NAME_TO_ID.put(PROP_NAME_credentialId, PROP_ID_credentialId);
      
          PROP_ID_TO_NAME[PROP_ID_roleId] = PROP_NAME_roleId;
          PROP_NAME_TO_ID.put(PROP_NAME_roleId, PROP_ID_roleId);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
    }

    
    /* 授权ID: AUTH_ID */
    private java.lang.String _authId;
    
    /* 凭证ID: CREDENTIAL_ID */
    private java.lang.String _credentialId;
    
    /* 角色ID: ROLE_ID */
    private java.lang.String _roleId;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    

    public _NopCredentialAuth(){
        // for debug
    }

    protected NopCredentialAuth newInstance(){
        NopCredentialAuth entity = new NopCredentialAuth();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCredentialAuth cloneInstance() {
        NopCredentialAuth entity = newInstance();
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
      return "io.nop.credential.dao.entity.NopCredentialAuth";
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
    
        return buildSimpleId(PROP_ID_authId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_authId;
          
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
        
            case PROP_ID_authId:
               return getAuthId();
        
            case PROP_ID_credentialId:
               return getCredentialId();
        
            case PROP_ID_roleId:
               return getRoleId();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_authId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authId));
               }
               setAuthId(typedValue);
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
        
            case PROP_ID_roleId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_roleId));
               }
               setRoleId(typedValue);
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
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_authId:{
               onInitProp(propId);
               this._authId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_credentialId:{
               onInitProp(propId);
               this._credentialId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_roleId:{
               onInitProp(propId);
               this._roleId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 授权ID: AUTH_ID
     */
    public final java.lang.String getAuthId(){
         onPropGet(PROP_ID_authId);
         return _authId;
    }

    /**
     * 授权ID: AUTH_ID
     */
    public final void setAuthId(java.lang.String value){
        if(onPropSet(PROP_ID_authId,value)){
            this._authId = value;
            internalClearRefs(PROP_ID_authId);
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
     * 角色ID: ROLE_ID
     */
    public final java.lang.String getRoleId(){
         onPropGet(PROP_ID_roleId);
         return _roleId;
    }

    /**
     * 角色ID: ROLE_ID
     */
    public final void setRoleId(java.lang.String value){
        if(onPropSet(PROP_ID_roleId,value)){
            this._roleId = value;
            internalClearRefs(PROP_ID_roleId);
            
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
