package demo;

class Bad {
    Process run(String command) throws java.io.IOException {
        return Runtime.getRuntime().exec(command);
    }
}
