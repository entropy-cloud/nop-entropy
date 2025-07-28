package io.nop.javaparser.delta;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.nop.commons.util.StringHelper;

import java.util.stream.Collectors;

public class DeltaMergeHelper {

    public static void mergeComment(BodyDeclaration<?> merged,
                                    BodyDeclaration<?> a,
                                    BodyDeclaration<?> b) {
        // 合并注释 - b优先
        b.getComment().ifPresent(comment -> merged.setComment(comment.clone()));
        if (!merged.getComment().isPresent()) {
            a.getComment().ifPresent(comment -> merged.setComment(comment.clone()));
        }
    }

    public static void mergeAnnotations(BodyDeclaration<?> merged,
                                        BodyDeclaration<?> a,
                                        BodyDeclaration<?> b) {
        // 先添加a的所有注解
        a.getAnnotations().forEach(annotation -> {
            merged.addAnnotation(annotation.clone());
        });

        // 然后添加或覆盖b的注解
        b.getAnnotations().forEach(bAnnotation -> {
            // 检查是否已存在同名注解
            boolean exists = merged.getAnnotations().stream()
                    .anyMatch(aAnnotation ->
                            aAnnotation.getNameAsString().equals(bAnnotation.getNameAsString()));

            if (exists) {
                // 移除原有注解，添加新的注解
                merged.getAnnotations().removeIf(aAnnotation ->
                        aAnnotation.getNameAsString().equals(bAnnotation.getNameAsString()));
            }
            merged.addAnnotation(bAnnotation.clone());
        });
    }

    public static void mergeImplementedTypes(BodyDeclaration<?> merged,
                                             NodeList<ClassOrInterfaceType> aTypes,
                                             NodeList<ClassOrInterfaceType> bTypes) {
        NodeList<ClassOrInterfaceType> implementedTypes = new NodeList<>();
        implementedTypes.addAll(aTypes);
        implementedTypes.addAll(bTypes);

        if (merged instanceof ClassOrInterfaceDeclaration) {
            ((ClassOrInterfaceDeclaration) merged).setImplementedTypes(implementedTypes);
        } else if (merged instanceof EnumDeclaration) {
            ((EnumDeclaration) merged).setImplementedTypes(implementedTypes);
        }
    }

    public static String getMethodSignature(MethodDeclaration method) {
        return method.getNameAsString() + "(" +
                method.getParameters().stream()
                        .map(p -> StringHelper.simpleClassName(p.getType().asString()))
                        .collect(Collectors.joining(",")) + ")";
    }

    public static boolean hasNonEmptyComment(BodyDeclaration<?> declaration) {
        return declaration.getComment().isPresent() &&
                !declaration.getComment().get().getContent().trim().isEmpty();
    }

    // 在DeltaMergeHelper中添加方法
    public static String getAnnotationMemberSignature(AnnotationMemberDeclaration member) {
        return member.getNameAsString() + ":" + member.getType().asString();
    }
}