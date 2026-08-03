package utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilTest {

    @Test
    void shouldMatchUrlAgainstAntPathRule() {
        assertTrue(StringUtil.matchesUrl(
                "https://example.com/api/users/123",
                "https://example.com/api/users/*"));
        assertFalse(StringUtil.matchesUrl(
                "https://example.com/api/orders/123",
                "https://example.com/api/users/*"));
    }

    @Test
    void shouldReturnFalseWhenUrlOrRuleIsBlank() {
        assertFalse(StringUtil.matchesUrl(null, "https://example.com"));
        assertFalse(StringUtil.matchesUrl("  ", "https://example.com"));
        assertFalse(StringUtil.matchesUrl("https://example.com", null));
        assertFalse(StringUtil.matchesUrl("https://example.com", "  "));
    }

    @Test
    void shouldMatchUrlAgainstAnyRuleInTheList() {
        List<String> rules = List.of(
                "https://example.com/api/users/*",
                "https://example.com/api/orders/**");

        assertTrue(StringUtil.matchesListUrl("https://example.com/api/orders/456", rules));
        assertFalse(StringUtil.matchesListUrl("https://example.com/api/products/456", rules));
    }

    @Test
    void shouldReturnFalseWhenRulesAreNullOrEmpty() {
        assertFalse(StringUtil.matchesListUrl("https://example.com", null));
        assertFalse(StringUtil.matchesListUrl("https://example.com", List.of()));
    }
}
