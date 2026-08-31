package com.lien.common.core.enums;

import com.lien.common.core.config.ThreadPoolConfig;
import lombok.Getter;

@Getter
/**
 * 线程池配置的拒绝策略枚举类。
 * 该枚举用于映射 {@link java.util.concurrent.RejectedExecutionHandler} 的不同实现，
 * 从而在 {@code ThreadPoolConfig} 中实现可配置的拒绝策略选择。
 *
 * @see ThreadPoolConfig
 */
public enum RejectType {

    /**
     * AbortPolicy策略
     */
    AbortPolicy(1),

    /**
     * CallerRunsPolicy策略
     */
    CallerRunsPolicy(2),

    /**
     * DiscardOldestPolicy策略
     */
    DiscardOldestPolicy(3),

    /**
     * DiscardPolicy策略
     */
    DiscardPolicy(4);


    private Integer value;

    RejectType(Integer value) {
        this.value = value;
    }
}
