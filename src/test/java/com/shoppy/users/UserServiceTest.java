package com.shoppy.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shoppy.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void signupDefaultsToCustomerAndStoresAHashedNormalisedEmail() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        UserResponse response = userService.create(new CreateUserRequest("  New@Example.com ", "Secret@123", null));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
    }

    @Test
    void signupAsSellerGetsTheAdminRole() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 8L));

        UserResponse response = userService.create(new CreateUserRequest("seller@example.com", "Secret@123", "ADMIN"));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void existingEmailIsAConflict() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("taken@example.com", "Secret@123", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateSignupIsAlsoAConflict() {
        // Two requests pass the existence check together; the UNIQUE constraint rejects the second.
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("race@example.com", "Secret@123", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
