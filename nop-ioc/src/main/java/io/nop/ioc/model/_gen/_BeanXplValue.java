package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [43:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanXplValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: outputMode
     * 
     */
    private io.nop.xlang.ast.XLangOutputMode _outputMode ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.xlang.api.EvalCode _source ;
    
    /**
     * 
     * xml name: outputMode
     *  
     */
    
    public io.nop.xlang.ast.XLangOutputMode getOutputMode(){
      return _outputMode;
    }

    
    public void setOutputMode(io.nop.xlang.ast.XLangOutputMode value){
        checkAllowChange();
        
        this._outputMode = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.xlang.api.EvalCode getSource(){
      return _source;
    }

    
    public void setSource(io.nop.xlang.api.EvalCode value){
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
        
        out.put("outputMode",this.getOutputMode());
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
