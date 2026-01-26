package com.hk-fintech.hk.common.events.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCreatedEvent {
    private Integer userId;
    private String email;
    private String name;
}