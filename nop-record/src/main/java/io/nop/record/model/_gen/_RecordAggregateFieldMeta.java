package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordAggregateFieldMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordAggregateFieldMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: aggFunc
     * 
     */
    private java.lang.String _aggFunc ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: prop
     * 
     */
    private java.lang.String _prop ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _valueExpr ;
    
    /**
     * 
     * xml name: aggFunc
     *  
     */
    
    public java.lang.String getAggFunc(){
      return _aggFunc;
    }

    
    public void setAggFunc(java.lang.String value){
        checkAllowChange();
        
        this._aggFunc = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: prop
     *  
     */
    
    public java.lang.String getProp(){
      return _prop;
    }

    
    public void setProp(java.lang.String value){
        checkAllowChange();
        
        this._prop = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
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
        
        out.putNotNull("aggFunc",this.getAggFunc());
        out.putNotNull("name",this.getName());
        out.putNotNull("prop",this.getProp());
        out.putNotNull("valueExpr",this.getValueExpr());
    }

    public RecordAggregateFieldMeta cloneInstance(){
        RecordAggregateFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordAggregateFieldMeta instance){
        super.copyTo(instance);
        
        instance.setAggFunc(this.getAggFunc());
        instance.setName(this.getName());
        instance.setProp(this.getProp());
        instance.setValueExpr(this.getValueExpr());
    }

    protected RecordAggregateFieldMeta newInstance(){
        return (RecordAggregateFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
