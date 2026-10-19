package com.shoppy.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shoppy.common.ApiException;
import com.shoppy.products.Product;
import com.shoppy.products.ProductRepository;
import com.shoppy.users.Role;
import com.shoppy.users.User;
import com.shoppy.users.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrdersServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    OrdersService ordersService;

    @Test
    void firstWebhookDeliveryCreatesTheOrderAndMarksTheProductSold() {
        Product product = new Product("Desk Lamp", "LED lamp", new BigDecimal("1149.00"), null);
        User buyer = new User("buyer@example.com", "hash", Role.USER);
        when(orderRepository.existsByProductId(1L)).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.getReferenceById(2L)).thenReturn(buyer);

        boolean created = ordersService.fulfil(1L, 2L);

        assertThat(created).isTrue();
        assertThat(product.isSold()).isTrue();
        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(order.capture());
        assertThat(order.getValue().getProduct()).isSameAs(product);
        assertThat(order.getValue().getUser()).isSameAs(buyer);
    }

    @Test
    void repeatedWebhookDeliveryIsIgnored() {
        // Stripe retries deliveries; the second one must not create a second order.
        when(orderRepository.existsByProductId(1L)).thenReturn(true);

        boolean created = ordersService.fulfil(1L, 2L);

        assertThat(created).isFalse();
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(productRepository);
    }

    @Test
    void unknownProductIsNotFound() {
        when(orderRepository.existsByProductId(99L)).thenReturn(false);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.fulfil(99L, 2L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(orderRepository, never()).save(any());
    }
}
