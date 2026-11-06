package top.easelink.framework.customview.htmltextview;

import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.Nullable;

import org.xml.sax.XMLReader;

import java.util.Stack;

public class HtmlTagHandler implements Html.TagHandler {

    public static final String UNORDERED_LIST = "HTML_TEXTVIEW_ESCAPED_UL_TAG";
    public static final String ORDERED_LIST = "HTML_TEXTVIEW_ESCAPED_OL_TAG";
    public static final String LIST_ITEM = "HTML_TEXTVIEW_ESCAPED_LI_TAG";

    private Stack<String> lists = new Stack<>();
    private Stack<Integer> olNextIndex = new Stack<>();
    private static int userGivenIndent = -1;
    private static final int defaultIndent = 10;
    private static final int defaultListItemIndent = defaultIndent * 2;
    private static final BulletSpan defaultBullet = new BulletSpan(defaultIndent);

    private ClickablePreCodeSpan clickablePreCodeSpan;
    private DrawPreCodeSpan drawPreCodeSpan;

    private static class Ul {}
    private static class Ol {}
    private static class Code {}
    private static class Center {}
    private static class Strike {}
    private static class Tr {}
    private static class Th {}
    private static class Td {}

    String overrideTags(@Nullable String html) {
        if (html == null) return null;
        html = html.replace("<ul", "<" + UNORDERED_LIST);
        html = html.replace("</ul>", "</" + UNORDERED_LIST + ">");
        html = html.replace("<ol", "<" + ORDERED_LIST);
        html = html.replace("</ol>", "</" + ORDERED_LIST + ">");
        html = html.replace("<li", "<" + LIST_ITEM);
        html = html.replace("</li>", "</" + LIST_ITEM + ">");
        return html;
    }

    @Override
    public void handleTag(final boolean opening, final String tag, Editable output, final XMLReader xmlReader) {
        if (tag.equalsIgnoreCase("pre")) return; // pre 已在外层处理

        if (opening) {
            if (tag.equalsIgnoreCase(UNORDERED_LIST)) {
                lists.push(tag);
            } else if (tag.equalsIgnoreCase(ORDERED_LIST)) {
                lists.push(tag);
                olNextIndex.push(1);
            } else if (tag.equalsIgnoreCase(LIST_ITEM)) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') output.append("\n");
                if (!lists.isEmpty()) {
                    String parentList = lists.peek();
                    if (parentList.equalsIgnoreCase(ORDERED_LIST)) {
                        start(output, new Ol());
                        olNextIndex.push(olNextIndex.pop() + 1);
                    } else if (parentList.equalsIgnoreCase(UNORDERED_LIST)) {
                        start(output, new Ul());
                    }
                }
            } else if (tag.equalsIgnoreCase("code")) {
                start(output, new Code());
            } else if (tag.equalsIgnoreCase("center")) {
                start(output, new Center());
            } else if (tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                start(output, new Strike());
            } else if (tag.equalsIgnoreCase("tr")) {
                start(output, new Tr());
            } else if (tag.equalsIgnoreCase("th")) {
                start(output, new Th());
            } else if (tag.equalsIgnoreCase("td")) {
                start(output, new Td());
            }
        } else {
            if (tag.equalsIgnoreCase(UNORDERED_LIST)) {
                lists.pop();
            } else if (tag.equalsIgnoreCase(ORDERED_LIST)) {
                lists.pop();
                olNextIndex.pop();
            } else if (tag.equalsIgnoreCase(LIST_ITEM)) {
                if (!lists.isEmpty()) {
                    int listItemIndent = (userGivenIndent > -1) ? (userGivenIndent * 2) : defaultListItemIndent;
                    if (lists.peek().equalsIgnoreCase(UNORDERED_LIST)) {
                        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') output.append("\n");
                        int indent = (userGivenIndent > -1) ? userGivenIndent : defaultIndent;
                        BulletSpan bullet = (userGivenIndent > -1) ? new BulletSpan(userGivenIndent) : defaultBullet;
                        if (lists.size() > 1) {
                            indent = indent - bullet.getLeadingMargin(true);
                            if (lists.size() > 2) indent -= (lists.size() - 2) * listItemIndent;
                        }
                        end(output, Ul.class, false, new LeadingMarginSpan.Standard(listItemIndent * (lists.size() - 1)), new BulletSpan(indent));
                    } else if (lists.peek().equalsIgnoreCase(ORDERED_LIST)) {
                        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') output.append("\n");
                        int indent = (userGivenIndent > -1) ? userGivenIndent : defaultIndent;
                        NumberSpan span = new NumberSpan(indent, olNextIndex.lastElement() - 1);
                        if (lists.size() > 1) {
                            indent = indent - span.getLeadingMargin(true);
                            if (lists.size() > 2) indent -= (lists.size() - 2) * listItemIndent;
                        }
                        end(output, Ol.class, false, new LeadingMarginSpan.Standard(listItemIndent * (lists.size() - 1)), new NumberSpan(indent, olNextIndex.lastElement() - 1));
                    }
                }
            } else if (tag.equalsIgnoreCase("code")) {
                end(output, Code.class, false, new TypefaceSpan("monospace"));
            } else if (tag.equalsIgnoreCase("center")) {
                end(output, Center.class, true, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
            } else if (tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                end(output, Strike.class, false, new StrikethroughSpan());
            } else if (tag.equalsIgnoreCase("tr")) {
                end(output, Tr.class, false);
            } else if (tag.equalsIgnoreCase("th")) {
                end(output, Th.class, false);
            } else if (tag.equalsIgnoreCase("td")) {
                end(output, Td.class, false);
            }
        }
    }

    private void start(Editable output, Object mark) {
        int len = output.length();
        output.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK);
    }

    private void end(Editable output, Class kind, boolean paragraphStyle, Object... replaces) {
        Object obj = getLast(output, kind);
        if (obj == null) return;
        int where = output.getSpanStart(obj);
        int len = output.length();
        output.removeSpan(obj);

        if (where != len) {
            int thisLen = len;
            if (paragraphStyle) {
                output.append("\n");
                thisLen++;
            }
            for (Object replace : replaces) {
                if (replace != null) {
                    output.setSpan(replace, where, thisLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    private static Object getLast(Editable text, Class kind) {
        Object[] objs = text.getSpans(0, text.length(), kind);
        if (objs.length == 0) return null;
        for (int i = objs.length; i > 0; i--) {
            if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) return objs[i - 1];
        }
        return null;
    }

    public void setListIndentPx(float px) { userGivenIndent = Math.round(px); }
    public void setClickablePreCodeSpan(ClickablePreCodeSpan clickablePreCodeSpan) { this.clickablePreCodeSpan = clickablePreCodeSpan; }
    public void setDrawPreCodeSpan(DrawPreCodeSpan drawPreCodeSpan) { this.drawPreCodeSpan = drawPreCodeSpan; }
}
