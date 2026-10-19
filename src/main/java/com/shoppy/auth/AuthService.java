package com.shoppy.auth;

import com.shoppy.common.ApiException;
import com.shoppy.users.User;
import com.shoppy.users.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** The same message for an unknown email and a wrong password, so neither leaks which it was. */
    public User verifyUser(String email, String password) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
    }
}
