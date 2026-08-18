package com.platform.auth.security;

import com.platform.auth.service.JwtService;
import com.platform.auth.service.TokenDenylistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Not annotated with {@code @Component} deliberately: Spring Boot auto-registers any
 * {@link OncePerRequestFilter} bean as a container-level servlet filter as well as
 * wherever it's added in the Spring Security chain, which would run it twice per
 * request. Instead this is constructed as a plain object via a {@code @Bean} method
 * in {@code SecurityConfig} and wired only into the security filter chain.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenylistService tokenDenylistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String jti = claims.getId();

                if (!tokenDenylistService.isDenied(jti)) {
                    UUID userId = UUID.fromString(claims.getSubject());
                    String email = claims.get("email", String.class);
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);

                    AuthenticatedUser principal = new AuthenticatedUser(userId, email, Set.copyOf(roles));
                    List<GrantedAuthority> authorities = roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .map(GrantedAuthority.class::cast)
                            .toList();

                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired/tampered token - leave context unauthenticated;
                // the request falls through to Spring Security's normal 401/403 handling.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
