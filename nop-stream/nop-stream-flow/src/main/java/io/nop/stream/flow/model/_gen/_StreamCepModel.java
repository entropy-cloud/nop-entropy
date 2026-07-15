package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamCepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * CEP 节点：复杂事件处理
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamCepModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: patternRef
     * 
     */
    private java.lang.String _patternRef ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: patternRef
     *  
     */
    
    public java.lang.String getPatternRef(){
      return _patternRef;
    }

    
    public void setPatternRef(java.lang.String value){
        checkAllowChange();
        
        this._patternRef = value;
           
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
        
        out.putNotNull("patternRef",this.getPatternRef());
        out.putNotNull("type",this.getType());
    }

    public StreamCepModel cloneInstance(){
        StreamCepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamCepModel instance){
        super.copyTo(instance);
        
        instance.setPatternRef(this.getPatternRef());
        instance.setType(this.getType());
    }

    protected StreamCepModel newInstance(){
        return (StreamCepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
