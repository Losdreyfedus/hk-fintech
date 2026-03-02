package com.hk-fintech.hk.common.logging;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class HttpLogEntity {

    private String type;
    private String correlationId;
    private String method;
    private String uri;
    private String queryString;
    private Map<String, String> headers;
    private String body;
    private Integer status;
    private Long durationMs;
    private String targetService;
}
