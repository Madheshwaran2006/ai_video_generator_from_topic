package com.aiexplainer.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Scene {
    private int sceneNumber;
    private String title;
    private String narration;
    private String visualKeyword;
}
