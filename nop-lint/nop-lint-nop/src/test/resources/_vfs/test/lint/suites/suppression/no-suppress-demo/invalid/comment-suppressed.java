class CommentSuppressed {
    void m() {
        // nop-lint-disable-next-line demo/no-suppress-demo
        System.out.println("one");
        System.out.println("two");
    }
}
