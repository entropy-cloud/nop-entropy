//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _VariableDeclarator extends XLangASTNode {
    
    protected io.nop.xlang.ast.XLangASTNode id;
    
    protected io.nop.xlang.ast.Expression init;
    
    protected io.nop.xlang.ast.NamedTypeNode varType;
    

    public _VariableDeclarator(){
    }

    
    public io.nop.xlang.ast.XLangASTNode getId(){
        return id;
    }

    public void setId(io.nop.xlang.ast.XLangASTNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.id = value;
    }
    
    public io.nop.xlang.ast.Expression getInit(){
        return init;
    }

    public void setInit(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.init = value;
    }
    
    public io.nop.xlang.ast.NamedTypeNode getVarType(){
        return varType;
    }

    public void setVarType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.varType = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public VariableDeclarator newInstance(){
      return new VariableDeclarator();
    }

    @Override
    public VariableDeclarator deepClone(){
       VariableDeclarator ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id.deepClone());
                      
                }
            
                if(varType != null){
                  
                          ret.setVarType(varType.deepClone());
                      
                }
            
                if(init != null){
                  
                          ret.setInit(init.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(id != null)
                processor.accept(id);
        
            if(varType != null)
                processor.accept(varType);
        
            if(init != null)
                processor.accept(init);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(id != null && processor.apply(id) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(varType != null && processor.apply(varType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(init != null && processor.apply(init) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.id == oldChild){
               this.setId((io.nop.xlang.ast.XLangASTNode)newChild);
               return true;
            }
        
            if(this.varType == oldChild){
               this.setVarType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
            if(this.init == oldChild){
               this.setInit((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.id == child){
                this.setId(null);
                return true;
            }
        
            if(this.varType == child){
                this.setVarType(null);
                return true;
            }
        
            if(this.init == child){
                this.setInit(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    VariableDeclarator other = (VariableDeclarator)node;
    
            if(!isNodeEquivalent(this.id,other.getId())){
               return false;
            }
        
            if(!isNodeEquivalent(this.varType,other.getVarType())){
               return false;
            }
        
            if(!isNodeEquivalent(this.init,other.getInit())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.VariableDeclarator;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(varType != null){
                      
                              json.put("varType", varType);
                          
                    }
                
                    if(init != null){
                      
                              json.put("init", init);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(id != null)
                    id.freeze(cascade);
                if(varType != null)
                    varType.freeze(cascade);
                if(init != null)
                    init.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
