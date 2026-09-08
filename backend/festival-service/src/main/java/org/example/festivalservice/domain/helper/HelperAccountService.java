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

/**
 * 주최자가 자기 페스티벌 현장 입장 검증을 맡길 도우미 계정을 발급·재발급·조회한다.
 * 계정 자체는 auth-service가 소유하므로 여기서는 "정말 이 호스트의 페스티벌인지"만 확인하고 내부 API로 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class HelperAccountService {

    private static final String HOST_ROLE = "HOST";

    private final FestivalRepository festivalRepository;
    private final RestClient authServiceRestClient;

    @Value("${internal.auth-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //호출할 때마다 계정을 하나씩 발급한다. 평문 비밀번호는 이 응답에서만 볼 수 있다.
    public HelperAccountDto.CredentialResponse createHelperAccount(Long festivalId, Long hostUserId, String role) {
        Festival festival = getOwnedFestival(festivalId, hostUserId, role);

        return callAuthService(() -> authServiceRestClient.post()
                .uri("/internal/v1/helper-accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .body(new HelperAccountDto.CreateRequest(festival.getId(), festival.getEndAt()))
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.CredentialResponse>>() {}));
    }

    //호스트가 발급 당시 비밀번호를 놓쳤을 때 — 계정은 그대로 두고 비밀번호만 새로 만든다.
    public HelperAccountDto.CredentialResponse reissuePassword(Long festivalId, Long helperUserId,
                                                               Long hostUserId, String role) {
        getOwnedFestival(festivalId, hostUserId, role);

        return callAuthService(() -> authServiceRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/helper-accounts/{helperUserId}/password")
                        .queryParam("festivalId", festivalId)
                        .build(helperUserId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .retrieve()
                .body(new ParameterizedTypeReference<AuthApiEnvelope<HelperAccountDto.CredentialResponse>>() {}));
    }

    //몇 개의 계정을 발급했는지 확인한다. 비밀번호는 해시로만 저장돼 목록에 담기지 않는다.
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

    //도우미 계정을 다룰 수 있는 건 그 페스티벌을 실제로 주최하는 호스트뿐이다.
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
            //재발급 대상 계정이 없거나 다른 페스티벌 계정인 경우 — 호스트에게는 "없는 계정"으로 동일하게 응답한다.
            throw new ApiException(HelperAccountErrorCode.HELPER_ACCOUNT_NOT_FOUND);
        } catch (RestClientException e) {
            throw new ApiException(HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
        if (envelope == null || envelope.data() == null) {
            throw new ApiException(HelperAccountErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
        return envelope.data();
    }

    @FunctionalInterface
    private interface AuthServiceCall<T> {
        AuthApiEnvelope<T> execute();
    }
}
