package io.nop.orm.model.interceptor._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.interceptor.OrmInterceptorActionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [15:10:0:0]/nop/schema/orm/orm-interceptor.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmInterceptorActionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _event ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: order
     * 当存在多个同名的action时，由order决定执行顺序
     */
    private int _order  = 100;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getEvent(){
      return _event;
    }

    
    public void setEvent(java.lang.String value){
        checkAllowChange();
        
        this._event = value;
           
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
     * xml name: order
     *  当存在多个同名的action时，由order决定执行顺序
     */
    
    public int getOrder(){
      return _order;
    }

    
    public void setOrder(int value){
        checkAllowChange();
        
        this._order = value;
           
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
        
        out.putNotNull("event",this.getEvent());
        out.putNotNull("id",this.getId());
        out.putNotNull("order",this.getOrder());
        out.putNotNull("source",this.getSource());
    }

    public OrmInterceptorActionModel cloneInstance(){
        OrmInterceptorActionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmInterceptorActionModel instance){
        super.copyTo(instance);
        
        instance.setEvent(this.getEvent());
        instance.setId(this.getId());
        instance.setOrder(this.getOrder());
        instance.setSource(this.getSource());
    }

    protected OrmInterceptorActionModel newInstance(){
        return (OrmInterceptorActionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
