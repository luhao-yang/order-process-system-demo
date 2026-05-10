package com.example.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OrderIntegrationTest {

    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("wiremock.port", wireMock::port);
    }

    @AfterAll
    static void stopWireMock() { wireMock.stop(); }

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        wireMock.resetAll();
    }

    @Test
    void loginCreateAndRetrieveOrderHappyPath() throws Exception {
        stubAllChannelsOk();

        String token = login("admin", "admin-pass");

        String body = """
                { "customerId": "c-1", "items": [ { "productId": "p-1", "quantity": 2, "unitPrice": 9.99 } ] }
                """;

        MvcResult res = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        assertThat(json.get("order").get("status").asText()).isEqualTo("CREATED");
        assertThat(json.get("notifications")).hasSize(2);
        json.get("notifications").forEach(n -> assertThat(n.get("status").asText()).isEqualTo("SENT"));

        String id = json.get("order").get("id").asText();

        mvc.perform(get("/api/v1/orders/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void partialNotificationFailureReturns207() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/email")).willReturn(ok()));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/sms")).willReturn(serverError()));

        String token = login("admin", "admin-pass");
        String body = """
                { "customerId": "c-2", "items": [ { "productId": "p-1", "quantity": 1, "unitPrice": 5.00 } ] }
                """;

        MvcResult res = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(207);
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        boolean smsFailed = false;
        for (JsonNode n : json.get("notifications")) {
            if (n.get("channel").asText().equals("sms")) {
                assertThat(n.get("status").asText()).isEqualTo("FAILED");
                assertThat(n.get("errorCode").asText()).isEqualTo("NOTIF_SMS_FAILED");
                smsFailed = true;
            }
        }
        assertThat(smsFailed).isTrue();
        // 3 retry attempts on /sms
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/sms")));
    }

    @Test
    void invalidStateTransitionReturns409() throws Exception {
        stubAllChannelsOk();
        String token = login("admin", "admin-pass");

        String createBody = """
                { "customerId": "c-3", "items": [ { "productId": "p-1", "quantity": 1, "unitPrice": 1.00 } ] }
                """;
        MvcResult created = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(createBody))
                .andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get("order").get("id").asText();

        // Cancel
        mvc.perform(patch("/api/v1/orders/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        // Try to complete an already-cancelled order -> 409
        MvcResult conflict = mvc.perform(patch("/api/v1/orders/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        JsonNode err = mapper.readTree(conflict.getResponse().getContentAsString());
        assertThat(err.get("errorCode").asText()).isEqualTo("ORDER_INVALID_STATE_TRANSITION");
    }

    @Test
    void userRoleCannotPatchStatus() throws Exception {
        stubAllChannelsOk();
        String adminToken = login("admin", "admin-pass");
        String userToken = login("user", "user-pass");

        String createBody = """
                { "customerId": "c-4", "items": [ { "productId": "p-1", "quantity": 1, "unitPrice": 1.00 } ] }
                """;
        MvcResult created = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(createBody))
                .andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get("order").get("id").asText();

        mvc.perform(patch("/api/v1/orders/" + id + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    private void stubAllChannelsOk() {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/email")).willReturn(ok()));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/sms")).willReturn(ok()));
    }

    private String login(String user, String pass) throws Exception {
        String body = "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                .contentType("application/json").content(body)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @TestConfiguration
    static class WireMockConfig {
        @Bean WireMockServer wireMockServer() { return wireMock; }
    }
}
