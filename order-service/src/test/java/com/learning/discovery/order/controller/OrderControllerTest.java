package com.learning.discovery.order.controller;

import com.learning.discovery.order.client.UserClient;
import com.learning.discovery.order.config.SecurityConfig;
import com.learning.discovery.order.model.Order;
import com.learning.discovery.order.model.UserSummary;
import com.learning.discovery.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@WithMockUser
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private OrderService orderService;

    @BeforeEach
    void stubOrders() {
        List<Order> orders = List.of(
                new Order(101L, 1L, "Keyboard", 1),
                new Order(102L, 2L, "Monitor", 2),
                new Order(103L, 1L, "Mouse", 3));
        when(orderService.findAll()).thenReturn(orders);
        orders.forEach(order -> when(orderService.findById(order.id())).thenReturn(Optional.of(order)));
        when(orderService.findById(999L)).thenReturn(Optional.empty());
    }

    @Test
    void getOrdersReturnsSeedList() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].product").value("Keyboard"));
    }

    @Test
    void getOrderByIdReturnsMatch() throws Exception {
        mockMvc.perform(get("/api/orders/102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("Monitor"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void getOrderByUnknownIdReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Order 999 not found"))
                .andExpect(jsonPath("$.instance").value("/api/orders/999"));
    }

    @Test
    void nonNumericIdReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/orders/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getOrderWithUserEnrichesFromUserService() throws Exception {
        when(userClient.fetchUser(1L)).thenReturn(Optional.of(new UserSummary(1L, "Alice", "alice@example.com")));

        mockMvc.perform(get("/api/orders/101/with-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.product").value("Keyboard"))
                .andExpect(jsonPath("$.user.name").value("Alice"));
    }

    @Test
    void getOrderWithUserWhenUserServiceUnreachableReturnsNullUser() throws Exception {
        when(userClient.fetchUser(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/101/with-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.product").value("Keyboard"))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @WithAnonymousUser
    void getOrdersWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/orders").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unexpectedExceptionReturns500ProblemDetailWithoutStackTrace() throws Exception {
        when(userClient.fetchUser(1L)).thenThrow(new IllegalStateException("boom: secret internals"));

        mockMvc.perform(get("/api/orders/101/with-user"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected error, see the order-service logs"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internals"))));
    }
}
