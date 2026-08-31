package com.lien.common.core.utils;

import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.lien.common.core.utils.JsonUtil.string2Obj;


public class BeanUtil extends BeanUtils{

    private BeanUtil() {} // 私有化构造函数，防止实例化
    public static <S,T> List<T> copyListProperties(List<S> source, Supplier<T> target) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        List<T> targetList = new ArrayList<>(source.size());
        for (S s : source) {
            T t = target.get();
            copyProperties(s, t);
            targetList.add(t);
        }
        return targetList;
    }

    /**
     * 深拷贝单个对象
     * 通过 JSON 序列化和反序列化实现完全独立的副本
     */
    public static <T> T deepCopy(T source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        String json = JsonUtil.Obj2string(source);
        return JsonUtil.string2Obj(json, clazz);
    }

    /**
     * 深拷贝列表
     */
    public static <T> List<T> deepCopyList(List<T> sources, Class<T> clazz) {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }
        String json = JsonUtil.Obj2string(sources);
        return JsonUtil.string2List(json, clazz);
    }
}
