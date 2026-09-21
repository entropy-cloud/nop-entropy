package demo;

class Boundary {

    void multi(String raw) {
        try {
            step1();
        } catch (IllegalStateException e) {
        } catch (IllegalArgumentException e2) {
        }
        try {
            step2();
        } catch (Exception e) {
            try {
                step3();
            } catch (Error fatal) {
            }
        }
    }

}
