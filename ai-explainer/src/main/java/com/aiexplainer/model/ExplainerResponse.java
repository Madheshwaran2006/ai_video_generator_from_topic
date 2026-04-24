package com.aiexplainer.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExplainerResponse {
    private String topic;
    private String fullScript;
    private List<Scene> scenes;
    private String audioFileName;
    private String videoFileName;
    private String status;
    private String message;
}
