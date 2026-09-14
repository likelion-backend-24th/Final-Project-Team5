package org.example.authservice.helper.service;

import org.example.authservice.auth.dto.*;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.service.*;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.*;
import org.example.authservice.helper.entity.HelperInvitation;
import org.example.authservice.helper.repository.HelperInvitationRepository;
import org.example.authservice.user.entity.*;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${HELPER_TEST_DB_URL:jdbc:h2:mem:helper_invitation_test;MODE=MySQL;DB_CLOSE_DELAY=-1}", "spring.datasource.driver-class-name=${HELPER_TEST_DB_DRIVER:org.h2.Driver}", "spring.datasource.username=${HELPER_TEST_DB_USER:sa}", "spring.datasource.password=${HELPER_TEST_DB_PASSWORD:}", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class HelperAccountServiceTest {
    @Autowired HelperAccountService service;
    @Autowired UserRepository users;
    @Autowired HelperInvitationRepository invitations;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService auth;
    @Autowired TokenSessionService sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean EmailService email;
    @MockitoSpyBean AccountAccessPolicy policy;
    AtomicReference<String> token = new AtomicReference<>();
    LocalDateTime now;

    @BeforeEach void setup() {
        invitations.deleteAll(); refreshTokens.deleteAll(); users.deleteAll();
        now = LocalDateTime.of(2030, 5, 1, 12, 0);
        doAnswer(call -> now).when(policy).now();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invitations.count()).isPositive();
            String link = call.getArgument(4);
            token.set(link.substring(link.lastIndexOf('/') + 1));
            return null;
        }).when(email).sendHelperInvitation(anyString(), anyString(), anyString(), any(), anyString());
    }
    CreateHelperAccountRequest request(long festival, String email) {
        return new CreateHelperAccountRequest(festival, "도우미 페스티벌", now.plusHours(1), now.plusDays(3), email);
    }
    HelperAccountSummaryResponse.HelperAccount invite() { return service.createHelperAccount(request(7, " Person@Example.COM ")); }
    AcceptHelperInvitationRequest password() { return new AcceptHelperInvitationRequest("new-password123", "new-password123"); }
    void mutate(long helperId, Consumer<HelperInvitation> action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> action.accept(invitations.findByHelperUser_Id(helperId).orElseThrow()));
    }
    void expectCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode().name()).isEqualTo(code));
    }
    User legacy() {
        User user = new User(); user.setName("기존 도우미"); user.setUsername("helper-legacy@helper.local");
        user.setNickname("기존 도우미"); user.setRole(Role.HELPER); user.setStatus(AccountStatus.ACTIVE);
        user.setFestivalId(7L); user.setFestivalEndAt(now.plusDays(3)); user.setPassword(encoder.encode("old-password"));
        return users.save(user);
    }
    @Test void createsPendingAccountWithHashedTokenAndNormalizedContact() {
        var result = invite();
        User user = users.findById(result.helperUserId()).orElseThrow();
        var invitation = invitations.findByHelperUser_Id(user.getId()).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.HELPER);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(user.getPassword()).isNull();
        assertThat(user.getFestivalId()).isEqualTo(7);
        assertThat(user.getUsername()).matches("helper-[a-z0-9]{8}@helper.local");
        assertThat(result.email()).isEqualTo("person@example.com");
        assertThat(token.get()).hasSize(43);
        assertThat(invitation.getTokenHash()).isEqualTo(TokenSessionService.hashToken(token.get())).hasSize(64);
        assertThat(invitation.getTokenHash()).doesNotContain(token.get());
        assertThat(invitation.getDeliveryStatus()).isEqualTo(HelperInvitation.DeliveryStatus.SENT);
        assertThat(invitation.getExpiresAt()).isEqualTo(now.plusHours(24));
    }
    @Test void getIsReadOnlyAndMasksEmail() {
        var created = invite();
        var before = invitations.findByHelperUser_Id(created.helperUserId()).orElseThrow();
        var info = service.inspect(token.get()); service.inspect(token.get());
        assertThat(info.maskedEmail()).isEqualTo("p***@example.com");
        assertThat(info.festivalName()).isEqualTo("도우미 페스티벌");
        var after = invitations.findByHelperUser_Id(created.helperUserId()).orElseThrow();
        assertThat(after.getAcceptedAt()).isNull();
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }
    @Test void acceptsOnceAndIssuesHelperJwtAndHttpOnlyCookie() throws Exception {
        var created = invite();
        var result = mvc.perform(post("/api/auth/helper-invitations/{token}/accept", token.get()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(password())))
            .andExpect(status().isOk()).andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().secure("refreshToken", true)).andExpect(jsonPath("$.data.refreshToken").doesNotExist()).andReturn();
        String access = mapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
        var claims = mapper.readTree(java.util.Base64.getUrlDecoder().decode(access.split("[.]")[1]));
        assertThat(claims.get("role").asText()).isEqualTo("HELPER");
        assertThat(claims.get("festivalId").asLong()).isEqualTo(7);
        assertThat(claims.get("helperSessionVersion").asLong()).isEqualTo(1);
        User active = users.findById(created.helperUserId()).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(encoder.matches(password().password(), active.getPassword())).isTrue();
        assertThat(invitations.findByHelperUser_Id(active.getId()).orElseThrow().getAcceptedAt()).isEqualTo(now);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
        mvc.perform(get("/api/users/me").header("X-User-Id", active.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.festivalId").value(7)).andExpect(jsonPath("$.data.assignedFestival.name").value("도우미 페스티벌"));
        expectCode(() -> service.accept(token.get(), password(), null), "INVITATION_ACCEPTED");
    }
    @Test void concurrentAcceptHasOneWinner() throws Exception {
        invite(); String raw = token.get(); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> { start.await(); try { service.accept(raw, password(), null); return true; } catch (ApiException e) { assertThat(e.getErrorCode().name()).isEqualTo("INVITATION_ACCEPTED"); return false; } };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(refreshTokens.count()).isEqualTo(1);
    }
    @Test void resendReplacesTokenAndEnforcesCooldownAndRollingLimit() {
        var created = invite(); String first = token.get();
        expectCode(() -> service.resend(7L, created.helperUserId()), "INVITATION_COOLDOWN");
        for (int i = 0; i < 9; i++) { now = now.plusSeconds(61); service.resend(7L, created.helperUserId()); }
        expectCode(() -> service.inspect(first), "INVITATION_INVALID");
        assertThat(service.inspect(token.get()).festivalId()).isEqualTo(7);
        now = now.plusSeconds(61);
        expectCode(() -> service.resend(7L, created.helperUserId()), "INVITATION_SEND_LIMIT");
        now = now.plusHours(24); service.resend(7L, created.helperUserId());
        assertThat(invitations.findByHelperUser_Id(created.helperUserId()).orElseThrow().getResendCount()).isEqualTo(10);
    }
    @Test void duplicateEmailDoesNotCreateAccountAndOtherFestivalGetsSeparateIdentity() {
        var first = invite();
        expectCode(() -> invite(), "INVITATION_DUPLICATE");
        assertThat(users.count()).isEqualTo(1);
        var second = service.createHelperAccount(request(8, "person@example.com"));
        assertThat(second.username()).isNotEqualTo(first.username());
        assertThat(users.count()).isEqualTo(2);
    }
    @Test void expiredInvalidRevokedAndEndedLinksAreDistinct() {
        var created = invite();
        expectCode(() -> service.inspect("invalid"), "INVITATION_INVALID");
        now = now.plusHours(24);
        expectCode(() -> service.inspect(token.get()), "INVITATION_EXPIRED");
        expectCode(() -> service.accept(token.get(), password(), null), "INVITATION_EXPIRED");
        assertThat(service.listHelperAccounts(7L).helpers().getFirst().status()).isEqualTo("EXPIRED");
        service.revoke(7L, created.helperUserId());
        expectCode(() -> service.accept(token.get(), password(), null), "INVITATION_REVOKED");
        var other = service.createHelperAccount(request(8, "other@example.com"));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> users.findById(other.helperUserId()).orElseThrow().setFestivalEndAt(now));
        expectCode(() -> service.inspect(token.get()), "HELPER_FESTIVAL_ENDED");
        expectCode(() -> service.accept(token.get(), password(), null), "HELPER_FESTIVAL_ENDED");
        expectCode(() -> service.createHelperAccount(new CreateHelperAccountRequest(9L, "종료", now.minusDays(1), now, "person@example.com")), "HELPER_FESTIVAL_ENDED");
    }
    @Test void expiryNeverExceedsFestivalEnd() {
        var created = service.createHelperAccount(new CreateHelperAccountRequest(7L, "짧은 행사", now, now.plusHours(2), "person@example.com"));
        assertThat(created.expiresAt()).isEqualTo(now.plusHours(2));
    }
    @Test void failedEmailPreservesAccountAndCanBeResent() {
        doThrow(new IllegalStateException("SMTP failure")).when(email).sendHelperInvitation(anyString(), anyString(), anyString(), any(), anyString());
        expectCode(() -> invite(), "INVITATION_SEND_FAILED");
        var helper = service.listHelperAccounts(7L).helpers().getFirst();
        assertThat(helper.status()).isEqualTo("PENDING");
        assertThat(helper.deliveryStatus()).isEqualTo("SEND_FAILED");
        doNothing().when(email).sendHelperInvitation(anyString(), anyString(), anyString(), any(), anyString());
        now = now.plusSeconds(61);
        assertThat(service.resend(7L, helper.helperUserId()).deliveryStatus()).isEqualTo("SENT");
    }
    @Test void pendingAndRevokedCannotLoginOrReissue() {
        var created = invite();
        expectCode(() -> auth.login(new LoginRequest(created.username(), "anything")), "HELPER_PENDING_ACTIVATION");
        String refresh = pendingRefresh(created.helperUserId());
        expectCode(() -> auth.reissue(refresh), "HELPER_PENDING_ACTIVATION");
        service.revoke(7L, created.helperUserId());
        expectCode(() -> auth.login(new LoginRequest(created.username(), "anything")), "INVITATION_REVOKED");
        expectCode(() -> auth.reissue(refresh), "INVITATION_REVOKED");
    }
    String pendingRefresh(long helperId) {
        var jwt = new org.example.authservice.auth.security.JwtTokenProvider();
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "secret", "test-jwt-secret-key-for-acceptance-tests-must-be-at-least-256-bits-long");
        org.springframework.test.util.ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 1209600000L); jwt.init();
        User user = users.findById(helperId).orElseThrow();
        String raw = jwt.generateRefreshToken(user.getUsername());
        RefreshToken saved = new RefreshToken(); saved.setUser(user); saved.setTokenHash(TokenSessionService.hashToken(raw)); saved.setExpiresAt(LocalDateTime.now().plusDays(1)); refreshTokens.save(saved);
        return raw;
    }
    @Test void legacyKeepsLoginUntilAcceptanceThenRevokesOldSessions() {
        var old = legacy();
        var oldTokens = auth.login(new LoginRequest(old.getUsername(), "old-password"));
        assertThat(service.listHelperAccounts(7L).helpers().getFirst().legacy()).isTrue();
        service.convertLegacy(old.getId(), request(7, "person@example.com"));
        assertThat(users.findById(old.getId()).orElseThrow().getPassword()).isEqualTo(old.getPassword());
        auth.login(new LoginRequest(old.getUsername(), "old-password"));
        service.accept(token.get(), password(), null);
        expectCode(() -> auth.login(new LoginRequest(old.getUsername(), "old-password")), "INVALID_PASSWORD");
        expectCode(() -> auth.reissue(oldTokens.getRefreshToken()), "HELPER_SESSION_REVOKED");
        expectCode(() -> service.validateSession(old.getId(), 7L, 0), "HELPER_SESSION_REVOKED");
        service.validateSession(old.getId(), 7L, 1);
    }
    @Test void revokedActiveSessionCannotBeUsedAndWrongFestivalCannotModify() {
        var created = invite(); service.accept(token.get(), password(), null);
        expectCode(() -> service.revoke(8L, created.helperUserId()), "FORBIDDEN_HELPER_FESTIVAL");
        expectCode(() -> service.resend(8L, created.helperUserId()), "FORBIDDEN_HELPER_FESTIVAL");
        service.validateSession(created.helperUserId(), 7L, 1);
        service.revoke(7L, created.helperUserId());
        expectCode(() -> service.validateSession(created.helperUserId(), 7L, 1), "HELPER_SESSION_REVOKED");
        assertThat(refreshTokens.findAllByUser_IdAndRevokedAtIsNull(created.helperUserId())).isEmpty();
    }
    @Test void cleanupDeletesPendingRevokedAndActiveWithInvitationsAndTokens() {
        var pending = invite(); pendingRefresh(pending.helperUserId());
        var revoked = service.createHelperAccount(request(7, "revoked@example.com")); service.revoke(7L, revoked.helperUserId());
        service.createHelperAccount(request(7, "active@example.com")); service.accept(token.get(), password(), null);
        legacy();
        assertThat(service.deleteExpiredHelperAccounts(now.plusDays(3).minusSeconds(1))).isZero();
        assertThat(service.deleteExpiredHelperAccounts(now.plusDays(3).plusSeconds(1))).isEqualTo(4);
        assertThat(invitations.count()).isZero(); assertThat(refreshTokens.count()).isZero(); assertThat(users.count()).isZero();
    }
    @Test void validationAndInternalAuthentication() throws Exception {
        mvc.perform(post("/internal/v1/helper-accounts").header("Authorization", "Bearer wrong").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request(7, "person@example.com")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/helper-accounts").header("Authorization", "Bearer CHANGE_ME_IN_ENV").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request(7, "invalid")))).andExpect(status().isBadRequest());
        invite();
        mvc.perform(post("/api/auth/helper-invitations/{token}/accept", token.get()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"short\",\"passwordConfirm\":\"short\"}")).andExpect(status().isBadRequest());
        expectCode(() -> service.accept(token.get(), new AcceptHelperInvitationRequest("password123", "different123"), null), "PASSWORD_CONFIRM_MISMATCH");
        assertThat(service.inspect(token.get())).isNotNull();
    }
    @Test void signupCannotAttachPasswordToPendingHelper() {
        var created = invite();
        expectCode(() -> auth.signup(new SignupRequest("name", created.username(), "new-nickname", "password123", true)), "DUPLICATE_USERNAME");
        assertThat(users.findById(created.helperUserId()).orElseThrow().getPassword()).isNull();
    }
    @Test void concurrentDuplicateCreatesOnlyOneAccount() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> { start.await(); try { invite(); return true; } catch (ApiException e) { assertThat(e.getErrorCode().name()).isEqualTo("INVITATION_DUPLICATE"); return false; } };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(users.count()).isEqualTo(1); assertThat(invitations.count()).isEqualTo(1);
    }
    @Test void endedHelperCannotLoginOrRefresh() {
        var created = invite(); var tokens = service.accept(token.get(), password(), null);
        now = now.plusDays(3);
        expectCode(() -> auth.login(new LoginRequest(created.username(), password().password())), "HELPER_FESTIVAL_ENDED");
        expectCode(() -> auth.reissue(tokens.getRefreshToken()), "HELPER_FESTIVAL_ENDED");
    }
    @Test void sameContactDoesNotAlterExistingRegularAccountAndCookieSessionIsReplaced() {
        User regular = legacy(); regular.setRole(Role.USER); regular.setUsername("person@example.com"); regular.setFestivalId(null); users.save(regular);
        var prior = auth.login(new LoginRequest(regular.getUsername(), "old-password"));
        var created = invite(); service.accept(token.get(), password(), prior.getRefreshToken());
        User unchanged = users.findById(regular.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(Role.USER); assertThat(unchanged.getPassword()).isEqualTo(regular.getPassword());
        assertThat(created.helperUserId()).isNotEqualTo(regular.getId());
        assertThat(refreshTokens.findByTokenHash(TokenSessionService.hashToken(prior.getRefreshToken())).orElseThrow().getRevokedAt()).isNotNull();
    }
}
