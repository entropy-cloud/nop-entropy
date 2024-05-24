package io.nop.rpa.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class TranslateDoc {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            page.navigate("https://www.baidu.com");
            page.getByLabel("百度").click();
            browser.close();
        }
    }

    void runTask(Page page) {

    }
}
