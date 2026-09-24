package demo;

import java.util.List;

class Clean {
    int total(List<String> rows) {
        int sum = 0;
        for (String row : rows) {
            sum += row.length();
        }
        return sum;
    }

    int byIndex(String[] rows) {
        int sum = 0;
        for (int i = 0; i < rows.length; i++) {
            sum += rows[i].length();
        }
        return sum;
    }
}
