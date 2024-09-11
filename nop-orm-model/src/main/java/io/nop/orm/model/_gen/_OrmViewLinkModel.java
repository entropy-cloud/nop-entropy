package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmViewLinkModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/view-entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmViewLinkModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: join
     * 
     */
    private java.util.List<io.nop.orm.model.OrmViewJoinOnModel> _join = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: leftEntityAlias
     * 
     */
    private java.lang.String _leftEntityAlias ;
    
    /**
     *  
     * xml name: rightEntityAlias
     * 
     */
    private java.lang.String _rightEntityAlias ;
    
    /**
     * 
     * xml name: join
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmViewJoinOnModel> getJoin(){
      return _join;
    }

    
    public void setJoin(java.util.List<io.nop.orm.model.OrmViewJoinOnModel> value){
        checkAllowChange();
        
        this._join = value;
           
    }

    
    /**
     * 
     * xml name: leftEntityAlias
     *  
     */
    
    public java.lang.String getLeftEntityAlias(){
      return _leftEntityAlias;
    }

    
    public void setLeftEntityAlias(java.lang.String value){
        checkAllowChange();
        
        this._leftEntityAlias = value;
           
    }

    
    /**
     * 
     * xml name: rightEntityAlias
     *  
     */
    
    public java.lang.String getRightEntityAlias(){
      return _rightEntityAlias;
    }

    
    public void setRightEntityAlias(java.lang.String value){
        checkAllowChange();
        
        this._rightEntityAlias = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._join = io.nop.api.core.util.FreezeHelper.deepFreeze(this._join);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("join",this.getJoin());
        out.putNotNull("leftEntityAlias",this.getLeftEntityAlias());
        out.putNotNull("rightEntityAlias",this.getRightEntityAlias());
    }

    public OrmViewLinkModel cloneInstance(){
        OrmViewLinkModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmViewLinkModel instance){
        super.copyTo(instance);
        
        instance.setJoin(this.getJoin());
        instance.setLeftEntityAlias(this.getLeftEntityAlias());
        instance.setRightEntityAlias(this.getRightEntityAlias());
    }

    protected OrmViewLinkModel newInstance(){
        return (OrmViewLinkModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
