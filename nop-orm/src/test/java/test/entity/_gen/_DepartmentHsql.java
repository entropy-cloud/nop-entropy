package test.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import test.entity.DepartmentHsql;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : TEST_DEPARTMENT_INFO_HSQL
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _DepartmentHsql extends DynamicOrmEntity{
    
    /* : ID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* : DEPT_NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* : LAYER_CODE VARCHAR */
    public static final String PROP_NAME_layerCode = "layerCode";
    public static final int PROP_ID_layerCode = 3;
    
    /* : layer_level INTEGER */
    public static final String PROP_NAME_layerLevel = "layerLevel";
    public static final int PROP_ID_layerLevel = 4;
    
    /* : PARENT_ID VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 5;
    

    private static int _PROP_ID_BOUND = 6;

    
    /* relation:  */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation:  */
    public static final String PROP_NAME_children = "children";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[6];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_layerCode] = PROP_NAME_layerCode;
          PROP_NAME_TO_ID.put(PROP_NAME_layerCode, PROP_ID_layerCode);
      
          PROP_ID_TO_NAME[PROP_ID_layerLevel] = PROP_NAME_layerLevel;
          PROP_NAME_TO_ID.put(PROP_NAME_layerLevel, PROP_ID_layerLevel);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
    }

    
    /* : ID */
    private java.lang.String _sid;
    
    /* : DEPT_NAME */
    private java.lang.String _name;
    
    /* : LAYER_CODE */
    private java.lang.String _layerCode;
    
    /* : layer_level */
    private java.lang.Integer _layerLevel;
    
    /* : PARENT_ID */
    private java.lang.String _parentId;
    

    public _DepartmentHsql(){
    }

    protected DepartmentHsql newInstance(){
       return new DepartmentHsql();
    }

    @Override
    public DepartmentHsql cloneInstance() {
        DepartmentHsql entity = newInstance();
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
      return "test.entity.DepartmentHsql";
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
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_layerCode:
               return getLayerCode();
        
            case PROP_ID_layerLevel:
               return getLayerLevel();
        
            case PROP_ID_parentId:
               return getParentId();
        
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
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
               break;
            }
        
            case PROP_ID_layerCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_layerCode));
               }
               setLayerCode(typedValue);
               break;
            }
        
            case PROP_ID_layerLevel:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_layerLevel));
               }
               setLayerLevel(typedValue);
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
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_layerCode:{
               onInitProp(propId);
               this._layerCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_layerLevel:{
               onInitProp(propId);
               this._layerLevel = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : ID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * : ID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * : DEPT_NAME
     */
    public java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * : DEPT_NAME
     */
    public void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * : LAYER_CODE
     */
    public java.lang.String getLayerCode(){
         onPropGet(PROP_ID_layerCode);
         return _layerCode;
    }

    /**
     * : LAYER_CODE
     */
    public void setLayerCode(java.lang.String value){
        if(onPropSet(PROP_ID_layerCode,value)){
            this._layerCode = value;
            internalClearRefs(PROP_ID_layerCode);
            
        }
    }
    
    /**
     * : layer_level
     */
    public java.lang.Integer getLayerLevel(){
         onPropGet(PROP_ID_layerLevel);
         return _layerLevel;
    }

    /**
     * : layer_level
     */
    public void setLayerLevel(java.lang.Integer value){
        if(onPropSet(PROP_ID_layerLevel,value)){
            this._layerLevel = value;
            internalClearRefs(PROP_ID_layerLevel);
            
        }
    }
    
    /**
     * : PARENT_ID
     */
    public java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * : PARENT_ID
     */
    public void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * 
     */
    public test.entity.DepartmentHsql getParent(){
       return (test.entity.DepartmentHsql)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(test.entity.DepartmentHsql refEntity){
       if(refEntity == null){
         
         this.setParentId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
             
                    this.setParentId(refEntity.getSid());
                 
          });
       }
    }
       
    private final OrmEntitySet<test.entity.DepartmentHsql> _children = new OrmEntitySet<>(this, PROP_NAME_children,
        test.entity.DepartmentHsql.PROP_NAME_parent, null,test.entity.DepartmentHsql.class);

    /**
     * 。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.DepartmentHsql> getChildren(){
       return _children;
    }
       
}
// resume CPD analysis - CPD-ON
