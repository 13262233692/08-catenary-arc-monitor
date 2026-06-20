package com.catenary.arc.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private String username;

    private String role;

    private String bureauId;

    private String stationId;

    private String workAreaId;
}
