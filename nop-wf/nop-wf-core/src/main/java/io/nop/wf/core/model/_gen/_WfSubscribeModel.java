package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfSubscribeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [91:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 监听全局EventBus上的event。当执行BizModel的action时会触发event, 此时from=bizObjName,event=actionId
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfSubscribeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: eventPattern
     * 
     */
    private java.lang.String _eventPattern ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
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
     * xml name: from
     *  
     */
    
    public java.lang.String getFrom(){
      return _from;
    }

    
    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this._from = value;
           
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
        out.put("from",this.getFrom());
        out.put("id",this.getId());
        out.put("source",this.getSource());
    }

    public WfSubscribeModel cloneInstance(){
        WfSubscribeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfSubscribeModel instance){
        super.copyTo(instance);
        
        instance.setEventPattern(this.getEventPattern());
        instance.setFrom(this.getFrom());
        instance.setId(this.getId());
        instance.setSource(this.getSource());
    }

    protected WfSubscribeModel newInstance(){
        return (WfSubscribeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
