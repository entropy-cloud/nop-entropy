package io.nop.auth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.auth.dao.entity.NopAuthResource;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  菜单资源: nop_auth_resource
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthResource extends DynamicOrmEntity{
    
    /* 资源ID: RESOURCE_ID VARCHAR */
    public static final String PROP_NAME_resourceId = "resourceId";
    public static final int PROP_ID_resourceId = 1;
    
    /* 站点ID: SITE_ID VARCHAR */
    public static final String PROP_NAME_siteId = "siteId";
    public static final int PROP_ID_siteId = 2;
    
    /* 显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 排序: ORDER_NO INTEGER */
    public static final String PROP_NAME_orderNo = "orderNo";
    public static final int PROP_ID_orderNo = 4;
    
    /* 资源类型: RESOURCE_TYPE VARCHAR */
    public static final String PROP_NAME_resourceType = "resourceType";
    public static final int PROP_ID_resourceType = 5;
    
    /* 父资源ID: PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 6;
    
    /* 图标: ICON VARCHAR */
    public static final String PROP_NAME_icon = "icon";
    public static final int PROP_ID_icon = 7;
    
    /* 前端路由: ROUTE_PATH VARCHAR */
    public static final String PROP_NAME_routePath = "routePath";
    public static final int PROP_ID_routePath = 8;
    
    /* 链接: URL VARCHAR */
    public static final String PROP_NAME_url = "url";
    public static final int PROP_ID_url = 9;
    
    /* 组件名: COMPONENT VARCHAR */
    public static final String PROP_NAME_component = "component";
    public static final int PROP_ID_component = 10;
    
    /* 链接目标: TARGET VARCHAR */
    public static final String PROP_NAME_target = "target";
    public static final int PROP_ID_target = 11;
    
    /* 是否隐藏: HIDDEN TINYINT */
    public static final String PROP_NAME_hidden = "hidden";
    public static final int PROP_ID_hidden = 12;
    
    /* 隐藏时保持状态: KEEP_ALIVE TINYINT */
    public static final String PROP_NAME_keepAlive = "keepAlive";
    public static final int PROP_ID_keepAlive = 13;
    
    /* 权限标识: PERMISSIONS VARCHAR */
    public static final String PROP_NAME_permissions = "permissions";
    public static final int PROP_ID_permissions = 14;
    
    /* 不检查权限: NO_AUTH TINYINT */
    public static final String PROP_NAME_noAuth = "noAuth";
    public static final int PROP_ID_noAuth = 15;
    
    /* 依赖资源: DEPENDS VARCHAR */
    public static final String PROP_NAME_depends = "depends";
    public static final int PROP_ID_depends = 16;
    
    /* 是否叶子节点: IS_LEAF TINYINT */
    public static final String PROP_NAME_isLeaf = "isLeaf";
    public static final int PROP_ID_isLeaf = 17;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 18;
    
    /* 自动更新父节点的权限: AUTH_CASCADE_UP TINYINT */
    public static final String PROP_NAME_authCascadeUp = "authCascadeUp";
    public static final int PROP_ID_authCascadeUp = 19;
    
    /* 扩展配置: Meta_CONFIG VARCHAR */
    public static final String PROP_NAME_metaConfig = "metaConfig";
    public static final int PROP_ID_metaConfig = 20;
    
    /* 组件属性: PROPS_CONFIG VARCHAR */
    public static final String PROP_NAME_propsConfig = "propsConfig";
    public static final int PROP_ID_propsConfig = 21;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 22;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 23;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 24;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 25;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 26;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 27;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 28;
    

    private static int _PROP_ID_BOUND = 29;

    
    /* relation: 父资源 */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation: 子系统 */
    public static final String PROP_NAME_site = "site";
    
    /* relation: 子资源 */
    public static final String PROP_NAME_children = "children";
    
    /* relation: 角色映射 */
    public static final String PROP_NAME_roleMappings = "roleMappings";
    
    /* component:  */
    public static final String PROP_NAME_metaConfigComponent = "metaConfigComponent";
    
    /* component:  */
    public static final String PROP_NAME_propsConfigComponent = "propsConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_resourceId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_resourceId};

    private static final String[] PROP_ID_TO_NAME = new String[29];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_resourceId] = PROP_NAME_resourceId;
          PROP_NAME_TO_ID.put(PROP_NAME_resourceId, PROP_ID_resourceId);
      
          PROP_ID_TO_NAME[PROP_ID_siteId] = PROP_NAME_siteId;
          PROP_NAME_TO_ID.put(PROP_NAME_siteId, PROP_ID_siteId);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_orderNo] = PROP_NAME_orderNo;
          PROP_NAME_TO_ID.put(PROP_NAME_orderNo, PROP_ID_orderNo);
      
          PROP_ID_TO_NAME[PROP_ID_resourceType] = PROP_NAME_resourceType;
          PROP_NAME_TO_ID.put(PROP_NAME_resourceType, PROP_ID_resourceType);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_icon] = PROP_NAME_icon;
          PROP_NAME_TO_ID.put(PROP_NAME_icon, PROP_ID_icon);
      
          PROP_ID_TO_NAME[PROP_ID_routePath] = PROP_NAME_routePath;
          PROP_NAME_TO_ID.put(PROP_NAME_routePath, PROP_ID_routePath);
      
          PROP_ID_TO_NAME[PROP_ID_url] = PROP_NAME_url;
          PROP_NAME_TO_ID.put(PROP_NAME_url, PROP_ID_url);
      
          PROP_ID_TO_NAME[PROP_ID_component] = PROP_NAME_component;
          PROP_NAME_TO_ID.put(PROP_NAME_component, PROP_ID_component);
      
          PROP_ID_TO_NAME[PROP_ID_target] = PROP_NAME_target;
          PROP_NAME_TO_ID.put(PROP_NAME_target, PROP_ID_target);
      
          PROP_ID_TO_NAME[PROP_ID_hidden] = PROP_NAME_hidden;
          PROP_NAME_TO_ID.put(PROP_NAME_hidden, PROP_ID_hidden);
      
          PROP_ID_TO_NAME[PROP_ID_keepAlive] = PROP_NAME_keepAlive;
          PROP_NAME_TO_ID.put(PROP_NAME_keepAlive, PROP_ID_keepAlive);
      
          PROP_ID_TO_NAME[PROP_ID_permissions] = PROP_NAME_permissions;
          PROP_NAME_TO_ID.put(PROP_NAME_permissions, PROP_ID_permissions);
      
          PROP_ID_TO_NAME[PROP_ID_noAuth] = PROP_NAME_noAuth;
          PROP_NAME_TO_ID.put(PROP_NAME_noAuth, PROP_ID_noAuth);
      
          PROP_ID_TO_NAME[PROP_ID_depends] = PROP_NAME_depends;
          PROP_NAME_TO_ID.put(PROP_NAME_depends, PROP_ID_depends);
      
          PROP_ID_TO_NAME[PROP_ID_isLeaf] = PROP_NAME_isLeaf;
          PROP_NAME_TO_ID.put(PROP_NAME_isLeaf, PROP_ID_isLeaf);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_authCascadeUp] = PROP_NAME_authCascadeUp;
          PROP_NAME_TO_ID.put(PROP_NAME_authCascadeUp, PROP_ID_authCascadeUp);
      
          PROP_ID_TO_NAME[PROP_ID_metaConfig] = PROP_NAME_metaConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_metaConfig, PROP_ID_metaConfig);
      
          PROP_ID_TO_NAME[PROP_ID_propsConfig] = PROP_NAME_propsConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_propsConfig, PROP_ID_propsConfig);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
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

    
    /* 资源ID: RESOURCE_ID */
    private java.lang.String _resourceId;
    
    /* 站点ID: SITE_ID */
    private java.lang.String _siteId;
    
    /* 显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 排序: ORDER_NO */
    private java.lang.Integer _orderNo;
    
    /* 资源类型: RESOURCE_TYPE */
    private java.lang.String _resourceType;
    
    /* 父资源ID: PARENT_ID */
    private java.lang.String _parentId;
    
    /* 图标: ICON */
    private java.lang.String _icon;
    
    /* 前端路由: ROUTE_PATH */
    private java.lang.String _routePath;
    
    /* 链接: URL */
    private java.lang.String _url;
    
    /* 组件名: COMPONENT */
    private java.lang.String _component;
    
    /* 链接目标: TARGET */
    private java.lang.String _target;
    
    /* 是否隐藏: HIDDEN */
    private java.lang.Byte _hidden;
    
    /* 隐藏时保持状态: KEEP_ALIVE */
    private java.lang.Byte _keepAlive;
    
    /* 权限标识: PERMISSIONS */
    private java.lang.String _permissions;
    
    /* 不检查权限: NO_AUTH */
    private java.lang.Byte _noAuth;
    
    /* 依赖资源: DEPENDS */
    private java.lang.String _depends;
    
    /* 是否叶子节点: IS_LEAF */
    private java.lang.Byte _isLeaf;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 自动更新父节点的权限: AUTH_CASCADE_UP */
    private java.lang.Byte _authCascadeUp;
    
    /* 扩展配置: Meta_CONFIG */
    private java.lang.String _metaConfig;
    
    /* 组件属性: PROPS_CONFIG */
    private java.lang.String _propsConfig;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopAuthResource(){
        // for debug
    }

    protected NopAuthResource newInstance(){
       return new NopAuthResource();
    }

    @Override
    public NopAuthResource cloneInstance() {
        NopAuthResource entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthResource";
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
    
        return buildSimpleId(PROP_ID_resourceId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_resourceId;
          
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
        
            case PROP_ID_resourceId:
               return getResourceId();
        
            case PROP_ID_siteId:
               return getSiteId();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_orderNo:
               return getOrderNo();
        
            case PROP_ID_resourceType:
               return getResourceType();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_icon:
               return getIcon();
        
            case PROP_ID_routePath:
               return getRoutePath();
        
            case PROP_ID_url:
               return getUrl();
        
            case PROP_ID_component:
               return getComponent();
        
            case PROP_ID_target:
               return getTarget();
        
            case PROP_ID_hidden:
               return getHidden();
        
            case PROP_ID_keepAlive:
               return getKeepAlive();
        
            case PROP_ID_permissions:
               return getPermissions();
        
            case PROP_ID_noAuth:
               return getNoAuth();
        
            case PROP_ID_depends:
               return getDepends();
        
            case PROP_ID_isLeaf:
               return getIsLeaf();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_authCascadeUp:
               return getAuthCascadeUp();
        
            case PROP_ID_metaConfig:
               return getMetaConfig();
        
            case PROP_ID_propsConfig:
               return getPropsConfig();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
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
        
            case PROP_ID_resourceId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_resourceId));
               }
               setResourceId(typedValue);
               break;
            }
        
            case PROP_ID_siteId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_siteId));
               }
               setSiteId(typedValue);
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
        
            case PROP_ID_orderNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_orderNo));
               }
               setOrderNo(typedValue);
               break;
            }
        
            case PROP_ID_resourceType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_resourceType));
               }
               setResourceType(typedValue);
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
        
            case PROP_ID_icon:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_icon));
               }
               setIcon(typedValue);
               break;
            }
        
            case PROP_ID_routePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_routePath));
               }
               setRoutePath(typedValue);
               break;
            }
        
            case PROP_ID_url:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_url));
               }
               setUrl(typedValue);
               break;
            }
        
            case PROP_ID_component:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_component));
               }
               setComponent(typedValue);
               break;
            }
        
            case PROP_ID_target:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_target));
               }
               setTarget(typedValue);
               break;
            }
        
            case PROP_ID_hidden:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_hidden));
               }
               setHidden(typedValue);
               break;
            }
        
            case PROP_ID_keepAlive:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_keepAlive));
               }
               setKeepAlive(typedValue);
               break;
            }
        
            case PROP_ID_permissions:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_permissions));
               }
               setPermissions(typedValue);
               break;
            }
        
            case PROP_ID_noAuth:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_noAuth));
               }
               setNoAuth(typedValue);
               break;
            }
        
            case PROP_ID_depends:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_depends));
               }
               setDepends(typedValue);
               break;
            }
        
            case PROP_ID_isLeaf:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isLeaf));
               }
               setIsLeaf(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_authCascadeUp:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_authCascadeUp));
               }
               setAuthCascadeUp(typedValue);
               break;
            }
        
            case PROP_ID_metaConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaConfig));
               }
               setMetaConfig(typedValue);
               break;
            }
        
            case PROP_ID_propsConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_propsConfig));
               }
               setPropsConfig(typedValue);
               break;
            }
        
            case PROP_ID_delFlag:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
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
        
            case PROP_ID_resourceId:{
               onInitProp(propId);
               this._resourceId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_siteId:{
               onInitProp(propId);
               this._siteId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_orderNo:{
               onInitProp(propId);
               this._orderNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_resourceType:{
               onInitProp(propId);
               this._resourceType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_icon:{
               onInitProp(propId);
               this._icon = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_routePath:{
               onInitProp(propId);
               this._routePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_url:{
               onInitProp(propId);
               this._url = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_component:{
               onInitProp(propId);
               this._component = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_target:{
               onInitProp(propId);
               this._target = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_hidden:{
               onInitProp(propId);
               this._hidden = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_keepAlive:{
               onInitProp(propId);
               this._keepAlive = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_permissions:{
               onInitProp(propId);
               this._permissions = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_noAuth:{
               onInitProp(propId);
               this._noAuth = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_depends:{
               onInitProp(propId);
               this._depends = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isLeaf:{
               onInitProp(propId);
               this._isLeaf = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_authCascadeUp:{
               onInitProp(propId);
               this._authCascadeUp = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_metaConfig:{
               onInitProp(propId);
               this._metaConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_propsConfig:{
               onInitProp(propId);
               this._propsConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
     * 资源ID: RESOURCE_ID
     */
    public java.lang.String getResourceId(){
         onPropGet(PROP_ID_resourceId);
         return _resourceId;
    }

    /**
     * 资源ID: RESOURCE_ID
     */
    public void setResourceId(java.lang.String value){
        if(onPropSet(PROP_ID_resourceId,value)){
            this._resourceId = value;
            internalClearRefs(PROP_ID_resourceId);
            orm_id();
        }
    }
    
    /**
     * 站点ID: SITE_ID
     */
    public java.lang.String getSiteId(){
         onPropGet(PROP_ID_siteId);
         return _siteId;
    }

    /**
     * 站点ID: SITE_ID
     */
    public void setSiteId(java.lang.String value){
        if(onPropSet(PROP_ID_siteId,value)){
            this._siteId = value;
            internalClearRefs(PROP_ID_siteId);
            
        }
    }
    
    /**
     * 显示名称: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名称: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 排序: ORDER_NO
     */
    public java.lang.Integer getOrderNo(){
         onPropGet(PROP_ID_orderNo);
         return _orderNo;
    }

    /**
     * 排序: ORDER_NO
     */
    public void setOrderNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_orderNo,value)){
            this._orderNo = value;
            internalClearRefs(PROP_ID_orderNo);
            
        }
    }
    
    /**
     * 资源类型: RESOURCE_TYPE
     */
    public java.lang.String getResourceType(){
         onPropGet(PROP_ID_resourceType);
         return _resourceType;
    }

    /**
     * 资源类型: RESOURCE_TYPE
     */
    public void setResourceType(java.lang.String value){
        if(onPropSet(PROP_ID_resourceType,value)){
            this._resourceType = value;
            internalClearRefs(PROP_ID_resourceType);
            
        }
    }
    
    /**
     * 父资源ID: PARENT_ID
     */
    public java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * 父资源ID: PARENT_ID
     */
    public void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 图标: ICON
     */
    public java.lang.String getIcon(){
         onPropGet(PROP_ID_icon);
         return _icon;
    }

    /**
     * 图标: ICON
     */
    public void setIcon(java.lang.String value){
        if(onPropSet(PROP_ID_icon,value)){
            this._icon = value;
            internalClearRefs(PROP_ID_icon);
            
        }
    }
    
    /**
     * 前端路由: ROUTE_PATH
     */
    public java.lang.String getRoutePath(){
         onPropGet(PROP_ID_routePath);
         return _routePath;
    }

    /**
     * 前端路由: ROUTE_PATH
     */
    public void setRoutePath(java.lang.String value){
        if(onPropSet(PROP_ID_routePath,value)){
            this._routePath = value;
            internalClearRefs(PROP_ID_routePath);
            
        }
    }
    
    /**
     * 链接: URL
     */
    public java.lang.String getUrl(){
         onPropGet(PROP_ID_url);
         return _url;
    }

    /**
     * 链接: URL
     */
    public void setUrl(java.lang.String value){
        if(onPropSet(PROP_ID_url,value)){
            this._url = value;
            internalClearRefs(PROP_ID_url);
            
        }
    }
    
    /**
     * 组件名: COMPONENT
     */
    public java.lang.String getComponent(){
         onPropGet(PROP_ID_component);
         return _component;
    }

    /**
     * 组件名: COMPONENT
     */
    public void setComponent(java.lang.String value){
        if(onPropSet(PROP_ID_component,value)){
            this._component = value;
            internalClearRefs(PROP_ID_component);
            
        }
    }
    
    /**
     * 链接目标: TARGET
     */
    public java.lang.String getTarget(){
         onPropGet(PROP_ID_target);
         return _target;
    }

    /**
     * 链接目标: TARGET
     */
    public void setTarget(java.lang.String value){
        if(onPropSet(PROP_ID_target,value)){
            this._target = value;
            internalClearRefs(PROP_ID_target);
            
        }
    }
    
    /**
     * 是否隐藏: HIDDEN
     */
    public java.lang.Byte getHidden(){
         onPropGet(PROP_ID_hidden);
         return _hidden;
    }

    /**
     * 是否隐藏: HIDDEN
     */
    public void setHidden(java.lang.Byte value){
        if(onPropSet(PROP_ID_hidden,value)){
            this._hidden = value;
            internalClearRefs(PROP_ID_hidden);
            
        }
    }
    
    /**
     * 隐藏时保持状态: KEEP_ALIVE
     */
    public java.lang.Byte getKeepAlive(){
         onPropGet(PROP_ID_keepAlive);
         return _keepAlive;
    }

    /**
     * 隐藏时保持状态: KEEP_ALIVE
     */
    public void setKeepAlive(java.lang.Byte value){
        if(onPropSet(PROP_ID_keepAlive,value)){
            this._keepAlive = value;
            internalClearRefs(PROP_ID_keepAlive);
            
        }
    }
    
    /**
     * 权限标识: PERMISSIONS
     */
    public java.lang.String getPermissions(){
         onPropGet(PROP_ID_permissions);
         return _permissions;
    }

    /**
     * 权限标识: PERMISSIONS
     */
    public void setPermissions(java.lang.String value){
        if(onPropSet(PROP_ID_permissions,value)){
            this._permissions = value;
            internalClearRefs(PROP_ID_permissions);
            
        }
    }
    
    /**
     * 不检查权限: NO_AUTH
     */
    public java.lang.Byte getNoAuth(){
         onPropGet(PROP_ID_noAuth);
         return _noAuth;
    }

    /**
     * 不检查权限: NO_AUTH
     */
    public void setNoAuth(java.lang.Byte value){
        if(onPropSet(PROP_ID_noAuth,value)){
            this._noAuth = value;
            internalClearRefs(PROP_ID_noAuth);
            
        }
    }
    
    /**
     * 依赖资源: DEPENDS
     */
    public java.lang.String getDepends(){
         onPropGet(PROP_ID_depends);
         return _depends;
    }

    /**
     * 依赖资源: DEPENDS
     */
    public void setDepends(java.lang.String value){
        if(onPropSet(PROP_ID_depends,value)){
            this._depends = value;
            internalClearRefs(PROP_ID_depends);
            
        }
    }
    
    /**
     * 是否叶子节点: IS_LEAF
     */
    public java.lang.Byte getIsLeaf(){
         onPropGet(PROP_ID_isLeaf);
         return _isLeaf;
    }

    /**
     * 是否叶子节点: IS_LEAF
     */
    public void setIsLeaf(java.lang.Byte value){
        if(onPropSet(PROP_ID_isLeaf,value)){
            this._isLeaf = value;
            internalClearRefs(PROP_ID_isLeaf);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 自动更新父节点的权限: AUTH_CASCADE_UP
     */
    public java.lang.Byte getAuthCascadeUp(){
         onPropGet(PROP_ID_authCascadeUp);
         return _authCascadeUp;
    }

    /**
     * 自动更新父节点的权限: AUTH_CASCADE_UP
     */
    public void setAuthCascadeUp(java.lang.Byte value){
        if(onPropSet(PROP_ID_authCascadeUp,value)){
            this._authCascadeUp = value;
            internalClearRefs(PROP_ID_authCascadeUp);
            
        }
    }
    
    /**
     * 扩展配置: Meta_CONFIG
     */
    public java.lang.String getMetaConfig(){
         onPropGet(PROP_ID_metaConfig);
         return _metaConfig;
    }

    /**
     * 扩展配置: Meta_CONFIG
     */
    public void setMetaConfig(java.lang.String value){
        if(onPropSet(PROP_ID_metaConfig,value)){
            this._metaConfig = value;
            internalClearRefs(PROP_ID_metaConfig);
            
        }
    }
    
    /**
     * 组件属性: PROPS_CONFIG
     */
    public java.lang.String getPropsConfig(){
         onPropGet(PROP_ID_propsConfig);
         return _propsConfig;
    }

    /**
     * 组件属性: PROPS_CONFIG
     */
    public void setPropsConfig(java.lang.String value){
        if(onPropSet(PROP_ID_propsConfig,value)){
            this._propsConfig = value;
            internalClearRefs(PROP_ID_propsConfig);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
    
    /**
     * 父资源
     */
    public io.nop.auth.dao.entity.NopAuthResource getParent(){
       return (io.nop.auth.dao.entity.NopAuthResource)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(io.nop.auth.dao.entity.NopAuthResource refEntity){
   
           if(refEntity == null){
           
                   this.setParentId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
           
                           this.setParentId(refEntity.getResourceId());
                       
           });
           }
       
    }
       
    /**
     * 子系统
     */
    public io.nop.auth.dao.entity.NopAuthSite getSite(){
       return (io.nop.auth.dao.entity.NopAuthSite)internalGetRefEntity(PROP_NAME_site);
    }

    public void setSite(io.nop.auth.dao.entity.NopAuthSite refEntity){
   
           if(refEntity == null){
           
                   this.setSiteId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_site, refEntity,()->{
           
                           this.setSiteId(refEntity.getSiteId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthResource> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        io.nop.auth.dao.entity.NopAuthResource.PROP_NAME_parent, null,io.nop.auth.dao.entity.NopAuthResource.class);

    /**
     * 子资源。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthResource> getChildren(){
       return _children;
    }
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthRoleResource> _roleMappings = new OrmEntitySet<>(this, PROP_NAME_roleMappings,
        io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_resource, null,io.nop.auth.dao.entity.NopAuthRoleResource.class);

    /**
     * 角色映射。 refPropName: resource, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.auth.dao.entity.NopAuthRoleResource> getRoleMappings(){
       return _roleMappings;
    }
       
   private io.nop.orm.component.JsonOrmComponent _metaConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_metaConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_metaConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_metaConfig);
      
   }

   public io.nop.orm.component.JsonOrmComponent getMetaConfigComponent(){
      if(_metaConfigComponent == null){
          _metaConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _metaConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_metaConfigComponent);
      }
      return _metaConfigComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _propsConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_propsConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_propsConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_propsConfig);
      
   }

   public io.nop.orm.component.JsonOrmComponent getPropsConfigComponent(){
      if(_propsConfigComponent == null){
          _propsConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _propsConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_propsConfigComponent);
      }
      return _propsConfigComponent;
   }

        public List<io.nop.auth.dao.entity.NopAuthRole> getRelatedRoleList(){
            return (List<io.nop.auth.dao.entity.NopAuthRole>)io.nop.orm.support.OrmEntityHelper.getRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_role);
        }
    
        public String getRelatedRoleList_label(){
        return io.nop.core.lang.utils.Underscore.pluckThenJoin(getRelatedRoleList(),io.nop.auth.dao.entity.NopAuthRole.PROP_NAME_roleName);
        }
    
        public List<java.lang.String> getRelatedRoleIdList(){
        return (List<java.lang.String>)io.nop.orm.support.OrmEntityHelper.getRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_roleId);
        }

        public void setRelatedRoleIdList(List<java.lang.String> value){
        io.nop.orm.support.OrmEntityHelper.setRefProps(getRoleMappings(),io.nop.auth.dao.entity.NopAuthRoleResource.PROP_NAME_roleId,value);
        }
    
}
// resume CPD analysis - CPD-ON
