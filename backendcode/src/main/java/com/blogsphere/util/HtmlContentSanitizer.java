package com.blogsphere.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/** Sanitizes rich blog content before it is stored or returned to a renderer. */
public final class HtmlContentSanitizer {

    private static final Safelist BLOG_CONTENT = new Safelist() {
        @Override
        public boolean isSafeAttribute(String tagName, Element element, Attribute attribute) {
            String value = attribute.getValue();
            if ("img".equals(tagName) && "src".equals(attribute.getKey())
                    && value.matches("/uploads/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)")) {
                return true;
            }
            if ("a".equals(tagName) && "href".equals(attribute.getKey())
                    && value.matches("^(?:/(?!/)|\\.{1,2}/|#|\\?)[^\\\\]*$")) {
                return true;
            }
            return super.isSafeAttribute(tagName, element, attribute);
        }
    }
            .addTags("a", "b", "blockquote", "br", "code", "div", "em", "h1", "h2", "h3",
                    "h4", "h5", "h6", "hr", "i", "img", "li", "ol", "p", "pre", "s",
                    "span", "strong", "sub", "sup", "u", "ul")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private HtmlContentSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, "", BLOG_CONTENT);
    }
}
