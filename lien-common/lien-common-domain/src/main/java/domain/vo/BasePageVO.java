package domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class BasePageVO<T> {
    /**
     * 查询结果总数
     */
    Integer totals;

    /**
     * 总页数
     */
    Integer totalPages;

    /**
     * 数据列表
     */
    List<T> list;
}
