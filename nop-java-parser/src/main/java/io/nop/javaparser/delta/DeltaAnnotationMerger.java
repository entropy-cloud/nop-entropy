package io.nop.javaparser.delta;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;

import java.util.Map;
import java.util.stream.Collectors;

public class DeltaAnnotationMerger {
    public static final DeltaAnnotationMerger INSTANCE = new DeltaAnnotationMerger();

    public AnnotationDeclaration merge(AnnotationDeclaration a, AnnotationDeclaration b) {
        AnnotationDeclaration merged = new AnnotationDeclaration();
        merged.setName(a.getNameAsString());

        // 合并类级别声明（注释、注解、修饰符等）
        DeltaMergeHelper.mergeComment(merged, a, b);
        DeltaMergeHelper.mergeAnnotations(merged, a, b);
        merged.setModifiers(a.getModifiers());
        b.getModifiers().forEach(mod -> merged.addModifier(mod.getKeyword()));

        // 合并注解成员
        mergeMembers(merged, a, b);

        return merged;
    }

    private void mergeMembers(AnnotationDeclaration merged,
                              AnnotationDeclaration a,
                              AnnotationDeclaration b) {
        Map<String, AnnotationMemberDeclaration> aMembers = getMembersByName(a);
        Map<String, AnnotationMemberDeclaration> bMembers = getMembersByName(b);

        // 合并策略：同名成员使用B的版本
        aMembers.forEach((name, member) -> {
            if (!bMembers.containsKey(name)) {
                merged.addMember(member.clone());
            }
        });

        // 添加所有B的成员（覆盖A的同名成员）
        bMembers.values().forEach(member -> {
            merged.addMember(member.clone());
        });
    }

    private Map<String, AnnotationMemberDeclaration> getMembersByName(AnnotationDeclaration annotation) {
        return annotation.getMembers().stream()
                .filter(m -> m instanceof AnnotationMemberDeclaration)
                .collect(Collectors.toMap(
                        m -> ((AnnotationMemberDeclaration) m).getNameAsString(),
                        m -> (AnnotationMemberDeclaration) m
                ));
    }
}