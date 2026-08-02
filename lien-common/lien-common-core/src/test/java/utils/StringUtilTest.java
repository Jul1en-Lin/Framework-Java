package utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilTest {

    @Test
    void shouldMatchUrlAgainstAntPathRule() {
        assertTrue(StringUtil.matchesUrlRule(
                "https://example.com/api/users/123",
                "https://example.com/api/users/*"));
        assertFalse(StringUtil.matchesUrlRule(
                "https://example.com/api/orders/123",
                "https://example.com/api/users/*"));
    }

    @Test
    void shouldReturnFalseWhenUrlOrRuleIsBlank() {
        assertFalse(StringUtil.matchesUrlRule(null, "^https://example\\.com$"));
        assertFalse(StringUtil.matchesUrlRule("  ", "^https://example\\.com$"));
        assertFalse(StringUtil.matchesUrlRule("https://example.com", null));
        assertFalse(StringUtil.matchesUrlRule("https://example.com", "  "));
    }

    @Test
    void shouldMatchUrlAgainstAnyRuleInTheList() {
        List<String> rules = List.of(
                "https://example.com/api/users/*",
                "https://example.com/api/orders/**");

        assertTrue(StringUtil.matchesAnyUrlRule("https://example.com/api/orders/456", rules));
        assertFalse(StringUtil.matchesAnyUrlRule("https://example.com/api/products/456", rules));
    }

    @Test
    void shouldReturnFalseWhenRulesAreNullOrEmpty() {
        assertFalse(StringUtil.matchesAnyUrlRule("https://example.com", null));
        assertFalse(StringUtil.matchesAnyUrlRule("https://example.com", List.of()));
    }
}
