package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiRefViewModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/disp.xdef <p>
 * 对于对象属性或者对象列表属性，使用xview文件中定义的page去显示
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiRefViewModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: addable
     * 
     */
    private java.lang.Boolean _addable ;
    
    /**
     *  
     * xml name: buttonLabel
     * 如果不为空，则表示使用弹出页面显示。
     */
    private java.lang.String _buttonLabel ;
    
    /**
     *  
     * xml name: data
     * 
     */
    private java.lang.Object _data ;
    
    /**
     *  
     * xml name: editable
     * 
     */
    private java.lang.Boolean _editable ;
    
    /**
     *  
     * xml name: form
     * 
     */
    private java.lang.String _form ;
    
    /**
     *  
     * xml name: grid
     * 
     */
    private java.lang.String _grid ;
    
    /**
     *  
     * xml name: page
     * 
     */
    private java.lang.String _page ;
    
    /**
     *  
     * xml name: path
     * 如果对应于xview文件所在路径，则page/grid/form属性必须有一个为非空，根据它们动态构建一个页面。
     * 也可以直接指定page.yaml文件，直接复用已有的文件。
     * 如果为空，则试图根据当前模型文件路径猜测得到一个view模型文件路径
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: removable
     * 
     */
    private java.lang.Boolean _removable ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     * 
     * xml name: addable
     *  
     */
    
    public java.lang.Boolean getAddable(){
      return _addable;
    }

    
    public void setAddable(java.lang.Boolean value){
        checkAllowChange();
        
        this._addable = value;
           
    }

    
    /**
     * 
     * xml name: buttonLabel
     *  如果不为空，则表示使用弹出页面显示。
     */
    
    public java.lang.String getButtonLabel(){
      return _buttonLabel;
    }

    
    public void setButtonLabel(java.lang.String value){
        checkAllowChange();
        
        this._buttonLabel = value;
           
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
     * xml name: editable
     *  
     */
    
    public java.lang.Boolean getEditable(){
      return _editable;
    }

    
    public void setEditable(java.lang.Boolean value){
        checkAllowChange();
        
        this._editable = value;
           
    }

    
    /**
     * 
     * xml name: form
     *  
     */
    
    public java.lang.String getForm(){
      return _form;
    }

    
    public void setForm(java.lang.String value){
        checkAllowChange();
        
        this._form = value;
           
    }

    
    /**
     * 
     * xml name: grid
     *  
     */
    
    public java.lang.String getGrid(){
      return _grid;
    }

    
    public void setGrid(java.lang.String value){
        checkAllowChange();
        
        this._grid = value;
           
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
     * xml name: path
     *  如果对应于xview文件所在路径，则page/grid/form属性必须有一个为非空，根据它们动态构建一个页面。
     * 也可以直接指定page.yaml文件，直接复用已有的文件。
     * 如果为空，则试图根据当前模型文件路径猜测得到一个view模型文件路径
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: removable
     *  
     */
    
    public java.lang.Boolean getRemovable(){
      return _removable;
    }

    
    public void setRemovable(java.lang.Boolean value){
        checkAllowChange();
        
        this._removable = value;
           
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
        
        out.putNotNull("addable",this.getAddable());
        out.putNotNull("buttonLabel",this.getButtonLabel());
        out.putNotNull("data",this.getData());
        out.putNotNull("editable",this.getEditable());
        out.putNotNull("form",this.getForm());
        out.putNotNull("grid",this.getGrid());
        out.putNotNull("page",this.getPage());
        out.putNotNull("path",this.getPath());
        out.putNotNull("removable",this.getRemovable());
        out.putNotNull("title",this.getTitle());
    }

    public UiRefViewModel cloneInstance(){
        UiRefViewModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiRefViewModel instance){
        super.copyTo(instance);
        
        instance.setAddable(this.getAddable());
        instance.setButtonLabel(this.getButtonLabel());
        instance.setData(this.getData());
        instance.setEditable(this.getEditable());
        instance.setForm(this.getForm());
        instance.setGrid(this.getGrid());
        instance.setPage(this.getPage());
        instance.setPath(this.getPath());
        instance.setRemovable(this.getRemovable());
        instance.setTitle(this.getTitle());
    }

    protected UiRefViewModel newInstance(){
        return (UiRefViewModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
