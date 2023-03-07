package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [80:10:0:0]/nop/schema/record/record-field.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RecordFieldSwitchCase extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: when
     * 与on表达式的返回值比较，如果相等，则实际类型为type指定的值
     */
    private java.lang.String _when ;
    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  与on表达式的返回值比较，如果相等，则实际类型为type指定的值
     */
    
    public java.lang.String getWhen(){
      return _when;
    }

    
    public void setWhen(java.lang.String value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("type",this.getType());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
