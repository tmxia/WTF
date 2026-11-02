package top.easelink.lcg.utils;

import android.text.TextUtils;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;






public class RegexUtils {

    @NonNull
    public static HashSet<String> extractInfoFrom(String content, String patternStr) {
        HashSet<String> urls = new HashSet<>();
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(patternStr)) {
            return urls;
        }
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }


}
