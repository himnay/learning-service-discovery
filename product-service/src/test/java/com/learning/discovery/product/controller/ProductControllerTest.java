package com.learning.discovery.product.controller;

import com.learning.discovery.product.config.SecurityConfig;
import com.learning.discovery.product.model.Product;
import com.learning.discovery.product.service.ProductService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@WithMockUser
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @BeforeEach
    void stubProducts() {
        List<Product> products = List.of(
                new Product(201L, "Keyboard", new BigDecimal("49.99")),
                new Product(202L, "Monitor", new BigDecimal("199.99")),
                new Product(203L, "Mouse", new BigDecimal("19.99")));
        when(productService.findAll()).thenReturn(products);
        products.forEach(product -> when(productService.findById(product.id())).thenReturn(Optional.of(product)));
        when(productService.findById(999L)).thenReturn(Optional.empty());
    }

    @Test
    void getProductsReturnsSeedList() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void getProductByIdReturnsMatch() throws Exception {
        mockMvc.perform(get("/api/products/202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Monitor"))
                .andExpect(jsonPath("$.price").value(199.99));
    }

    @Test
    @WithAnonymousUser
    void getProductsWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductByUnknownIdReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Product 999 not found"))
                .andExpect(jsonPath("$.instance").value("/api/products/999"));
    }

    @Test
    void nonNumericIdReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
