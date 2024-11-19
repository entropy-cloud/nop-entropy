package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordTypeMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-definitions.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordTypeMeta extends io.nop.record.model.RecordObjectMeta {
    
    /**
     *  
     * xml name: abstract
     * 
     */
    private boolean _abstract  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: abstract
     *  
     */
    
    public boolean isAbstract(){
      return _abstract;
    }

    
    public void setAbstract(boolean value){
        checkAllowChange();
        
        this._abstract = value;
           
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
        
        out.putNotNull("abstract",this.isAbstract());
        out.putNotNull("name",this.getName());
    }

    public RecordTypeMeta cloneInstance(){
        RecordTypeMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordTypeMeta instance){
        super.copyTo(instance);
        
        instance.setAbstract(this.isAbstract());
        instance.setName(this.getName());
    }

    protected RecordTypeMeta newInstance(){
        return (RecordTypeMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
