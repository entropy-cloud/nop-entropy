package demo;

import java.util.List;

class Bad {
    int total(List<String> rows) {
        int sum = 0;
        for (int i = 0; i < rows.size(); i++) {
            sum += rows.get(i).length();
        }
        return sum;
    }
}
