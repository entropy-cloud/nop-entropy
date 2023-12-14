//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _GenNodeExpression extends io.nop.xlang.ast.OutputExpression {
    
    protected java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> attrs;
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.Expression extAttrs;
    
    protected io.nop.xlang.ast.Expression tagName;
    
    protected boolean textNode;
    

    public _GenNodeExpression(){
    }

    
    public java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> getAttrs(){
        return attrs;
    }

    public void setAttrs(java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.attrs = value;
    }
    
    public java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> makeAttrs(){
        java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> list = getAttrs();
        if(list == null){
            list = new java.util.ArrayList<>();
            setAttrs(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.Expression getExtAttrs(){
        return extAttrs;
    }

    public void setExtAttrs(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.extAttrs = value;
    }
    
    public io.nop.xlang.ast.Expression getTagName(){
        return tagName;
    }

    public void setTagName(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.tagName = value;
    }
    
    public boolean getTextNode(){
        return textNode;
    }

    public void setTextNode(boolean value){
        checkAllowChange();
        
        this.textNode = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public GenNodeExpression newInstance(){
      return new GenNodeExpression();
    }

    @Override
    public GenNodeExpression deepClone(){
       GenNodeExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(tagName != null){
                  
                          ret.setTagName(tagName.deepClone());
                      
                }
            
                if(extAttrs != null){
                  
                          ret.setExtAttrs(extAttrs.deepClone());
                      
                }
            
                if(attrs != null){
                  
                          java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> copy_attrs = new java.util.ArrayList<>(attrs.size());
                          for(io.nop.xlang.ast.GenNodeAttrExpression item: attrs){
                              copy_attrs.add(item.deepClone());
                          }
                          ret.setAttrs(copy_attrs);
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
                ret.setTextNode(textNode);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(tagName != null)
                processor.accept(tagName);
        
            if(extAttrs != null)
                processor.accept(extAttrs);
        
            if(attrs != null){
               for(io.nop.xlang.ast.GenNodeAttrExpression child: attrs){
                    processor.accept(child);
                }
            }
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(tagName != null && processor.apply(tagName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(extAttrs != null && processor.apply(extAttrs) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(attrs != null){
               for(io.nop.xlang.ast.GenNodeAttrExpression child: attrs){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.tagName == oldChild){
               this.setTagName((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.extAttrs == oldChild){
               this.setExtAttrs((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.attrs != null){
               int index = this.attrs.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> list = this.replaceInList(this.attrs,index,newChild);
                   this.setAttrs(list);
                   return true;
               }
            }
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.tagName == child){
                this.setTagName(null);
                return true;
            }
        
            if(this.extAttrs == child){
                this.setExtAttrs(null);
                return true;
            }
        
            if(this.attrs != null){
               int index = this.attrs.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.GenNodeAttrExpression> list = this.removeInList(this.attrs,index);
                   this.setAttrs(list);
                   return true;
               }
            }
            if(this.body == child){
                this.setBody(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    GenNodeExpression other = (GenNodeExpression)node;
    
            if(!isNodeEquivalent(this.tagName,other.getTagName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.extAttrs,other.getExtAttrs())){
               return false;
            }
        
            if(isListEquivalent(this.attrs,other.getAttrs())){
               return false;
            }
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
                if(!isValueEquivalent(this.textNode,other.getTextNode())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.GenNodeExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(tagName != null){
                      
                              json.put("tagName", tagName);
                          
                    }
                
                    if(extAttrs != null){
                      
                              json.put("extAttrs", extAttrs);
                          
                    }
                
                    if(attrs != null){
                      
                              if(!attrs.isEmpty())
                                json.put("attrs", attrs);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
                   json.put("textNode", textNode);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(tagName != null)
                    tagName.freeze(cascade);
                if(extAttrs != null)
                    extAttrs.freeze(cascade);
                attrs = io.nop.api.core.util.FreezeHelper.freezeList(attrs,cascade);         
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
