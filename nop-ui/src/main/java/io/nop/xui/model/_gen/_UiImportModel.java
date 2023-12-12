package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [12:6:0:0]/nop/schema/xui/xuc.xdef <p>
 * <x:gen-extends>
 * <xui:UsePage src="a.xpage" page="crud" />
 * editor生成XNode，然后编程处理
 * </x:gen-extends>
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _UiImportModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: items
     * 
     */
    private java.util.Set<java.lang.String> _items ;
    
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
     * xml name: items
     *  
     */
    
    public java.util.Set<java.lang.String> getItems(){
      return _items;
    }

    
    public void setItems(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._items = value;
           
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
        out.put("id",this.getId());
        out.put("items",this.getItems());
    }
}
 // resume CPD analysis - CPD-ON
