package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeRunModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.OfficeRunTemplateModel _model ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private io.nop.office.model.WordRunStyle _style ;
    
    /**
     *  
     * xml name: t
     * 
     */
    private java.lang.String _t ;
    
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
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeRunTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeRunTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public io.nop.office.model.WordRunStyle getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.office.model.WordRunStyle value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: t
     *  
     */
    
    public java.lang.String getT(){
      return _t;
    }

    
    public void setT(java.lang.String value){
        checkAllowChange();
        
        this._t = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("t",this.getT());
    }

    public OfficeRunModel cloneInstance(){
        OfficeRunModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeRunModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setStyle(this.getStyle());
        instance.setT(this.getT());
    }

    protected OfficeRunModel newInstance(){
        return (OfficeRunModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
