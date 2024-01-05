package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfListenerModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [82:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfListenerModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: eventPattern
     * 
     */
    private java.lang.String _eventPattern ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: eventPattern
     *  
     */
    
    public java.lang.String getEventPattern(){
      return _eventPattern;
    }

    
    public void setEventPattern(java.lang.String value){
        checkAllowChange();
        
        this._eventPattern = value;
           
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
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.put("eventPattern",this.getEventPattern());
        out.put("id",this.getId());
        out.put("source",this.getSource());
    }

    public WfListenerModel cloneInstance(){
        WfListenerModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfListenerModel instance){
        super.copyTo(instance);
        
        instance.setEventPattern(this.getEventPattern());
        instance.setId(this.getId());
        instance.setSource(this.getSource());
    }

    protected WfListenerModel newInstance(){
        return (WfListenerModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
