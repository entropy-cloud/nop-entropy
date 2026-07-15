package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSideOutputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * SideOutput 节点：侧输出
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSideOutputModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: condition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _condition ;
    
    /**
     *  
     * xml name: tag
     * 
     */
    private java.lang.String _tag ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: condition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getCondition(){
      return _condition;
    }

    
    public void setCondition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._condition = value;
           
    }

    
    /**
     * 
     * xml name: tag
     *  
     */
    
    public java.lang.String getTag(){
      return _tag;
    }

    
    public void setTag(java.lang.String value){
        checkAllowChange();
        
        this._tag = value;
           
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
        
        out.putNotNull("condition",this.getCondition());
        out.putNotNull("tag",this.getTag());
        out.putNotNull("type",this.getType());
    }

    public StreamSideOutputModel cloneInstance(){
        StreamSideOutputModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSideOutputModel instance){
        super.copyTo(instance);
        
        instance.setCondition(this.getCondition());
        instance.setTag(this.getTag());
        instance.setType(this.getType());
    }

    protected StreamSideOutputModel newInstance(){
        return (StreamSideOutputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
