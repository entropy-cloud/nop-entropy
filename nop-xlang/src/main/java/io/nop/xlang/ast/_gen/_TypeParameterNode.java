//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.TypeParameterNode;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _TypeParameterNode extends XLangASTNode {
    
    protected io.nop.xlang.ast.NamedTypeNode lowerBound;
    
    protected io.nop.xlang.ast.Identifier name;
    
    protected io.nop.xlang.ast.NamedTypeNode upperBound;
    

    public _TypeParameterNode(){
    }

    
    public io.nop.xlang.ast.NamedTypeNode getLowerBound(){
        return lowerBound;
    }

    public void setLowerBound(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.lowerBound = value;
    }
    
    public io.nop.xlang.ast.Identifier getName(){
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.name = value;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getUpperBound(){
        return upperBound;
    }

    public void setUpperBound(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.upperBound = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public TypeParameterNode newInstance(){
      return new TypeParameterNode();
    }

    @Override
    public TypeParameterNode deepClone(){
       TypeParameterNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name.deepClone());
                      
                }
            
                if(upperBound != null){
                  
                          ret.setUpperBound(upperBound.deepClone());
                      
                }
            
                if(lowerBound != null){
                  
                          ret.setLowerBound(lowerBound.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(name != null)
                processor.accept(name);
        
            if(upperBound != null)
                processor.accept(upperBound);
        
            if(lowerBound != null)
                processor.accept(lowerBound);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(name != null && processor.apply(name) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(upperBound != null && processor.apply(upperBound) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(lowerBound != null && processor.apply(lowerBound) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.name == oldChild){
               this.setName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.upperBound == oldChild){
               this.setUpperBound((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
            if(this.lowerBound == oldChild){
               this.setLowerBound((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.name == child){
                this.setName(null);
                return true;
            }
        
            if(this.upperBound == child){
                this.setUpperBound(null);
                return true;
            }
        
            if(this.lowerBound == child){
                this.setLowerBound(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    TypeParameterNode other = (TypeParameterNode)node;
    
            if(!isNodeEquivalent(this.name,other.getName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.upperBound,other.getUpperBound())){
               return false;
            }
        
            if(!isNodeEquivalent(this.lowerBound,other.getLowerBound())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.TypeParameterNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(upperBound != null){
                      
                              json.put("upperBound", upperBound);
                          
                    }
                
                    if(lowerBound != null){
                      
                              json.put("lowerBound", lowerBound);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(name != null)
                    name.freeze(cascade);
                if(upperBound != null)
                    upperBound.freeze(cascade);
                if(lowerBound != null)
                    lowerBound.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
