package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordEnumOption;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordEnumOption extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: doc
     * 
     */
    private java.lang.String _doc ;
    
    /**
     *  
     * xml name: docRef
     * 翻译为java doc的@see注释
     */
    private java.lang.String _docRef ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     * 
     * xml name: doc
     *  
     */
    
    public java.lang.String getDoc(){
      return _doc;
    }

    
    public void setDoc(java.lang.String value){
        checkAllowChange();
        
        this._doc = value;
           
    }

    
    /**
     * 
     * xml name: docRef
     *  翻译为java doc的@see注释
     */
    
    public java.lang.String getDocRef(){
      return _docRef;
    }

    
    public void setDocRef(java.lang.String value){
        checkAllowChange();
        
        this._docRef = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this._value = value;
           
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
        
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("docRef",this.getDocRef());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("value",this.getValue());
    }

    public RecordEnumOption cloneInstance(){
        RecordEnumOption instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordEnumOption instance){
        super.copyTo(instance);
        
        instance.setDoc(this.getDoc());
        instance.setDocRef(this.getDocRef());
        instance.setLabel(this.getLabel());
        instance.setValue(this.getValue());
    }

    protected RecordEnumOption newInstance(){
        return (RecordEnumOption) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
