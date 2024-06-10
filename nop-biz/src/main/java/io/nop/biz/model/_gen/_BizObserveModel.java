package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizObserveModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [110:10:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 监听全局EventBus上的event。当执行BizModel的action时会触发event, 此时from=bizObjName,event=actionId
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizObserveModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
        
        out.putNotNull("eventPattern",this.getEventPattern());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("id",this.getId());
        out.putNotNull("source",this.getSource());
    }

    public BizObserveModel cloneInstance(){
        BizObserveModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizObserveModel instance){
        super.copyTo(instance);
        
        instance.setEventPattern(this.getEventPattern());
        instance.setFrom(this.getFrom());
        instance.setId(this.getId());
        instance.setSource(this.getSource());
    }

    protected BizObserveModel newInstance(){
        return (BizObserveModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
