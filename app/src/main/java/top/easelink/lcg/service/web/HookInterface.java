package top.easelink.lcg.service.web;

import android.webkit.JavascriptInterface;








public interface HookInterface {

    @JavascriptInterface
    void processHtml(String html);
}
