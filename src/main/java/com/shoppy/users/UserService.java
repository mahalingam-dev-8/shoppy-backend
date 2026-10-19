package com.shoppy.users;

import com.shoppy.common.ApiException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already exists");
        }
        Role role = request.role() == null ? Role.USER : Role.valueOf(request.role());
        try {
            User user = new User(email, passwordEncoder.encode(request.password()), role);
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two signups with the same email at once: the second hits the UNIQUE constraint.
            throw ApiException.conflict("Email already exists");
        }
    }

    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
