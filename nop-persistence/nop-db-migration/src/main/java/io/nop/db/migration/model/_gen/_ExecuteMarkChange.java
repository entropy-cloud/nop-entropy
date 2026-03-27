package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.ExecuteMarkChange;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 执行标记（用于复杂的流程控制）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExecuteMarkChange extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: comment
     * 注释说明
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: continueOnError
     * 遇到错误是否继续
     */
    private boolean _continueOnError  = false;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: comment
     *  注释说明
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: continueOnError
     *  遇到错误是否继续
     */
    
    public boolean isContinueOnError(){
      return _continueOnError;
    }

    
    public void setContinueOnError(boolean value){
        checkAllowChange();
        
        this._continueOnError = value;
           
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
        
        out.putNotNull("comment",this.getComment());
        out.putNotNull("continueOnError",this.isContinueOnError());
        out.putNotNull("id",this.getId());
        out.putNotNull("type",this.getType());
    }

    public ExecuteMarkChange cloneInstance(){
        ExecuteMarkChange instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExecuteMarkChange instance){
        super.copyTo(instance);
        
        instance.setComment(this.getComment());
        instance.setContinueOnError(this.isContinueOnError());
        instance.setId(this.getId());
        instance.setType(this.getType());
    }

    protected ExecuteMarkChange newInstance(){
        return (ExecuteMarkChange) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
