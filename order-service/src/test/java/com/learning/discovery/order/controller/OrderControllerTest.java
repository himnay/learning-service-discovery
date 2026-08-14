package com.learning.discovery.order.controller;

import com.learning.discovery.order.client.UserClient;
import com.learning.discovery.order.model.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

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
    void getOrderByUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
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
}
