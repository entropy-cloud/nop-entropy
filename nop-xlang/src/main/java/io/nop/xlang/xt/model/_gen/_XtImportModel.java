package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [15:6:0:0]/nop/schema/xt.xdef <p>
 * 导入其他的xt转换规则定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _XtImportModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: prefix
     * 为了避免和当前文件中定义的名称冲突，可以为导入的mapping和transform规则id增加前缀。
     */
    private java.lang.String _prefix ;
    
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
     * xml name: prefix
     *  为了避免和当前文件中定义的名称冲突，可以为导入的mapping和transform规则id增加前缀。
     */
    
    public java.lang.String getPrefix(){
      return _prefix;
    }

    
    public void setPrefix(java.lang.String value){
        checkAllowChange();
        
        this._prefix = value;
           
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
        
        out.put("from",this.getFrom());
        out.put("prefix",this.getPrefix());
    }
}
 // resume CPD analysis - CPD-ON
