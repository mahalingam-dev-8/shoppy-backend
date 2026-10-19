package com.shoppy.auth;

import com.shoppy.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Reads the JWT from the {@code Authentication} cookie and, when valid, sets the caller as the
 * authenticated principal. The role is loaded from the database on each request (as the NestJS
 * RolesGuard did), so a role change takes effect without waiting for the token to expire.
 *
 * <p>Not a Spring bean on purpose: a @Component Filter would also be registered in the servlet
 * chain and run twice. SecurityConfig constructs it.
 */
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "Authentication";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtCookieAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parseUserId(cookie.getValue())
                    .flatMap(userRepository::findById)
                    .ifPresent(user -> {
                        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRole());
                        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
