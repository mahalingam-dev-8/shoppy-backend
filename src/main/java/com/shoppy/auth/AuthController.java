package com.shoppy.auth;

import com.shoppy.users.User;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * Sets the JWT as the {@code Authentication} cookie. The Next.js login action reads the token
     * from the first Set-Cookie value, so this must stay the only cookie on the response (the
     * stateless session config keeps JSESSIONID off it).
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.verifyUser(request.email(), request.password());
        String token = jwtService.generate(user.getId());

        ResponseCookie cookie = ResponseCookie.from(JwtCookieAuthFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtService.getExpiration())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("tokenpayload", Map.of("userid", user.getId())));
    }
}
