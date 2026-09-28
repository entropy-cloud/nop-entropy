package io.nop.code.flow;

import java.util.List;
import java.util.Set;

import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;

/**
 * Spring 入口点模式（原 FlowDetector 私有内部类 DefaultSpringEntryPointPatternProvider，
 * N5.2 迁出为可插拔顶层实现并经 IoC 装配）。
 */
public class SpringEntryPointPatternProvider implements IEntryPointPatternProvider {

    public static final Set<String> SPRING_ENTRY_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.jms.annotation.JmsListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.messaging.handler.annotation.MessageMapping",
            "org.springframework.context.event.EventListener",
            "org.springframework.boot.context.event.ApplicationReadyEvent",
            "org.springframework.web.bind.annotation.RequestMapping"
    );

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean isEntryPoint(CodeSymbol symbol) {
        // Annotation data is not available in the in-memory CodeSymbol model.
        // Instead, match Spring entry point patterns by checking the symbol's
        // qualified name against common Spring endpoint naming conventions.
        String qn = symbol.getQualifiedName();
        if (qn == null) {
            return false;
        }

        // Check if the symbol's class name suggests a Spring component
        String className = extractClassName(qn);
        if (className != null) {
            if (className.endsWith("Controller") || className.endsWith("RestController")
                    || className.endsWith("Endpoint") || className.endsWith("Listener")
                    || className.endsWith("Handler") || className.endsWith("Scheduler")
                    || className.endsWith("Consumer") || className.endsWith("Subscriber")) {
                // Methods in these classes are likely entry points
                CodeSymbolKind kind = symbol.getKind();
                if (kind == CodeSymbolKind.METHOD || kind == CodeSymbolKind.CONSTRUCTOR) {
                    return true;
                }
            }
        }

        // Check extData for annotation short names
        String extData = symbol.getExtData();
        if (extData != null) {
            List<String> annotations = ExtDataHelper.getAnnotations(extData);
            for (String annotation : SPRING_ENTRY_ANNOTATIONS) {
                String shortName = annotation.substring(annotation.lastIndexOf('.') + 1);
                if (annotations.contains(shortName) || annotations.contains(annotation)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String extractClassName(String qualifiedName) {
        // e.g., "com.example.controller.UserController.getMethod" -> "UserController"
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
        return List.copyOf(SPRING_ENTRY_ANNOTATIONS);
    }

    @Override
    public List<String> getNamePatterns() {
        return List.of("main", "handle*", "process*", "onEvent*");
    }
}
