package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [94:10:0:0]/nop/schema/orm/entity.xdef <p>
 * 根据当前字段值计算得到的属性。在java对象上可以通过get/set方法来实现getter/setter，也可以通过这里的脚本配置来实现。
 * compute的结果不会被自动缓存，每次访问都会重新计算。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _OrmComputePropModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: args
     * 
     */
    private KeyedList<io.nop.orm.model.OrmComputeArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: getter
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _getter ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: notGenCode
     * 
     */
    private boolean _notGenCode  = false;
    
    /**
     *  
     * xml name: setter
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _setter ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: args
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmComputeArgModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.orm.model.OrmComputeArgModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.orm.model.OrmComputeArgModel::getName);
           
    }

    
    public io.nop.orm.model.OrmComputeArgModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.orm.model.OrmComputeArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.OrmComputeArgModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.OrmComputeArgModel::getName);
            setArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_args(){
        return this._args.keySet();
    }

    public boolean hasArgs(){
        return !this._args.isEmpty();
    }
    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: getter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getGetter(){
      return _getter;
    }

    
    public void setGetter(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._getter = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: notGenCode
     *  
     */
    
    public boolean isNotGenCode(){
      return _notGenCode;
    }

    
    public void setNotGenCode(boolean value){
        checkAllowChange();
        
        this._notGenCode = value;
           
    }

    
    /**
     * 
     * xml name: setter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSetter(){
      return _setter;
    }

    
    public void setSetter(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._setter = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("args",this.getArgs());
        out.put("displayName",this.getDisplayName());
        out.put("getter",this.getGetter());
        out.put("name",this.getName());
        out.put("notGenCode",this.isNotGenCode());
        out.put("setter",this.getSetter());
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
