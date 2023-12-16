package io.nop.xui.graph_designer._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [11:6:0:0]/nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
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
        
        out.put("initialValue",this.getInitialValue());
        out.put("max",this.getMax());
        out.put("min",this.getMin());
        out.put("step",this.getStep());
    }
}
 // resume CPD analysis - CPD-ON
