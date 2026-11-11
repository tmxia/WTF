package top.easelink.framework.customview.htmltextview;

import android.content.Context;
import android.text.Html;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.google.android.material.textview.MaterialTextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;
import top.easelink.framework.R;

public class HtmlTextView extends MaterialTextView {

    public static final String TAG = "HtmlTextView";
    public static final boolean DEBUG = false;

    @Nullable private ClickablePreCodeSpan clickablePreCodeSpan;
    @Nullable private DrawPreCodeSpan drawPreCodeSpan;
    private OnImgTagClickListener onImgTagClickListener;
    private OnLinkTagClickListener onLinkTagClickListener;
    private float indent = 24.0f;
    private boolean removeTrailingWhiteSpace = true;

    // 固定占位符，非常醒目
    private static final String CODE_PLACEHOLDER = "👉 [点击查看代码] 👈";

    public HtmlTextView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }
    public HtmlTextView(Context context, AttributeSet attrs) { super(context, attrs); }
    public HtmlTextView(Context context) { super(context); }

    public void setHtml(@RawRes int resId) { setHtml(resId, null); }
    public void setHtml(@NonNull String html) { setHtml(html, null); }
    public void setHtml(@RawRes int resId, @Nullable Html.ImageGetter imageGetter) {
        InputStream inputStreamText = getContext().getResources().openRawResource(resId);
        setHtml(convertStreamToString(inputStreamText), imageGetter);
    }

    public void setHtml(@NonNull String html, @Nullable Html.ImageGetter imageGetter) {
        try {
            List<String> codeBlocks = new ArrayList<>();
            
            // 1. 匹配并提取代码块
            Pattern pattern = Pattern.compile("(<pre[^>]*>([\\s\\S]*?)</pre>)|(<div[^>]*class=\"[^\"]*(?:blockcode|code)[^\"]*\"[^>]*>([\\s\\S]*?)</div>(?:\\s*</div>)?)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            StringBuffer sb = new StringBuffer();
            int index = 0;

            while (matcher.find()) {
                String rawCode = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
                if (rawCode == null) rawCode = "";

                // 提取并清理代码内容（用于弹窗展示）
                rawCode = rawCode.replaceAll("(?i)<br\\s*/?>", "\n");
                rawCode = rawCode.replaceAll("(?i)<li[^>]*>", "\n");
                rawCode = rawCode.replaceAll("(?i)</li>", "");
                rawCode = rawCode.replaceAll("<[^>]*>", "");
                rawCode = rawCode.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
                rawCode = rawCode.trim();

                codeBlocks.add(rawCode);
                // 在正文中原位替换为一段醒目的文本
                matcher.appendReplacement(sb, Matcher.quoteReplacement("\n" + CODE_PLACEHOLDER + "\n"));
                index++;
            }
            matcher.appendTail(sb);

            // 2. 交给原生解析
            String sanitizedHtml = sb.toString();
            CharSequence text = HtmlFormatter.formatHtml(
                    sanitizedHtml, imageGetter, clickablePreCodeSpan, drawPreCodeSpan, indent, removeTrailingWhiteSpace, getContext(), onImgTagClickListener, onLinkTagClickListener);

            SpannableStringBuilder builder = new SpannableStringBuilder(text);

            // 核心修复：遍历所有的超链接（URLSpan），给它们赋予 WTF 的主题红和下划线
            URLSpan[] urlSpans = builder.getSpans(0, builder.length(), URLSpan.class);
            for (URLSpan span : urlSpans) {
                int start = builder.getSpanStart(span);
                int end = builder.getSpanEnd(span);
                int flags = builder.getSpanFlags(span);
                
                // 加上 WTF 标志性的红色 (#E53935)
                builder.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor("#E53935")), start, end, flags);
                // 加上下划线
                builder.setSpan(new UnderlineSpan(), start, end, flags);
            }

            // 3. 给占位符挂载点击事件
            int searchIndex = 0;
            int blockIndex = 0;
            while (true) {
                int start = builder.toString().indexOf(CODE_PLACEHOLDER, searchIndex);
                if (start == -1) break;
                int end = start + CODE_PLACEHOLDER.length();

                // 样式设计：浅蓝背景 + 蓝色文字 + 下划线
                builder.setSpan(new BackgroundColorSpan(android.graphics.Color.parseColor("#E6F2FF")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(android.graphics.Color.parseColor("#007AFF")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // 挂载点击事件（触发弹窗）
                if (clickablePreCodeSpan != null && blockIndex < codeBlocks.size()) {
                    ClickablePreCodeSpan copySpan = clickablePreCodeSpan.newInstance();
                    copySpan.setHtml(codeBlocks.get(blockIndex));
                    builder.setSpan(copySpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                searchIndex = end;
                blockIndex++;
            }

            setText(builder);
            setMovementMethod(LocalLinkMovementMethod.getInstance());

        } catch (Exception e) {
            Timber.e(e);
            setText(R.string.html_general_error);
        }
    }

    public void setRemoveTrailingWhiteSpace(boolean removeTrailingWhiteSpace) { this.removeTrailingWhiteSpace = removeTrailingWhiteSpace; }
    @Deprecated() public void setRemoveFromHtmlSpace(boolean removeFromHtmlSpace) { this.removeTrailingWhiteSpace = removeFromHtmlSpace; }
    public void setClickablePreCodeSpan(@Nullable ClickablePreCodeSpan clickablePreCodeSpan) { this.clickablePreCodeSpan = clickablePreCodeSpan; }
    public void setDrawPreCodeSpan(@Nullable DrawPreCodeSpan drawPreCodeSpan) { this.drawPreCodeSpan = drawPreCodeSpan; }
    public void setImageTagClickListener(OnImgTagClickListener onImgTagClickListener) { this.onImgTagClickListener = onImgTagClickListener; }
    public void setOnLinkTagClickListener(OnLinkTagClickListener onLinkTagClickListener) { this.onLinkTagClickListener = onLinkTagClickListener; }
    public void setListIndentPx(float px) { this.indent = px; }

    @NonNull private static String convertStreamToString(@NonNull InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Override public boolean dispatchTouchEvent(final MotionEvent event) {
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

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getMovementMethod() instanceof LocalLinkMovementMethod) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_DOWN) {
                return ((LocalLinkMovementMethod) getMovementMethod()).onTouchEvent(this, (Spannable) getText(), event);
            }
        }
        return false;
    }
}
