package com.lien.common.core.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import java.util.List;

/**
 * 字符串工具类。
 */
public final class StringUtil {

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private StringUtil() {} // 私有化构造函数，防止实例化

    /**
     * 判断 URL 是否匹配指定的 Ant 路径规则。
     *
     * <p>例如 {@code *} 匹配同一级路径，{@code **} 匹配多级路径。</p>
     *
     * @param url  待匹配的 URL
     * @param rule Ant 路径规则
     * @return URL 和规则都非空白且完整匹配时返回 {@code true}
     */
    public static boolean matchesUrl(String url, String rule) {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(rule)) {
            return false;
        }
        return ANT_PATH_MATCHER.match(rule, url);
    }

    /**
     * 判断 URL 是否匹配指定规则列表中的任意一条规则。
     *
     * @param url   待匹配的 URL
     * @param rules Ant 路径规则链表表
     * @return 匹配任意一条非空白规则时返回 {@code true}
     */
    public static boolean matchesListUrl(String url, List<String> rules) {
        if (StringUtils.isBlank(url) || rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (matchesUrl(url, rule)) {
                return true;
            }
        }
        return false;
    }
}
