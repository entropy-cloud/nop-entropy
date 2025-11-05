package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiDialogModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/action.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiDialogModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actions
     * 
     */
    private KeyedList<io.nop.xui.model.IUiActionModel> _actions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: closeOnEsc
     * 
     */
    private java.lang.Boolean _closeOnEsc ;
    
    /**
     *  
     * xml name: closeOnOutside
     * 
     */
    private java.lang.Boolean _closeOnOutside ;
    
    /**
     *  
     * xml name: data
     * 
     */
    private java.lang.Object _data ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.String _height ;
    
    /**
     *  
     * xml name: noActions
     * 
     */
    private java.lang.Boolean _noActions ;
    
    /**
     *  
     * xml name: page
     * 
     */
    private java.lang.String _page ;
    
    /**
     *  
     * xml name: showCloseButton
     * 
     */
    private java.lang.Boolean _showCloseButton ;
    
    /**
     *  
     * xml name: size
     * sm/lg/xl/full控制不同的大小。full表示全屏
     */
    private java.lang.String _size ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.String _width ;
    
    /**
     * 
     * xml name: actions
     *  
     */
    
    public java.util.List<io.nop.xui.model.IUiActionModel> getActions(){
      return _actions;
    }

    
    public void setActions(java.util.List<io.nop.xui.model.IUiActionModel> value){
        checkAllowChange();
        
        this._actions = KeyedList.fromList(value, io.nop.xui.model.IUiActionModel::getId);
           
    }

    
    public java.util.Set<String> keySet_actions(){
        return this._actions.keySet();
    }

    public boolean hasActions(){
        return !this._actions.isEmpty();
    }
    
    /**
     * 
     * xml name: closeOnEsc
     *  
     */
    
    public java.lang.Boolean getCloseOnEsc(){
      return _closeOnEsc;
    }

    
    public void setCloseOnEsc(java.lang.Boolean value){
        checkAllowChange();
        
        this._closeOnEsc = value;
           
    }

    
    /**
     * 
     * xml name: closeOnOutside
     *  
     */
    
    public java.lang.Boolean getCloseOnOutside(){
      return _closeOnOutside;
    }

    
    public void setCloseOnOutside(java.lang.Boolean value){
        checkAllowChange();
        
        this._closeOnOutside = value;
           
    }

    
    /**
     * 
     * xml name: data
     *  
     */
    
    public java.lang.Object getData(){
      return _data;
    }

    
    public void setData(java.lang.Object value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    /**
     * 
     * xml name: height
     *  
     */
    
    public java.lang.String getHeight(){
      return _height;
    }

    
    public void setHeight(java.lang.String value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
    /**
     * 
     * xml name: noActions
     *  
     */
    
    public java.lang.Boolean getNoActions(){
      return _noActions;
    }

    
    public void setNoActions(java.lang.Boolean value){
        checkAllowChange();
        
        this._noActions = value;
           
    }

    
    /**
     * 
     * xml name: page
     *  
     */
    
    public java.lang.String getPage(){
      return _page;
    }

    
    public void setPage(java.lang.String value){
        checkAllowChange();
        
        this._page = value;
           
    }

    
    /**
     * 
     * xml name: showCloseButton
     *  
     */
    
    public java.lang.Boolean getShowCloseButton(){
      return _showCloseButton;
    }

    
    public void setShowCloseButton(java.lang.Boolean value){
        checkAllowChange();
        
        this._showCloseButton = value;
           
    }

    
    /**
     * 
     * xml name: size
     *  sm/lg/xl/full控制不同的大小。full表示全屏
     */
    
    public java.lang.String getSize(){
      return _size;
    }

    
    public void setSize(java.lang.String value){
        checkAllowChange();
        
        this._size = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  
     */
    
    public java.lang.String getTitle(){
      return _title;
    }

    
    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this._title = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.String getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.String value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("actions",this.getActions());
        out.putNotNull("closeOnEsc",this.getCloseOnEsc());
        out.putNotNull("closeOnOutside",this.getCloseOnOutside());
        out.putNotNull("data",this.getData());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("noActions",this.getNoActions());
        out.putNotNull("page",this.getPage());
        out.putNotNull("showCloseButton",this.getShowCloseButton());
        out.putNotNull("size",this.getSize());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("width",this.getWidth());
    }

    public UiDialogModel cloneInstance(){
        UiDialogModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiDialogModel instance){
        super.copyTo(instance);
        
        instance.setActions(this.getActions());
        instance.setCloseOnEsc(this.getCloseOnEsc());
        instance.setCloseOnOutside(this.getCloseOnOutside());
        instance.setData(this.getData());
        instance.setHeight(this.getHeight());
        instance.setNoActions(this.getNoActions());
        instance.setPage(this.getPage());
        instance.setShowCloseButton(this.getShowCloseButton());
        instance.setSize(this.getSize());
        instance.setTitle(this.getTitle());
        instance.setWidth(this.getWidth());
    }

    protected UiDialogModel newInstance(){
        return (UiDialogModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
