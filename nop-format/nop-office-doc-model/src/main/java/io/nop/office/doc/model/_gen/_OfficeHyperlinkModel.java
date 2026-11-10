package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeHyperlinkModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: anchor
     * 
     */
    private java.lang.String _anchor ;
    
    /**
     *  
     * xml name: href
     * 
     */
    private java.lang.String _href ;
    
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
    private io.nop.office.doc.model.OfficeHyperlinkTemplateModel _model ;
    
    /**
     *  
     * xml name: runs
     * 
     */
    private KeyedList<io.nop.office.doc.model.OfficeRun> _runs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: anchor
     *  
     */
    
    public java.lang.String getAnchor(){
      return _anchor;
    }

    
    public void setAnchor(java.lang.String value){
        checkAllowChange();
        
        this._anchor = value;
           
    }

    
    /**
     * 
     * xml name: href
     *  
     */
    
    public java.lang.String getHref(){
      return _href;
    }

    
    public void setHref(java.lang.String value){
        checkAllowChange();
        
        this._href = value;
           
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
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeHyperlinkTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeHyperlinkTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: runs
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeRun> getRuns(){
      return _runs;
    }

    
    public void setRuns(java.util.List<io.nop.office.doc.model.OfficeRun> value){
        checkAllowChange();
        
        this._runs = KeyedList.fromList(value, io.nop.office.doc.model.OfficeRun::getId);
           
    }

    
    public java.util.Set<String> keySet_runs(){
        return this._runs.keySet();
    }

    public boolean hasRuns(){
        return !this._runs.isEmpty();
    }
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._runs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._runs);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("anchor",this.getAnchor());
        out.putNotNull("href",this.getHref());
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("runs",this.getRuns());
        out.putNotNull("type",this.getType());
    }

    public OfficeHyperlinkModel cloneInstance(){
        OfficeHyperlinkModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeHyperlinkModel instance){
        super.copyTo(instance);
        
        instance.setAnchor(this.getAnchor());
        instance.setHref(this.getHref());
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setRuns(this.getRuns());
        instance.setType(this.getType());
    }

    protected OfficeHyperlinkModel newInstance(){
        return (OfficeHyperlinkModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
