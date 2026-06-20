package com.catenary.arc.service;

import java.util.Date;
import com.catenary.arc.dto.LoginRequest;
import com.catenary.arc.dto.LoginResponse;
import com.catenary.arc.entity.User;
import com.catenary.arc.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("用户已被禁用");
        }

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("bureauId", user.getBureauId() != null ? user.getBureauId() : "")
                .claim("stationId", user.getStationId() != null ? user.getStationId() : "")
                .claim("workAreaId", user.getWorkAreaId() != null ? user.getWorkAreaId() : "")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret)))
                .compact();

        log.info("User logged in: {}", user.getUsername());

        return LoginResponse.builder()
                .token(token)
                .expiresIn(expiration / 1000)
                .username(user.getUsername())
                .role(user.getRole().name())
                .bureauId(user.getBureauId())
                .stationId(user.getStationId())
                .workAreaId(user.getWorkAreaId())
                .build();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret)))
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
