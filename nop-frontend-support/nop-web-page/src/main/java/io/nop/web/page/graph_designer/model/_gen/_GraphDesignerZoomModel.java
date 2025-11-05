package io.nop.web.page.graph_designer.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.web.page.graph_designer.model.GraphDesignerZoomModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphDesignerZoomModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: initialValue
     * 
     */
    private java.lang.Integer _initialValue ;
    
    /**
     *  
     * xml name: max
     * 
     */
    private java.lang.Integer _max ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.Integer _min ;
    
    /**
     *  
     * xml name: step
     * 
     */
    private java.lang.Integer _step ;
    
    /**
     * 
     * xml name: initialValue
     *  
     */
    
    public java.lang.Integer getInitialValue(){
      return _initialValue;
    }

    
    public void setInitialValue(java.lang.Integer value){
        checkAllowChange();
        
        this._initialValue = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  
     */
    
    public java.lang.Integer getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Integer value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.Integer getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Integer value){
        checkAllowChange();
        
        this._min = value;
           
    }

    
    /**
     * 
     * xml name: step
     *  
     */
    
    public java.lang.Integer getStep(){
      return _step;
    }

    
    public void setStep(java.lang.Integer value){
        checkAllowChange();
        
        this._step = value;
           
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
        
        out.putNotNull("initialValue",this.getInitialValue());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
        out.putNotNull("step",this.getStep());
    }

    public GraphDesignerZoomModel cloneInstance(){
        GraphDesignerZoomModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GraphDesignerZoomModel instance){
        super.copyTo(instance);
        
        instance.setInitialValue(this.getInitialValue());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
        instance.setStep(this.getStep());
    }

    protected GraphDesignerZoomModel newInstance(){
        return (GraphDesignerZoomModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
