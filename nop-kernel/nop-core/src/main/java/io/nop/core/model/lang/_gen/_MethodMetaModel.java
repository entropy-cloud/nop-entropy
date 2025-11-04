package io.nop.core.model.lang._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.lang.MethodMetaModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/lang/method.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _MethodMetaModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: annotations
     * 
     */
    private io.nop.core.lang.xml.XNode _annotations ;
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.core.model.lang.MethodArgMetaModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: code
     * 
     */
    private java.lang.String _code ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: returnType
     * 
     */
    private io.nop.core.type.IGenericType _returnType ;
    
    /**
     * 
     * xml name: annotations
     *  
     */
    
    public io.nop.core.lang.xml.XNode getAnnotations(){
      return _annotations;
    }

    
    public void setAnnotations(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._annotations = value;
           
    }

    
    /**
     * 
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.core.model.lang.MethodArgMetaModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.core.model.lang.MethodArgMetaModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.core.model.lang.MethodArgMetaModel::getName);
           
    }

    
    public io.nop.core.model.lang.MethodArgMetaModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.core.model.lang.MethodArgMetaModel item) {
        checkAllowChange();
        java.util.List<io.nop.core.model.lang.MethodArgMetaModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.core.model.lang.MethodArgMetaModel::getName);
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
     * xml name: code
     *  
     */
    
    public java.lang.String getCode(){
      return _code;
    }

    
    public void setCode(java.lang.String value){
        checkAllowChange();
        
        this._code = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
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
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
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
     * xml name: returnType
     *  
     */
    
    public io.nop.core.type.IGenericType getReturnType(){
      return _returnType;
    }

    
    public void setReturnType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._returnType = value;
           
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
        
        out.putNotNull("annotations",this.getAnnotations());
        out.putNotNull("args",this.getArgs());
        out.putNotNull("code",this.getCode());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("returnType",this.getReturnType());
    }

    public MethodMetaModel cloneInstance(){
        MethodMetaModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(MethodMetaModel instance){
        super.copyTo(instance);
        
        instance.setAnnotations(this.getAnnotations());
        instance.setArgs(this.getArgs());
        instance.setCode(this.getCode());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setId(this.getId());
        instance.setName(this.getName());
        instance.setReturnType(this.getReturnType());
    }

    protected MethodMetaModel newInstance(){
        return (MethodMetaModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
