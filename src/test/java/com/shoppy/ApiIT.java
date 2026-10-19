package com.shoppy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole application against a real PostgreSQL (Flyway migrations, JPA, Spring Security,
 * JSON) and drives it over HTTP. Skipped when Docker isn't available; CI always has it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApiIT {

    private static final String PASSWORD = "Secret@123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void weakPasswordIsRejectedWithAReadableMessage() throws Exception {
        mvc.perform(post("/users").contentType(APPLICATION_JSON).content(signupJson("weak@example.com", "abc", "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value("password is not strong enough"));
    }

    @Test
    void signupLoginAndFetchCurrentUser() throws Exception {
        signup("alice@example.com", "USER");
        Cookie auth = login("alice@example.com");

        mvc.perform(get("/users/me").cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        signup("dup@example.com", "USER");

        mvc.perform(post("/users").contentType(APPLICATION_JSON).content(signupJson("dup@example.com", PASSWORD, "USER")))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        signup("carol@example.com", "USER");

        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"password\":\"Wrong@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedRouteWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void customerCannotListProducts() throws Exception {
        signup("bob@example.com", "USER");
        Cookie auth = login("bob@example.com");

        mvc.perform(post("/products").cookie(auth).contentType(APPLICATION_JSON).content(productJson("Lamp")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerListsAProductAndItAppearsInTheStore() throws Exception {
        signup("seller@example.com", "ADMIN");
        Cookie auth = login("seller@example.com");

        mvc.perform(post("/products").cookie(auth).contentType(APPLICATION_JSON).content(productJson("Desk Lamp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.price").value(499.00))
                .andExpect(jsonPath("$.sold").value(false));

        mvc.perform(get("/products").param("status", "available").cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Desk Lamp")));
    }

    @Test
    void customerCannotSeeSales() throws Exception {
        signup("dave@example.com", "USER");
        Cookie auth = login("dave@example.com");

        mvc.perform(get("/orders/all").cookie(auth))
                .andExpect(status().isForbidden());
    }

    private void signup(String email, String role) throws Exception {
        mvc.perform(post("/users").contentType(APPLICATION_JSON).content(signupJson(email, PASSWORD, role)))
                .andExpect(status().isCreated());
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("Authentication");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return new Cookie("Authentication", cookie.getValue());
    }

    private static String signupJson(String email, String password, String role) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}".formatted(email, password, role);
    }

    /** Price as a string, the way the frontend sends it (built from a FormData). */
    private static String productJson(String name) {
        return "{\"name\":\"%s\",\"description\":\"A test product\",\"price\":\"499.00\"}".formatted(name);
    }
}
