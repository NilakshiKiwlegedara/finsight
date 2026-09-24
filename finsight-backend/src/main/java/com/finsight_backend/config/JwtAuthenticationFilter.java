package com.finsight_backend.config;

import com.finsight_backend.repository.UserRepository;
import com.finsight_backend.service.JwtService;
import com.finsight_backend.exception.ApiError;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Created only in the security chain, avoiding duplicate servlet-filter registration.
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PathPatternRequestMatcher.withDefaults().matcher("/api/auth/**").matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            String email;
            try {
                if (!jwtService.isTokenValid(token)) {
                    ApiError.write(response, 401, "Invalid or expired token");
                    return;
                }
                email = jwtService.extractEmail(token);
            } catch (JwtException | IllegalArgumentException exception) {
                ApiError.write(response, 401, "Invalid or expired token");
                return;
            }
            if (email == null || email.isBlank() || !userRepository.existsByEmail(email)) {
                ApiError.write(response, 401, "Invalid or expired token");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(email, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }
}
