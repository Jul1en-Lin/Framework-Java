package com.lien.api.constants;

/**
 * 地图常量信息
 */
public class MapConstants {

    /**
     * 城市级别
     */
    public final static Integer CITY_LEVEL = 2;

    /**
     * 城市列表缓存 key
     */
    public final static String CACHE_MAP_CITY_KEY = "map:city:id";

    /**
     * 城市拼音缓存 key
     */
    public final static String CACHE_MAP_CITY_PINYIN_KEY = "map:city:pinyin";

    /**
     * 城市区划缓存 key
     */
    public final static  String CACHE_MAP_CITY_CHILDREN_KEY = "map:city:children:";

    /**
     * 热门城市缓存 key
     */
    public final static String CACHE_MAP_HOT_CITY = "map:city:hot";
}
