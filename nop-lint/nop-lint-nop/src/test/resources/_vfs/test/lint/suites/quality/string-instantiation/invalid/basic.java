package demo;

class Bad {
    void run(byte[] bytes) {
        String a = new String();
        String b = new String("literal");
        String c = new String(bytes);
        use(a, b, c);
    }

    void use(String... parts) {
    }
}
