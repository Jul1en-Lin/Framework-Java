package com.lien.mstemplateservice.entity.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqMessage implements Serializable {

    private Long id;

    private String content;

    private LocalDateTime sendTime;
}
