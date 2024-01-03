//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ExportNamedDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ExportNamedDeclaration extends io.nop.xlang.ast.ModuleDeclaration {
    
    protected io.nop.xlang.ast.Literal source;
    
    protected java.util.List<io.nop.xlang.ast.ExportSpecifier> specifiers;
    

    public _ExportNamedDeclaration(){
    }

    
    public io.nop.xlang.ast.Literal getSource(){
        return source;
    }

    public void setSource(io.nop.xlang.ast.Literal value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.source = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ExportSpecifier> getSpecifiers(){
        return specifiers;
    }

    public void setSpecifiers(java.util.List<io.nop.xlang.ast.ExportSpecifier> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.specifiers = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ExportSpecifier> makeSpecifiers(){
        java.util.List<io.nop.xlang.ast.ExportSpecifier> list = getSpecifiers();
        if(list == null){
            list = new java.util.ArrayList<>();
            setSpecifiers(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("source",getSource());
       
          checkMandatory("specifiers",getSpecifiers());
       
    }


    public ExportNamedDeclaration newInstance(){
      return new ExportNamedDeclaration();
    }

    @Override
    public ExportNamedDeclaration deepClone(){
       ExportNamedDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(specifiers != null){
                  
                          java.util.List<io.nop.xlang.ast.ExportSpecifier> copy_specifiers = new java.util.ArrayList<>(specifiers.size());
                          for(io.nop.xlang.ast.ExportSpecifier item: specifiers){
                              copy_specifiers.add(item.deepClone());
                          }
                          ret.setSpecifiers(copy_specifiers);
                      
                }
            
                if(source != null){
                  
                          ret.setSource(source.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(specifiers != null){
               for(io.nop.xlang.ast.ExportSpecifier child: specifiers){
                    processor.accept(child);
                }
            }
            if(source != null)
                processor.accept(source);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(specifiers != null){
               for(io.nop.xlang.ast.ExportSpecifier child: specifiers){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(source != null && processor.apply(source) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.specifiers != null){
               int index = this.specifiers.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ExportSpecifier> list = this.replaceInList(this.specifiers,index,newChild);
                   this.setSpecifiers(list);
                   return true;
               }
            }
            if(this.source == oldChild){
               this.setSource((io.nop.xlang.ast.Literal)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.specifiers != null){
               int index = this.specifiers.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ExportSpecifier> list = this.removeInList(this.specifiers,index);
                   this.setSpecifiers(list);
                   return true;
               }
            }
            if(this.source == child){
                this.setSource(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ExportNamedDeclaration other = (ExportNamedDeclaration)node;
    
            if(isListEquivalent(this.specifiers,other.getSpecifiers())){
               return false;
            }
            if(!isNodeEquivalent(this.source,other.getSource())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ExportNamedDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(specifiers != null){
                      
                              if(!specifiers.isEmpty())
                                json.put("specifiers", specifiers);
                          
                    }
                
                    if(source != null){
                      
                              json.put("source", source);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                specifiers = io.nop.api.core.util.FreezeHelper.freezeList(specifiers,cascade);         
                if(source != null)
                    source.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
