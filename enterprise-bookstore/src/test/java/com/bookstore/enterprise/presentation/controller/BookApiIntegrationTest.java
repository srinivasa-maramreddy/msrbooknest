package com.bookstore.enterprise.presentation.controller;

import com.bookstore.enterprise.application.security.OtpCodeGenerator;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaAuthorEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaCategoryEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaAuthorRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaBookRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaCategoryRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaOrderRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JpaOrderRepository orderRepository;

    @Autowired
    private JpaBookRepository bookRepository;

    @Autowired
    private JpaAuthorRepository authorRepository;

    @Autowired
    private JpaCategoryRepository categoryRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @MockBean
    private OtpCodeGenerator otpCodeGenerator;

    private Long authorId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        when(otpCodeGenerator.generateCode()).thenReturn("123456");

        orderRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();
        authorRepository.deleteAll();
        categoryRepository.deleteAll();

        JpaAuthorEntity author = new JpaAuthorEntity();
        author.setName("Test Author");
        author.setBio("Bio");
        author = authorRepository.save(author);
        authorId = author.getId();

        JpaCategoryEntity category = new JpaCategoryEntity();
        category.setName("Test Category");
        category.setDescription("Desc");
        category = categoryRepository.save(category);
        categoryId = category.getId();
    }

    @Test
    void publicGetBooksShouldReturnOk() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateBookUsingJwtToken() throws Exception {
        String registerPayload = """
                {
                  "fullName": "Admin User",
                  "email": "admin@test.com",
                  "password": "Pass@1234",
                  "role": "ADMIN",
                  "verificationChannel": "EMAIL",
                  "adminRegistrationKey": "admin-secret-key-test"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationChannel").value("EMAIL"));

        String verifyPayload = """
                {
                  "verificationChannel": "EMAIL",
                  "destination": "admin@test.com",
                  "otpCode": "123456"
                }
                """;

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"));

        String loginPayload = """
                {
                  "email": "admin@test.com",
                  "password": "Pass@1234",
                  "verificationChannel": "EMAIL"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationChannel").value("EMAIL"))
                .andExpect(jsonPath("$.purpose").value("LOGIN"));

        String loginVerifyPayload = """
                {
                  "verificationChannel": "EMAIL",
                  "destination": "admin@test.com",
                  "otpCode": "123456",
                  "purpose": "LOGIN"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginVerifyPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = node.get("token").asText();

        String createPayload = """
                {
                  "isbn": "9781111111111",
                  "title": "Integration Testing with Spring",
                  "description": "A practical test book",
                  "price": 299.00,
                  "stock": 20,
                  "coverImageUrl": "https://example.com/cover.jpg",
                  "featured": true,
                  "bestSeller": false,
                  "authorId": %d,
                  "categoryId": %d
                }
                """.formatted(authorId, categoryId);

        mockMvc.perform(post("/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Testing with Spring"))
                .andExpect(jsonPath("$.authorId").value(authorId))
                .andExpect(jsonPath("$.categoryId").value(categoryId));
    }
}
