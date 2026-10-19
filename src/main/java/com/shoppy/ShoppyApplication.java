package com.shoppy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Authentication is done by JwtCookieAuthFilter, so Boot's generated in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ShoppyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppyApplication.class, args);
    }
}
