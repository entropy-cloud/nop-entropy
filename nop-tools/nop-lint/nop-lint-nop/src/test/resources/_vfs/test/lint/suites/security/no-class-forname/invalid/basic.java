package demo;

class Bad {
    Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
}
