package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeDocModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: height
     * 
     */
    private double _height ;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.OfficeDocTemplateModel _model ;
    
    /**
     *  
     * xml name: pages
     * 
     */
    private KeyedList<io.nop.office.doc.model.OfficeDocPageModel> _pages = KeyedList.emptyList();
    
    /**
     *  
     * xml name: width
     * 
     */
    private double _width ;
    
    /**
     * 
     * xml name: height
     *  
     */
    
    public double getHeight(){
      return _height;
    }

    
    public void setHeight(double value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeDocTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeDocTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: pages
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeDocPageModel> getPages(){
      return _pages;
    }

    
    public void setPages(java.util.List<io.nop.office.doc.model.OfficeDocPageModel> value){
        checkAllowChange();
        
        this._pages = KeyedList.fromList(value, io.nop.office.doc.model.OfficeDocPageModel::getName);
           
    }

    
    public io.nop.office.doc.model.OfficeDocPageModel getPage(String name){
        return this._pages.getByKey(name);
    }

    public boolean hasPage(String name){
        return this._pages.containsKey(name);
    }

    public void addPage(io.nop.office.doc.model.OfficeDocPageModel item) {
        checkAllowChange();
        java.util.List<io.nop.office.doc.model.OfficeDocPageModel> list = this.getPages();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.office.doc.model.OfficeDocPageModel::getName);
            setPages(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_pages(){
        return this._pages.keySet();
    }

    public boolean hasPages(){
        return !this._pages.isEmpty();
    }
    
    /**
     * 
     * xml name: width
     *  
     */
    
    public double getWidth(){
      return _width;
    }

    
    public void setWidth(double value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._pages = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pages);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("height",this.getHeight());
        out.putNotNull("model",this.getModel());
        out.putNotNull("pages",this.getPages());
        out.putNotNull("width",this.getWidth());
    }

    public OfficeDocModel cloneInstance(){
        OfficeDocModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeDocModel instance){
        super.copyTo(instance);
        
        instance.setHeight(this.getHeight());
        instance.setModel(this.getModel());
        instance.setPages(this.getPages());
        instance.setWidth(this.getWidth());
    }

    protected OfficeDocModel newInstance(){
        return (OfficeDocModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
