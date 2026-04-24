package com.aiexplainer.model;

import lombok.Data;

@Data
public class ExplainerRequest {
    private String topic;
    private String difficulty;
    private String model;
}
