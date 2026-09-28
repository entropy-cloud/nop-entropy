package io.nop.code.flow;

import java.util.List;

import io.nop.code.core.framework.FrameworkPatternModel;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;

/**
 * N5.3: DSL 驱动的入口点模式。isEntryPoint 语义与 {@link SpringEntryPointPatternProvider}
 * 完全一致（类名后缀 + METHOD/CONSTRUCTOR + 注解 FQN/短名）；namePatterns 为声明性元数据，
 * 仅经 getNamePatterns() 暴露，不参与判定。
 */
public class ConfigDrivenEntryPointPatternProvider implements IEntryPointPatternProvider {

    private final FrameworkPatternModel model;

    public ConfigDrivenEntryPointPatternProvider(FrameworkPatternModel model) {
        this.model = model;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean isEntryPoint(CodeSymbol symbol) {
        String qn = symbol.getQualifiedName();
        if (qn == null) {
            return false;
        }

        String className = extractClassName(qn);
        if (className != null) {
            for (String suffix : model.getClassSuffixes()) {
                if (className.endsWith(suffix)) {
                    CodeSymbolKind kind = symbol.getKind();
                    if (kind == CodeSymbolKind.METHOD || kind == CodeSymbolKind.CONSTRUCTOR) {
                        return true;
                    }
                    break;
                }
            }
        }

        String extData = symbol.getExtData();
        if (extData != null) {
            List<String> annotations = ExtDataHelper.getAnnotations(extData);
            for (String annotationFqn : model.getAnnotationFqns()) {
                String shortName = annotationFqn.substring(annotationFqn.lastIndexOf('.') + 1);
                if (annotations.contains(shortName) || annotations.contains(annotationFqn)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String extractClassName(String qualifiedName) {
        int parenIdx = qualifiedName.indexOf('(');
        String withoutParams = parenIdx > 0 ? qualifiedName.substring(0, parenIdx) : qualifiedName;
        int lastDot = withoutParams.lastIndexOf('.');
        if (lastDot < 0) return null;
        String beforeMethod = withoutParams.substring(0, lastDot);
        int prevDot = beforeMethod.lastIndexOf('.');
        return prevDot >= 0 ? beforeMethod.substring(prevDot + 1) : beforeMethod;
    }

    @Override
    public List<String> getAnnotationPatterns() {
        return List.copyOf(model.getAnnotationFqns());
    }

    @Override
    public List<String> getNamePatterns() {
        return List.copyOf(model.getNamePatterns());
    }
}
