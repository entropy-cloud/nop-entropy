package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [43:18:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BizCacheEvictModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cacheKeyExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _cacheKeyExpr ;
    
    /**
     *  
     * xml name: cacheName
     * 
     */
    private java.lang.String _cacheName ;
    
    /**
     * 
     * xml name: cacheKeyExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getCacheKeyExpr(){
      return _cacheKeyExpr;
    }

    
    public void setCacheKeyExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._cacheKeyExpr = value;
           
    }

    
    /**
     * 
     * xml name: cacheName
     *  
     */
    
    public java.lang.String getCacheName(){
      return _cacheName;
    }

    
    public void setCacheName(java.lang.String value){
        checkAllowChange();
        
        this._cacheName = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cacheKeyExpr",this.getCacheKeyExpr());
        out.put("cacheName",this.getCacheName());
    }
}
 // resume CPD analysis - CPD-ON
