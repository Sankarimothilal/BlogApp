package com.blogsphere.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlContentSanitizerTest {

    @Test
    void preservesUploadedWebpImageElements() {
        String sanitized = HtmlContentSanitizer.sanitize(
                "<p>Before<img src=\"/uploads/abc_123-image.webp\" alt=\"Map\"></p>");

        assertTrue(sanitized.contains("<img src=\"/uploads/abc_123-image.webp\" alt=\"Map\">"));
    }

    @Test
    void preservesAbsoluteUploadedWebpImageUrlsReturnedByMediaApi() {
        String sanitized = HtmlContentSanitizer.sanitize(
                "<img src=\"http://localhost:8080/uploads/abc_123-image.webp\" alt=\"Map\">");

        assertTrue(sanitized.contains("<img src=\"http://localhost:8080/uploads/abc_123-image.webp\" alt=\"Map\">"));
    }

    @Test
    void removesUnsafeImageUrls() {
        String sanitized = HtmlContentSanitizer.sanitize(
                "<img src=\"javascript:alert(1)\" alt=\"bad\">");

        assertTrue(!sanitized.contains("javascript:"));
    }
}
