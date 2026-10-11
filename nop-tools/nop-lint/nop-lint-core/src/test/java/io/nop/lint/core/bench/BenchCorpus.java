package io.nop.lint.core.bench;

/**
 * Deterministic benchmark corpus: one representative Java compilation unit
 * with matchable occurrences of every baseline pattern (raw exception throw,
 * chained dao call, class declaration). Embedded as a string so benchmark
 * runs never depend on filesystem state; multi-file scenarios replicate it.
 */
public final class BenchCorpus {

    private BenchCorpus() {
    }

    static final String JAVA_SOURCE = """
            package demo.bench;

            import java.util.ArrayList;
            import java.util.List;

            public class OrderService extends BaseService {
                private final List<Order> orders = new ArrayList<>();

                public Order find(long id) {
                    for (Order order : orders) {
                        if (order.id() == id) {
                            return order;
                        }
                    }
                    throw new RuntimeException("order not found: " + id);
                }

                public void validate(Order order) {
                    if (order == null) {
                        throw new RuntimeException("null order");
                    }
                    if (order.total() < 0) {
                        throw new IllegalArgumentException("negative total");
                    }
                }

                public void persist(Order order) {
                    validate(order);
                    this.dao().save(order);
                    dao().save(order);
                }

                public List<Order> findRecent(int limit) {
                    List<Order> result = new ArrayList<>();
                    for (int i = orders.size() - 1; i >= 0 && result.size() < limit; i--) {
                        result.add(orders.get(i));
                    }
                    return result;
                }

                public int countByStatus(String status) {
                    int count = 0;
                    for (Order order : orders) {
                        if (status.equals(order.status())) {
                            count++;
                        }
                    }
                    return count;
                }
            }
            """;

    static String[] replicated(int copies) {
        String[] sources = new String[copies];
        for (int i = 0; i < copies; i++) {
            sources[i] = JAVA_SOURCE;
        }
        return sources;
    }

    /**
     * Deterministic ~2000-line Java compilation unit (120 generated order
     * classes, each with comment lines the suppression tail must scan and
     * matchable throws): the large-corpus face that makes per-node hot-path
     * allocations observable (plan 08 Phase 1).
     */
    static final String JAVA_SOURCE_LARGE = buildJavaLarge();

    /**
     * Deterministic large XNode document (~800 mixed-case tags + comments):
     * the XML facade face where the suppression tail's comment-kind check
     * meets mixed-case kind names (plan 08 Phase 1).
     */
    static final String XML_SOURCE_LARGE = buildXmlLarge();

    private static String buildJavaLarge() {
        StringBuilder sb = new StringBuilder(128 * 1024);
        sb.append("package demo.bench.large;\n\n");
        for (int c = 0; c < 120; c++) {
            sb.append("public class OrderService").append(c).append(" extends BaseService {\n");
            sb.append("    private final java.util.List<Order> orders = new java.util.ArrayList<>();\n\n");
            sb.append("    // find the order by id; throws when missing\n");
            sb.append("    public Order find").append(c).append("(long id) {\n");
            sb.append("        /* linear scan over the local shard */\n");
            sb.append("        for (Order order : orders) {\n");
            sb.append("            if (order.id() == id) {\n");
            sb.append("                return order;\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("        System.out.println(\"miss ").append(c).append("\");\n");
            sb.append("        throw new RuntimeException(\"order not found: \" + id);\n");
            sb.append("    }\n\n");
            sb.append("    // persist through the dao gateway\n");
            sb.append("    public void persist").append(c).append("(Order order) {\n");
            sb.append("        dao().save(order);\n");
            sb.append("    }\n}\n\n");
        }
        return sb.toString();
    }

    private static String buildXmlLarge() {
        StringBuilder sb = new StringBuilder(96 * 1024);
        sb.append("<Catalog>\n");
        for (int i = 0; i < 400; i++) {
            sb.append("  <!-- entity ").append(i).append(" mapping -->\n");
            sb.append("  <Entity name=\"Order").append(i).append("\" tableName=\"demo_order_").append(i).append("\">\n");
            sb.append("    <Fields>\n");
            sb.append("      <Field name=\"id\" type=\"Long\"/>\n");
            sb.append("      <Field name=\"total\" type=\"Double\"/>\n");
            sb.append("    </Fields>\n");
            sb.append("    <Auth action=\"query\" role=\"admin\"/>\n");
            sb.append("  </Entity>\n");
        }
        sb.append("</Catalog>\n");
        return sb.toString();
    }
}
