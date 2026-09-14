package org.example.festivalservice.domain.helper;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 행사 소유권을 검증한 뒤 인증 서비스에 초대 관리를 위임한다. */
@Service
@RequiredArgsConstructor
public class HelperAccountService {

    private static final String HOST_ROLE = "HOST";

    private final FestivalRepository festivalRepository;

    private final RestClient authServiceRestClient;

    @Value("${internal.auth-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    public HelperAccountDto.HelperAccount createHelperAccount(
            Long festivalId, Long hostUserId, String role, String email
    ) {
        Festival festival = getOwnedFestival(festivalId, hostUserId, role);

        return callAuthService(() -> authServiceRestClient.post()
                .uri("/internal/v1/helper-accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .body(request(festival, email))
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.HelperAccount>>() {}));
    }

    public HelperAccountDto.HelperAccount convertLegacy(
            Long festivalId, Long helperId, Long hostId, String role, String email
    ) {
        Festival festival = getOwnedFestival(festivalId, hostId, role);
        return callAuthService(() -> authServiceRestClient.post()
                .uri("/internal/v1/helper-accounts/{id}/invitation", helperId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .body(request(festival, email))
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.HelperAccount>>() {}));
    }

    public HelperAccountDto.HelperAccount resend(Long festivalId, Long helperId, Long hostId, String role) {
        getOwnedFestival(festivalId, hostId, role);
        return callAuthService(() -> authServiceRestClient.post()
                .uri("/internal/v1/helper-accounts/{id}/resend?festivalId={festival}", helperId, festivalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.HelperAccount>>() {}));
    }

    public HelperAccountDto.HelperAccount revoke(Long festivalId, Long helperId, Long hostId, String role) {
        getOwnedFestival(festivalId, hostId, role);
        return callAuthService(() -> authServiceRestClient.delete()
                .uri("/internal/v1/helper-accounts/{id}?festivalId={festival}", helperId, festivalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.HelperAccount>>() {}));
    }

    private HelperAccountDto.CreateRequest request(Festival festival, String email) {
        return new HelperAccountDto.CreateRequest(
                festival.getId(), festival.getName(), festival.getStartAt(), festival.getEndAt(), email);
    }

    public HelperAccountDto.SummaryResponse listHelperAccounts(Long festivalId, Long hostUserId, String role) {
        getOwnedFestival(festivalId, hostUserId, role);

        return callAuthService(() -> authServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/helper-accounts")
                        .queryParam("festivalId", festivalId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.SummaryResponse>>() {}));
    }

    // 도우미 계정을 다룰 수 있는 건 그 페스티벌을 실제로 주최하는 호스트뿐이다.
    private Festival getOwnedFestival(Long festivalId, Long hostUserId, String role) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(HelperAccountErrorCode.FORBIDDEN_HOST_ROLE);
        }
        Festival festival = festivalRepository.findById(festivalId)
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        if (!festival.getHostUserId().equals(hostUserId)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_NOT_OWNER);
        }
        return festival;
    }

    private <T> T callAuthService(AuthServiceCall<T> call) {
        AuthApiEnvelope<T> envelope;
        try {
            envelope = call.execute();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            // 대상 계정이 없거나 다른 행사에 속한 경우 모두 같은 오류로 감춰 계정 존재 여부를 노출하지 않는다.
            throw new ApiException(HelperAccountErrorCode.HELPER_ACCOUNT_NOT_FOUND);
        } catch (RestClientResponseException e) {
            throw new ApiException(toErrorCode(e));
        } catch (RestClientException e) {
            throw new ApiException(HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
        if (envelope == null || !envelope.success() || envelope.data() == null) {
            throw new ApiException(HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
        return envelope.data();
    }

    private HelperAccountErrorCode toErrorCode(RestClientResponseException exception) {
        try {
            AuthError error = exception.getResponseBodyAs(AuthError.class);
            if (error != null && error.errorCode() != null) {
                return HelperAccountErrorCode.valueOf(error.errorCode());
            }
        } catch (IllegalArgumentException ignored) {
            // 알 수 없는 내부 오류 코드는 외부 계약에 노출하지 않고 통신 오류로 처리한다.
            return HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE;
        }
        return HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE;
    }

    private record AuthError(String errorCode) {
    }

    @FunctionalInterface
    private interface AuthServiceCall<T> {
        AuthApiEnvelope<T> execute();
    }
}
