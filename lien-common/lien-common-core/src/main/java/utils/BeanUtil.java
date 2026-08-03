package utils;

import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


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
}
