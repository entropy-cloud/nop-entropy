package io.nop.oauth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.oauth.dao.entity.NopOauthAuthorizationConsent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Oauth许可: nop_oauth_authorization_consent
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopOauthAuthorizationConsent extends DynamicOrmEntity{
    
    /* 注册客户端ID: REGISTERED_CLIENT_ID VARCHAR */
    public static final String PROP_NAME_registeredClientId = "registeredClientId";
    public static final int PROP_ID_registeredClientId = 1;
    
    /* 客户端名称: PRINCIPAL_NAME VARCHAR */
    public static final String PROP_NAME_principalName = "principalName";
    public static final int PROP_ID_principalName = 2;
    
    /* 扩展属性: AUTHORITIES VARCHAR */
    public static final String PROP_NAME_authorities = "authorities";
    public static final int PROP_ID_authorities = 3;
    

    private static int _PROP_ID_BOUND = 4;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_registeredClientId,PROP_NAME_principalName);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_registeredClientId,PROP_ID_principalName};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_registeredClientId] = PROP_NAME_registeredClientId;
          PROP_NAME_TO_ID.put(PROP_NAME_registeredClientId, PROP_ID_registeredClientId);
      
          PROP_ID_TO_NAME[PROP_ID_principalName] = PROP_NAME_principalName;
          PROP_NAME_TO_ID.put(PROP_NAME_principalName, PROP_ID_principalName);
      
          PROP_ID_TO_NAME[PROP_ID_authorities] = PROP_NAME_authorities;
          PROP_NAME_TO_ID.put(PROP_NAME_authorities, PROP_ID_authorities);
      
    }

    
    /* 注册客户端ID: REGISTERED_CLIENT_ID */
    private java.lang.String _registeredClientId;
    
    /* 客户端名称: PRINCIPAL_NAME */
    private java.lang.String _principalName;
    
    /* 扩展属性: AUTHORITIES */
    private java.lang.String _authorities;
    

    public _NopOauthAuthorizationConsent(){
        // for debug
    }

    protected NopOauthAuthorizationConsent newInstance(){
        NopOauthAuthorizationConsent entity = new NopOauthAuthorizationConsent();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopOauthAuthorizationConsent cloneInstance() {
        NopOauthAuthorizationConsent entity = newInstance();
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
      return "io.nop.oauth.dao.entity.NopOauthAuthorizationConsent";
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
        
            return propId == PROP_ID_registeredClientId || propId == PROP_ID_principalName;
          
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
        
            case PROP_ID_registeredClientId:
               return getRegisteredClientId();
        
            case PROP_ID_principalName:
               return getPrincipalName();
        
            case PROP_ID_authorities:
               return getAuthorities();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_registeredClientId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_registeredClientId));
               }
               setRegisteredClientId(typedValue);
               break;
            }
        
            case PROP_ID_principalName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_principalName));
               }
               setPrincipalName(typedValue);
               break;
            }
        
            case PROP_ID_authorities:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorities));
               }
               setAuthorities(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_registeredClientId:{
               onInitProp(propId);
               this._registeredClientId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_principalName:{
               onInitProp(propId);
               this._principalName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_authorities:{
               onInitProp(propId);
               this._authorities = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 注册客户端ID: REGISTERED_CLIENT_ID
     */
    public final java.lang.String getRegisteredClientId(){
         onPropGet(PROP_ID_registeredClientId);
         return _registeredClientId;
    }

    /**
     * 注册客户端ID: REGISTERED_CLIENT_ID
     */
    public final void setRegisteredClientId(java.lang.String value){
        if(onPropSet(PROP_ID_registeredClientId,value)){
            this._registeredClientId = value;
            internalClearRefs(PROP_ID_registeredClientId);
            orm_id();
        }
    }
    
    /**
     * 客户端名称: PRINCIPAL_NAME
     */
    public final java.lang.String getPrincipalName(){
         onPropGet(PROP_ID_principalName);
         return _principalName;
    }

    /**
     * 客户端名称: PRINCIPAL_NAME
     */
    public final void setPrincipalName(java.lang.String value){
        if(onPropSet(PROP_ID_principalName,value)){
            this._principalName = value;
            internalClearRefs(PROP_ID_principalName);
            orm_id();
        }
    }
    
    /**
     * 扩展属性: AUTHORITIES
     */
    public final java.lang.String getAuthorities(){
         onPropGet(PROP_ID_authorities);
         return _authorities;
    }

    /**
     * 扩展属性: AUTHORITIES
     */
    public final void setAuthorities(java.lang.String value){
        if(onPropSet(PROP_ID_authorities,value)){
            this._authorities = value;
            internalClearRefs(PROP_ID_authorities);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
