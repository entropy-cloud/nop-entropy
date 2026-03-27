package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.CustomChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 自定义变更（使用 XPL 脚本实现）
 * 用于实现复杂的迁移逻辑
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CustomChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: implementation
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _implementation ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
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
     * xml name: implementation
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getImplementation(){
      return _implementation;
    }

    
    public void setImplementation(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._implementation = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("id",this.getId());
        out.putNotNull("implementation",this.getImplementation());
        out.putNotNull("type",this.getType());
    }

    public CustomChange cloneInstance(){
        CustomChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CustomChange instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setImplementation(this.getImplementation());
        instance.setType(this.getType());
    }

    protected CustomChange newInstance(){
        return (CustomChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
