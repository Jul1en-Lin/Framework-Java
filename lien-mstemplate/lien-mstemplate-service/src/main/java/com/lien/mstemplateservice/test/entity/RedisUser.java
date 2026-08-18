package com.lien.mstemplateservice.test.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisUser implements Serializable {

    private Long id;

    private String name;

    private Integer age;

    private LocalDateTime createTime;
}
