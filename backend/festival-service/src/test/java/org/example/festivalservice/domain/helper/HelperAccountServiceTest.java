package org.example.festivalservice.domain.helper;

import org.example.festivalservice.controller.HostHelperAccountController;
import org.example.festivalservice.common.exception.GlobalExceptionHandler;
import org.example.festivalservice.domain.festival.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HelperAccountServiceTest {
    FestivalRepository festivals;
    MockRestServiceServer server;
    MockMvc mvc;
    @BeforeEach void setup() {
        festivals = mock(FestivalRepository.class);
        var builder = RestClient.builder().baseUrl("http://auth.test");
        server = MockRestServiceServer.bindTo(builder).build();
        var service = new HelperAccountService(festivals, builder.build());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "internalAuthToken", "test-internal");
        mvc = MockMvcBuilders.standaloneSetup(new HostHelperAccountController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(festivals.findById(7L)).thenReturn(Optional.of(Festival.builder().id(7L).hostUserId(10L).name("행사")
            .startAt(LocalDateTime.of(2030, 1, 1, 10, 0)).endAt(LocalDateTime.of(2030, 1, 2, 10, 0)).build()));
    }
    @AfterEach void verifyServer() { server.verify(); }
    String base() { return "/api/host/festivals/7/helpers"; }
    String account() { return "{\"success\":true,\"data\":{\"helperUserId\":20,\"username\":\"helper-test@helper.local\",\"email\":\"person@example.com\",\"status\":\"PENDING\",\"deliveryStatus\":\"SENT\",\"legacy\":false}}"; }
    @Test void ownerCanInviteNormalizedEmailWithFestivalSnapshot() throws Exception {
        server.expect(requestTo("http://auth.test/internal/v1/helper-accounts")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-internal"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"festivalId\":7,\"festivalName\":\"행사\",\"festivalStartAt\":\"2030-01-01T10:00:00\",\"festivalEndAt\":\"2030-01-02T10:00:00\",\"email\":\"person@example.com\"}"))
            .andRespond(withSuccess(account(), MediaType.APPLICATION_JSON));
        mvc.perform(post(base()).header("X-User-Id",10).header("X-User-Role","HOST").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\" Person@Example.COM \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING")).andExpect(jsonPath("$.data.password").doesNotExist());
    }
    @Test void invalidEmailIsRejectedWithoutInternalCall() throws Exception {
        for (String email : new String[]{"invalid", "", "a".repeat(255) + "@example.com"}) {
            mvc.perform(post(base()).header("X-User-Id",10).header("X-User-Role","HOST").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest());
        }
    }
    @Test void roleAndOwnershipAreCheckedForEveryOperation() throws Exception {
        for (String role : new String[]{"USER", "HOST"}) {
            var requests = java.util.List.of(post(base()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"person@example.com\"}"),
                get(base()), post(base()+"/20/resend"), delete(base()+"/20"),
                post(base()+"/20/invitation").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"person@example.com\"}"));
            for (var request : requests) mvc.perform(request.header("X-User-Id",99).header("X-User-Role",role)).andExpect(status().isForbidden());
        }
    }
    @Test void ownerCanListResendRevokeAndConvert() throws Exception {
        server.expect(requestTo("http://auth.test/internal/v1/helper-accounts?festivalId=7"))
            .andRespond(withSuccess("{\"success\":true,\"data\":{\"totalCount\":0,\"helpers\":[]}}",MediaType.APPLICATION_JSON));
        mvc.perform(get(base()).header("X-User-Id",10).header("X-User-Role","HOST")).andExpect(status().isOk());
        server.verify(); server.reset();
        server.expect(requestTo("http://auth.test/internal/v1/helper-accounts/20/resend?festivalId=7")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(account(),MediaType.APPLICATION_JSON));
        mvc.perform(post(base()+"/20/resend").header("X-User-Id",10).header("X-User-Role","HOST")).andExpect(status().isOk());
        server.verify(); server.reset();
        server.expect(requestTo("http://auth.test/internal/v1/helper-accounts/20?festivalId=7")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess(account(),MediaType.APPLICATION_JSON));
        mvc.perform(delete(base()+"/20").header("X-User-Id",10).header("X-User-Role","HOST")).andExpect(status().isOk());
        server.verify(); server.reset();
        server.expect(requestTo("http://auth.test/internal/v1/helper-accounts/20/invitation")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(account(),MediaType.APPLICATION_JSON));
        mvc.perform(post(base()+"/20/invitation").header("X-User-Id",10).header("X-User-Role","HOST").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"person@example.com\"}")).andExpect(status().isOk());
    }
    @Test void authErrorsKeepSafeActionableCodes() throws Exception {
        for (var code : java.util.Map.of("INVITATION_DUPLICATE",409,"INVITATION_COOLDOWN",429,"INVITATION_SEND_FAILED",502,"HELPER_FESTIVAL_ENDED",410).entrySet()) {
            server.reset();
            server.expect(requestTo("http://auth.test/internal/v1/helper-accounts")).andRespond(withStatus(HttpStatus.valueOf(code.getValue()))
                .contentType(MediaType.APPLICATION_JSON).body("{\"success\":false,\"errorCode\":\"" + code.getKey() + "\"}"));
            mvc.perform(post(base()).header("X-User-Id",10).header("X-User-Role","HOST").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().is(code.getValue())).andExpect(jsonPath("$.errorCode").value(code.getKey()));
            server.verify();
        }
    }
}
