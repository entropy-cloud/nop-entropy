package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeParagraphModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
    private io.nop.office.doc.model.OfficeParagraphTemplateModel _model ;
    
    /**
     *  
     * xml name: r
     * 
     */
    private KeyedList<io.nop.office.doc.model.OfficeRunModel> _runs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: style
     * 
     */
    private io.nop.office.model.WordParagraphStyle _style ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
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
    
    public io.nop.office.doc.model.OfficeParagraphTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeParagraphTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: r
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeRunModel> getRuns(){
      return _runs;
    }

    
    public void setRuns(java.util.List<io.nop.office.doc.model.OfficeRunModel> value){
        checkAllowChange();
        
        this._runs = KeyedList.fromList(value, io.nop.office.doc.model.OfficeRunModel::getId);
           
    }

    
    public io.nop.office.doc.model.OfficeRunModel getR(String name){
        return this._runs.getByKey(name);
    }

    public boolean hasR(String name){
        return this._runs.containsKey(name);
    }

    public void addR(io.nop.office.doc.model.OfficeRunModel item) {
        checkAllowChange();
        java.util.List<io.nop.office.doc.model.OfficeRunModel> list = this.getRuns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.office.doc.model.OfficeRunModel::getId);
            setRuns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_runs(){
        return this._runs.keySet();
    }

    public boolean hasRuns(){
        return !this._runs.isEmpty();
    }
    
    /**
     * 
     * xml name: style
     *  
     */
    
    public io.nop.office.model.WordParagraphStyle getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.office.model.WordParagraphStyle value){
        checkAllowChange();
        
        this._style = value;
           
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
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("id",this.getId());
        out.putNotNull("model",this.getModel());
        out.putNotNull("runs",this.getRuns());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("type",this.getType());
    }

    public OfficeParagraphModel cloneInstance(){
        OfficeParagraphModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeParagraphModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setModel(this.getModel());
        instance.setRuns(this.getRuns());
        instance.setStyle(this.getStyle());
        instance.setType(this.getType());
    }

    protected OfficeParagraphModel newInstance(){
        return (OfficeParagraphModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
