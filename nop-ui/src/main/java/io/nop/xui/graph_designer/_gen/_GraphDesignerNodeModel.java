package io.nop.xui.graph_designer._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.graph_designer.GraphDesignerNodeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphDesignerNodeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: abstract
     * 
     */
    private java.lang.Boolean _abstract ;
    
    /**
     *  
     * xml name: addable
     * 
     */
    private java.lang.Boolean _addable  = true;
    
    /**
     *  
     * xml name: allowChildren
     * 
     */
    private java.util.Set<java.lang.String> _allowChildren ;
    
    /**
     *  
     * xml name: allowParents
     * 允许嵌入在哪些容器类型中。如果不配置，则允许嵌入到任意容器中
     */
    private java.util.Set<java.lang.String> _allowParents ;
    
    /**
     *  
     * xml name: anchors
     * 
     */
    private KeyedList<io.nop.xui.graph_designer.GraphDesignerAnchorModel> _anchors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: base
     * 
     */
    private java.lang.String _base ;
    
    /**
     *  
     * xml name: deletable
     * 
     */
    private java.lang.Boolean _deletable ;
    
    /**
     *  
     * xml name: draggable
     * 
     */
    private java.lang.Boolean _draggable ;
    
    /**
     *  
     * xml name: fixedAspectRatio
     * 
     */
    private java.lang.Boolean _fixedAspectRatio ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Integer _height ;
    
    /**
     *  
     * xml name: icon
     * 
     */
    private java.lang.String _icon ;
    
    /**
     *  
     * xml name: kind
     * start表示起始节点，end表示结束节点，normal表示一般节点
     */
    private java.lang.String _kind ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: layout
     * 
     */
    private java.lang.String _layout ;
    
    /**
     *  
     * xml name: maxHeight
     * 
     */
    private java.lang.Integer _maxHeight ;
    
    /**
     *  
     * xml name: maxOccurs
     * 
     */
    private java.lang.Integer _maxOccurs ;
    
    /**
     *  
     * xml name: maxWidth
     * 
     */
    private java.lang.Integer _maxWidth ;
    
    /**
     *  
     * xml name: minHeight
     * 
     */
    private java.lang.Integer _minHeight ;
    
    /**
     *  
     * xml name: minOccurs
     * 
     */
    private java.lang.Integer _minOccurs ;
    
    /**
     *  
     * xml name: minWidth
     * 
     */
    private java.lang.Integer _minWidth ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: propsForm
     * 属性表单
     */
    private java.lang.String _propsForm ;
    
    /**
     *  
     * xml name: resizable
     * 
     */
    private java.lang.Boolean _resizable ;
    
    /**
     *  
     * xml name: shape
     * 矩形、圆形等基础形状
     */
    private java.lang.String _shape ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private java.lang.String _style ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.core.lang.xml.XNode _template ;
    
    /**
     *  
     * xml name: textDraggable
     * 
     */
    private java.lang.Boolean _textDraggable ;
    
    /**
     *  
     * xml name: textPosition
     * 
     */
    private java.lang.String _textPosition ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Integer _width ;
    
    /**
     * 
     * xml name: abstract
     *  
     */
    
    public java.lang.Boolean getAbstract(){
      return _abstract;
    }

    
    public void setAbstract(java.lang.Boolean value){
        checkAllowChange();
        
        this._abstract = value;
           
    }

    
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
     * xml name: allowChildren
     *  
     */
    
    public java.util.Set<java.lang.String> getAllowChildren(){
      return _allowChildren;
    }

    
    public void setAllowChildren(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowChildren = value;
           
    }

    
    /**
     * 
     * xml name: allowParents
     *  允许嵌入在哪些容器类型中。如果不配置，则允许嵌入到任意容器中
     */
    
    public java.util.Set<java.lang.String> getAllowParents(){
      return _allowParents;
    }

    
    public void setAllowParents(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowParents = value;
           
    }

    
    /**
     * 
     * xml name: anchors
     *  
     */
    
    public java.util.List<io.nop.xui.graph_designer.GraphDesignerAnchorModel> getAnchors(){
      return _anchors;
    }

    
    public void setAnchors(java.util.List<io.nop.xui.graph_designer.GraphDesignerAnchorModel> value){
        checkAllowChange();
        
        this._anchors = KeyedList.fromList(value, io.nop.xui.graph_designer.GraphDesignerAnchorModel::getName);
           
    }

    
    public io.nop.xui.graph_designer.GraphDesignerAnchorModel getAnchor(String name){
        return this._anchors.getByKey(name);
    }

    public boolean hasAnchor(String name){
        return this._anchors.containsKey(name);
    }

    public void addAnchor(io.nop.xui.graph_designer.GraphDesignerAnchorModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.graph_designer.GraphDesignerAnchorModel> list = this.getAnchors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.graph_designer.GraphDesignerAnchorModel::getName);
            setAnchors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_anchors(){
        return this._anchors.keySet();
    }

    public boolean hasAnchors(){
        return !this._anchors.isEmpty();
    }
    
    /**
     * 
     * xml name: base
     *  
     */
    
    public java.lang.String getBase(){
      return _base;
    }

    
    public void setBase(java.lang.String value){
        checkAllowChange();
        
        this._base = value;
           
    }

    
    /**
     * 
     * xml name: deletable
     *  
     */
    
    public java.lang.Boolean getDeletable(){
      return _deletable;
    }

    
    public void setDeletable(java.lang.Boolean value){
        checkAllowChange();
        
        this._deletable = value;
           
    }

    
    /**
     * 
     * xml name: draggable
     *  
     */
    
    public java.lang.Boolean getDraggable(){
      return _draggable;
    }

    
    public void setDraggable(java.lang.Boolean value){
        checkAllowChange();
        
        this._draggable = value;
           
    }

    
    /**
     * 
     * xml name: fixedAspectRatio
     *  
     */
    
    public java.lang.Boolean getFixedAspectRatio(){
      return _fixedAspectRatio;
    }

    
    public void setFixedAspectRatio(java.lang.Boolean value){
        checkAllowChange();
        
        this._fixedAspectRatio = value;
           
    }

    
    /**
     * 
     * xml name: height
     *  
     */
    
    public java.lang.Integer getHeight(){
      return _height;
    }

    
    public void setHeight(java.lang.Integer value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
    /**
     * 
     * xml name: icon
     *  
     */
    
    public java.lang.String getIcon(){
      return _icon;
    }

    
    public void setIcon(java.lang.String value){
        checkAllowChange();
        
        this._icon = value;
           
    }

    
    /**
     * 
     * xml name: kind
     *  start表示起始节点，end表示结束节点，normal表示一般节点
     */
    
    public java.lang.String getKind(){
      return _kind;
    }

    
    public void setKind(java.lang.String value){
        checkAllowChange();
        
        this._kind = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: layout
     *  
     */
    
    public java.lang.String getLayout(){
      return _layout;
    }

    
    public void setLayout(java.lang.String value){
        checkAllowChange();
        
        this._layout = value;
           
    }

    
    /**
     * 
     * xml name: maxHeight
     *  
     */
    
    public java.lang.Integer getMaxHeight(){
      return _maxHeight;
    }

    
    public void setMaxHeight(java.lang.Integer value){
        checkAllowChange();
        
        this._maxHeight = value;
           
    }

    
    /**
     * 
     * xml name: maxOccurs
     *  
     */
    
    public java.lang.Integer getMaxOccurs(){
      return _maxOccurs;
    }

    
    public void setMaxOccurs(java.lang.Integer value){
        checkAllowChange();
        
        this._maxOccurs = value;
           
    }

    
    /**
     * 
     * xml name: maxWidth
     *  
     */
    
    public java.lang.Integer getMaxWidth(){
      return _maxWidth;
    }

    
    public void setMaxWidth(java.lang.Integer value){
        checkAllowChange();
        
        this._maxWidth = value;
           
    }

    
    /**
     * 
     * xml name: minHeight
     *  
     */
    
    public java.lang.Integer getMinHeight(){
      return _minHeight;
    }

    
    public void setMinHeight(java.lang.Integer value){
        checkAllowChange();
        
        this._minHeight = value;
           
    }

    
    /**
     * 
     * xml name: minOccurs
     *  
     */
    
    public java.lang.Integer getMinOccurs(){
      return _minOccurs;
    }

    
    public void setMinOccurs(java.lang.Integer value){
        checkAllowChange();
        
        this._minOccurs = value;
           
    }

    
    /**
     * 
     * xml name: minWidth
     *  
     */
    
    public java.lang.Integer getMinWidth(){
      return _minWidth;
    }

    
    public void setMinWidth(java.lang.Integer value){
        checkAllowChange();
        
        this._minWidth = value;
           
    }

    
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
     * xml name: propsForm
     *  属性表单
     */
    
    public java.lang.String getPropsForm(){
      return _propsForm;
    }

    
    public void setPropsForm(java.lang.String value){
        checkAllowChange();
        
        this._propsForm = value;
           
    }

    
    /**
     * 
     * xml name: resizable
     *  
     */
    
    public java.lang.Boolean getResizable(){
      return _resizable;
    }

    
    public void setResizable(java.lang.Boolean value){
        checkAllowChange();
        
        this._resizable = value;
           
    }

    
    /**
     * 
     * xml name: shape
     *  矩形、圆形等基础形状
     */
    
    public java.lang.String getShape(){
      return _shape;
    }

    
    public void setShape(java.lang.String value){
        checkAllowChange();
        
        this._shape = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public java.lang.String getStyle(){
      return _style;
    }

    
    public void setStyle(java.lang.String value){
        checkAllowChange();
        
        this._style = value;
           
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

    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.core.lang.xml.XNode getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._template = value;
           
    }

    
    /**
     * 
     * xml name: textDraggable
     *  
     */
    
    public java.lang.Boolean getTextDraggable(){
      return _textDraggable;
    }

    
    public void setTextDraggable(java.lang.Boolean value){
        checkAllowChange();
        
        this._textDraggable = value;
           
    }

    
    /**
     * 
     * xml name: textPosition
     *  
     */
    
    public java.lang.String getTextPosition(){
      return _textPosition;
    }

    
    public void setTextPosition(java.lang.String value){
        checkAllowChange();
        
        this._textPosition = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.Integer getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Integer value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._anchors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._anchors);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("abstract",this.getAbstract());
        out.putNotNull("addable",this.getAddable());
        out.putNotNull("allowChildren",this.getAllowChildren());
        out.putNotNull("allowParents",this.getAllowParents());
        out.putNotNull("anchors",this.getAnchors());
        out.putNotNull("base",this.getBase());
        out.putNotNull("deletable",this.getDeletable());
        out.putNotNull("draggable",this.getDraggable());
        out.putNotNull("fixedAspectRatio",this.getFixedAspectRatio());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("icon",this.getIcon());
        out.putNotNull("kind",this.getKind());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("layout",this.getLayout());
        out.putNotNull("maxHeight",this.getMaxHeight());
        out.putNotNull("maxOccurs",this.getMaxOccurs());
        out.putNotNull("maxWidth",this.getMaxWidth());
        out.putNotNull("minHeight",this.getMinHeight());
        out.putNotNull("minOccurs",this.getMinOccurs());
        out.putNotNull("minWidth",this.getMinWidth());
        out.putNotNull("name",this.getName());
        out.putNotNull("propsForm",this.getPropsForm());
        out.putNotNull("resizable",this.getResizable());
        out.putNotNull("shape",this.getShape());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("template",this.getTemplate());
        out.putNotNull("textDraggable",this.getTextDraggable());
        out.putNotNull("textPosition",this.getTextPosition());
        out.putNotNull("width",this.getWidth());
    }

    public GraphDesignerNodeModel cloneInstance(){
        GraphDesignerNodeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GraphDesignerNodeModel instance){
        super.copyTo(instance);
        
        instance.setAbstract(this.getAbstract());
        instance.setAddable(this.getAddable());
        instance.setAllowChildren(this.getAllowChildren());
        instance.setAllowParents(this.getAllowParents());
        instance.setAnchors(this.getAnchors());
        instance.setBase(this.getBase());
        instance.setDeletable(this.getDeletable());
        instance.setDraggable(this.getDraggable());
        instance.setFixedAspectRatio(this.getFixedAspectRatio());
        instance.setHeight(this.getHeight());
        instance.setIcon(this.getIcon());
        instance.setKind(this.getKind());
        instance.setLabel(this.getLabel());
        instance.setLayout(this.getLayout());
        instance.setMaxHeight(this.getMaxHeight());
        instance.setMaxOccurs(this.getMaxOccurs());
        instance.setMaxWidth(this.getMaxWidth());
        instance.setMinHeight(this.getMinHeight());
        instance.setMinOccurs(this.getMinOccurs());
        instance.setMinWidth(this.getMinWidth());
        instance.setName(this.getName());
        instance.setPropsForm(this.getPropsForm());
        instance.setResizable(this.getResizable());
        instance.setShape(this.getShape());
        instance.setStyle(this.getStyle());
        instance.setTagSet(this.getTagSet());
        instance.setTemplate(this.getTemplate());
        instance.setTextDraggable(this.getTextDraggable());
        instance.setTextPosition(this.getTextPosition());
        instance.setWidth(this.getWidth());
    }

    protected GraphDesignerNodeModel newInstance(){
        return (GraphDesignerNodeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
