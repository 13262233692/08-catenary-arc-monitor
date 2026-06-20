package com.catenary.arc.controller;

import com.catenary.arc.dto.ApiResponse;
import com.catenary.arc.dto.LoginRequest;
import com.catenary.arc.dto.LoginResponse;
import com.catenary.arc.entity.User;
import com.catenary.arc.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        LoginResponse loginResponse = authService.login(loginRequest);
        return ApiResponse.success(loginResponse);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getUserByUsername(userDetails.getUsername());
        Map<String, Object> info = Map.of(
                "username", user.getUsername(),
                "realName", user.getRealName() != null ? user.getRealName() : "",
                "role", user.getRole().name(),
                "bureauId", user.getBureauId() != null ? user.getBureauId() : "",
                "stationId", user.getStationId() != null ? user.getStationId() : "",
                "workAreaId", user.getWorkAreaId() != null ? user.getWorkAreaId() : ""
        );
        return ApiResponse.success(info);
    }
}
