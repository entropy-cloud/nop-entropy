class AnnotationSuppressed {
    @SuppressWarnings("nop-lint:demo/no-suppress-demo")
    void m1() {
        System.out.println("one");
    }

    void m2() {
        System.out.println("two");
    }
}
