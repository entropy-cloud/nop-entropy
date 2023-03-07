//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ForOfStatement extends io.nop.xlang.ast.Statement {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.Identifier index;
    
    protected io.nop.xlang.ast.Expression left;
    
    protected io.nop.xlang.ast.Expression right;
    

    public _ForOfStatement(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.Identifier getIndex(){
        return index;
    }

    public void setIndex(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.index = value;
    }
    
    public io.nop.xlang.ast.Expression getLeft(){
        return left;
    }

    public void setLeft(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.left = value;
    }
    
    public io.nop.xlang.ast.Expression getRight(){
        return right;
    }

    public void setRight(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.right = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
          checkMandatory("left",getLeft());
       
          checkMandatory("right",getRight());
       
    }


    public ForOfStatement newInstance(){
      return new ForOfStatement();
    }

    @Override
    public ForOfStatement deepClone(){
       ForOfStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(index != null){
                  
                          ret.setIndex(index.deepClone());
                      
                }
            
                if(left != null){
                  
                          ret.setLeft(left.deepClone());
                      
                }
            
                if(right != null){
                  
                          ret.setRight(right.deepClone());
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(index != null)
                processor.accept(index);
        
            if(left != null)
                processor.accept(left);
        
            if(right != null)
                processor.accept(right);
        
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(index != null && processor.apply(index) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(left != null && processor.apply(left) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(right != null && processor.apply(right) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.index == oldChild){
               this.setIndex((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.left == oldChild){
               this.setLeft((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.right == oldChild){
               this.setRight((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.index == child){
                this.setIndex(null);
                return true;
            }
        
            if(this.left == child){
                this.setLeft(null);
                return true;
            }
        
            if(this.right == child){
                this.setRight(null);
                return true;
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
    ForOfStatement other = (ForOfStatement)node;
    
            if(!isNodeEquivalent(this.index,other.getIndex())){
               return false;
            }
        
            if(!isNodeEquivalent(this.left,other.getLeft())){
               return false;
            }
        
            if(!isNodeEquivalent(this.right,other.getRight())){
               return false;
            }
        
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ForOfStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(index != null){
                      
                              json.put("index", index);
                          
                    }
                
                    if(left != null){
                      
                              json.put("left", left);
                          
                    }
                
                    if(right != null){
                      
                              json.put("right", right);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(index != null)
                    index.freeze(cascade);
                if(left != null)
                    left.freeze(cascade);
                if(right != null)
                    right.freeze(cascade);
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
