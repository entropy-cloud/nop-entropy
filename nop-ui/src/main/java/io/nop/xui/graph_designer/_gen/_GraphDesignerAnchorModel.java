package io.nop.xui.graph_designer._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.graph_designer.GraphDesignerAnchorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [34:18:0:0]/nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphDesignerAnchorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: positon
     * 
     */
    private java.lang.String _positon ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
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
     * xml name: positon
     *  
     */
    
    public java.lang.String getPositon(){
      return _positon;
    }

    
    public void setPositon(java.lang.String value){
        checkAllowChange();
        
        this._positon = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
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
        
        out.put("name",this.getName());
        out.put("positon",this.getPositon());
        out.put("tagSet",this.getTagSet());
    }

    public GraphDesignerAnchorModel cloneInstance(){
        GraphDesignerAnchorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GraphDesignerAnchorModel instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
        instance.setPositon(this.getPositon());
        instance.setTagSet(this.getTagSet());
    }

    protected GraphDesignerAnchorModel newInstance(){
        return (GraphDesignerAnchorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
