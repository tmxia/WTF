/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@schuermann.eu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.easelink.framework.customview.htmltextview;

import android.content.Context;
import android.text.Html;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.google.android.material.textview.MaterialTextView;

import java.io.InputStream;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;
import top.easelink.framework.R;

public class HtmlTextView extends MaterialTextView {

    public static final String TAG = "HtmlTextView";
    public static final boolean DEBUG = false;

    @Nullable
    private ClickablePreCodeSpan clickablePreCodeSpan;
    @Nullable
    private DrawPreCodeSpan drawPreCodeSpan;
    private OnImgTagClickListener onImgTagClickListener;
    private OnLinkTagClickListener onLinkTagClickListener;
    private float indent = 24.0f; // Default to 24px.
    private boolean removeTrailingWhiteSpace = true;

    public HtmlTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public HtmlTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HtmlTextView(Context context) {
        super(context);
    }

    public void setHtml(@RawRes int resId) {
        setHtml(resId, null);
    }

    public void setHtml(@NonNull String html) {
        setHtml(html, null);
    }

    public void setHtml(@RawRes int resId, @Nullable Html.ImageGetter imageGetter) {
        InputStream inputStreamText = getContext().getResources().openRawResource(resId);
        setHtml(convertStreamToString(inputStreamText), imageGetter);
    }

    public void setHtml(@NonNull String html, @Nullable Html.ImageGetter imageGetter) {
        try {
            CharSequence text = HtmlFormatter.formatHtml(
                    html,
                    imageGetter,
                    clickablePreCodeSpan,
                    drawPreCodeSpan,
                    indent,
                    removeTrailingWhiteSpace,
                    getContext(),
                    onImgTagClickListener,
                    onLinkTagClickListener);

            setText(text);
            
            // make links work
            setMovementMethod(LocalLinkMovementMethod.getInstance());

            // ================= 新增：原生代码语法高亮 =================
            if (text instanceof Spannable) {
                highlightCode((Spannable) text);
            }
            // =========================================================

        } catch (Exception e) {
            Timber.e(e);
            setText(R.string.html_general_error);
        }
    }

    // ================= 核心高亮实现 =================
    private void highlightCode(Spannable spannable) {
        ClickablePreCodeSpan[] spans = spannable.getSpans(0, spannable.length(), ClickablePreCodeSpan.class);
        for (ClickablePreCodeSpan span : spans) {
            int start = spannable.getSpanStart(span);
            int end = spannable.getSpanEnd(span);
            if (start >= 0 && end <= spannable.length()) {
                String code = spannable.subSequence(start, end).toString();
                applyHighlight(spannable, start, code);
            }
        }
    }

    private void applyHighlight(Spannable spannable, int start, String code) {
        // 现代 IDE 配色方案（类似 VSCode 深色主题）
        int keywordColor = android.graphics.Color.parseColor("#569CD6"); // 蓝色
        int stringColor = android.graphics.Color.parseColor("#CE9178"); // 橙红色
        int commentColor = android.graphics.Color.parseColor("#6A9955"); // 绿色
        int numberColor = android.graphics.Color.parseColor("#B5CEA8"); // 浅绿色

        // 1. 匹配关键字
        String[] keywords = {"abstract","actual","annotation","as","assert","associate","async","await","break","by","catch","class","companion","const","constructor","continue","crossinline","data","delegate","delete","do","dynamic","else","enum","expect","external","false","field","file","final","finally","fixed","for","fun","get","if","import","in","infix","init","inline","inner","instanceof","interface","internal","is","lambda","lateinit","let","local","null","object","open","operator","out","override","package","param","private","property","protected","public","receiver","reified","return","sealed","set","super","suspend","tailrec","this","throw","true","try","typealias","typeof","val","value","var","vararg","when","where","while","with",
                "void","int","char","float","double","boolean","long","short","byte","String","new","static","final"};
        StringBuilder kwRegex = new StringBuilder("\\b(");
        for (int i = 0; i < keywords.length; i++) {
            kwRegex.append(keywords[i]);
            if (i < keywords.length - 1) kwRegex.append("|");
        }
        kwRegex.append(")\\b");

        Pattern kwPattern = Pattern.compile(kwRegex.toString());
        Matcher kwMatcher = kwPattern.matcher(code);
        while (kwMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(keywordColor),
                    start + kwMatcher.start(), start + kwMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2. 匹配字符串
        Pattern strPattern = Pattern.compile("(\".*?\"|'.*?')");
        Matcher strMatcher = strPattern.matcher(code);
        while (strMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(stringColor),
                    start + strMatcher.start(), start + strMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 3. 匹配注释 (覆盖前面可能的高亮)
        Pattern commentPattern = Pattern.compile("(//.*|/\\*[\\s\\S]*?\\*/)");
        Matcher commentMatcher = commentPattern.matcher(code);
        while (commentMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(commentColor),
                    start + commentMatcher.start(), start + commentMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 4. 匹配数字
        Pattern numPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
        Matcher numMatcher = numPattern.matcher(code);
        while (numMatcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(numberColor),
                    start + numMatcher.start(), start + numMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    // ===============================================

    public void setRemoveTrailingWhiteSpace(boolean removeTrailingWhiteSpace) {
        this.removeTrailingWhiteSpace = removeTrailingWhiteSpace;
    }

    @Deprecated()
    public void setRemoveFromHtmlSpace(boolean removeFromHtmlSpace) {
        this.removeTrailingWhiteSpace = removeFromHtmlSpace;
    }

    public void setClickablePreCodeSpan(@Nullable ClickablePreCodeSpan clickablePreCodeSpan) {
        this.clickablePreCodeSpan = clickablePreCodeSpan;
    }

    public void setDrawPreCodeSpan(@Nullable DrawPreCodeSpan drawPreCodeSpan) {
        this.drawPreCodeSpan = drawPreCodeSpan;
    }

    public void setImageTagClickListener(OnImgTagClickListener onImgTagClickListener) {
        this.onImgTagClickListener = onImgTagClickListener;
    }

    public void setOnLinkTagClickListener(OnLinkTagClickListener onLinkTagClickListener) {
        this.onLinkTagClickListener = onLinkTagClickListener;
    }

    public void setListIndentPx(float px) {
        this.indent = px;
    }

    @NonNull
    private static String convertStreamToString(@NonNull InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        int startSelection = getSelectionStart();
        int endSelection = getSelectionEnd();
        if (startSelection < 0 || endSelection < 0) {
            Selection.setSelection((Spannable) getText(), getText().length());
        } else if (startSelection != endSelection) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                final CharSequence text = getText();
                setText(null);
                setText(text);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getMovementMethod() instanceof LocalLinkMovementMethod) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_DOWN) {
                return ((LocalLinkMovementMethod) getMovementMethod()).onTouchEvent(this, (Spannable) getText(), event);
            }
        }
        return false;
    }
}
