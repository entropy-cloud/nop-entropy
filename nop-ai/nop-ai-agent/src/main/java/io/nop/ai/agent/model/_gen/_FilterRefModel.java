package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.FilterRefModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _FilterRefModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: points
     * 
     */
    private java.util.Set<java.lang.String> _points ;
    
    /**
     *  
     * xml name: ref
     * 
     */
    private java.lang.String _ref ;
    
    /**
     * 
     * xml name: points
     *  
     */
    
    public java.util.Set<java.lang.String> getPoints(){
      return _points;
    }

    
    public void setPoints(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._points = value;
           
    }

    
    /**
     * 
     * xml name: ref
     *  
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
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
        
        out.putNotNull("points",this.getPoints());
        out.putNotNull("ref",this.getRef());
    }

    public FilterRefModel cloneInstance(){
        FilterRefModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(FilterRefModel instance){
        super.copyTo(instance);
        
        instance.setPoints(this.getPoints());
        instance.setRef(this.getRef());
    }

    protected FilterRefModel newInstance(){
        return (FilterRefModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
