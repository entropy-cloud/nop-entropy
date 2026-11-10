package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeListModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
    private KeyedList<io.nop.office.doc.model.OfficeListItemModel> _items = KeyedList.emptyList();
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.office.doc.model.OfficeListTemplateModel _model ;
    
    /**
     *  
     * xml name: numId
     * 
     */
    private java.lang.String _numId ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
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
     * xml name: items
     *  
     */
    
    public java.util.List<io.nop.office.doc.model.OfficeListItemModel> getItems(){
      return _items;
    }

    
    public void setItems(java.util.List<io.nop.office.doc.model.OfficeListItemModel> value){
        checkAllowChange();
        
        this._items = KeyedList.fromList(value, io.nop.office.doc.model.OfficeListItemModel::getId);
           
    }

    
    public io.nop.office.doc.model.OfficeListItemModel getItem(String name){
        return this._items.getByKey(name);
    }

    public boolean hasItem(String name){
        return this._items.containsKey(name);
    }

    public void addItem(io.nop.office.doc.model.OfficeListItemModel item) {
        checkAllowChange();
        java.util.List<io.nop.office.doc.model.OfficeListItemModel> list = this.getItems();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.office.doc.model.OfficeListItemModel::getId);
            setItems(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_items(){
        return this._items.keySet();
    }

    public boolean hasItems(){
        return !this._items.isEmpty();
    }
    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.office.doc.model.OfficeListTemplateModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.office.doc.model.OfficeListTemplateModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: numId
     *  
     */
    
    public java.lang.String getNumId(){
      return _numId;
    }

    
    public void setNumId(java.lang.String value){
        checkAllowChange();
        
        this._numId = value;
           
    }

    
    /**
     * 
     * xml name: styleId
     *  
     */
    
    public java.lang.String getStyleId(){
      return _styleId;
    }

    
    public void setStyleId(java.lang.String value){
        checkAllowChange();
        
        this._styleId = value;
           
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
        
           this._items = io.nop.api.core.util.FreezeHelper.deepFreeze(this._items);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("id",this.getId());
        out.putNotNull("items",this.getItems());
        out.putNotNull("model",this.getModel());
        out.putNotNull("numId",this.getNumId());
        out.putNotNull("styleId",this.getStyleId());
        out.putNotNull("type",this.getType());
    }

    public OfficeListModel cloneInstance(){
        OfficeListModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeListModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setItems(this.getItems());
        instance.setModel(this.getModel());
        instance.setNumId(this.getNumId());
        instance.setStyleId(this.getStyleId());
        instance.setType(this.getType());
    }

    protected OfficeListModel newInstance(){
        return (OfficeListModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
