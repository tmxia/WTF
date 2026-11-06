package top.easelink.framework.customview.htmltextview;

import android.content.Context;
import android.text.Html;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.google.android.material.textview.MaterialTextView;

import java.io.InputStream;
import java.util.Scanner;

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
            // 1. 在解析前，把 <a> 标签全部干掉，保留纯文本，防止代码里的 http 变成红色下划线
            String sanitizedHtml = html.replaceAll("(?i)<a[^>]*>(.*?)</a>", "$1");

            // 2. 正常交给 HtmlFormatter 解析，内部的 HtmlTagHandler 会自动把代码块抽出来变成“点击显示代码”占位符
            CharSequence text = HtmlFormatter.formatHtml(
                    sanitizedHtml, imageGetter, clickablePreCodeSpan, drawPreCodeSpan, indent, removeTrailingWhiteSpace, getContext(), onImgTagClickListener, onLinkTagClickListener);

            SpannableStringBuilder builder = new SpannableStringBuilder(text);

            // 3. 终极兜底：把任何可能漏网的超链接下划线给物理粉碎
            URLSpan[] urlSpans = builder.getSpans(0, builder.length(), URLSpan.class);
            for (URLSpan urlSpan : urlSpans) {
                builder.removeSpan(urlSpan);
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
