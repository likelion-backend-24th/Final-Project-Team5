package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.CoordinateBackfillResultDto;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalCoordinateBackfillService;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.FestivalRegion;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.infrastructure.kakao.KakaoLocalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FestivalCoordinateBackfillServiceTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    private FestivalCoordinateBackfillService service;

    @BeforeEach
    void setUp() {
        service = new FestivalCoordinateBackfillService(festivalRepository, kakaoLocalClient);
    }

    private Festival festival(long id, FestivalRegion region, String locationDetail) {
        return Festival.builder().id(id).name("festival-" + id).region(region).locationDetail(locationDetail).build();
    }

    @Test
    void 검색_결과의_지역이_일치하면_좌표를_채운다() {
        Festival festival = festival(1L, FestivalRegion.SEOUL, "서울숲 야외무대");
        when(festivalRepository.findByLatitudeIsNull()).thenReturn(List.of(festival));
        when(kakaoLocalClient.searchKeyword(anyString())).thenReturn(
                Optional.of(new KakaoLocalClient.KeywordResult("서울 성동구 서울숲길 273", null, 37.5445, 127.0374)));

        List<CoordinateBackfillResultDto> results = service.backfillMissingCoordinates("ADMIN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).applied()).isTrue();
        assertThat(festival.getLatitude()).isEqualTo(37.5445);
        assertThat(festival.getLongitude()).isEqualTo(127.0374);
    }

    @Test
    void 검색_결과의_지역이_다르면_적용하지_않고_건너뛴다() {
        Festival festival = festival(1L, FestivalRegion.SEOUL, "중앙공원");
        when(festivalRepository.findByLatitudeIsNull()).thenReturn(List.of(festival));
        //같은 이름이지만 부산에 있는 곳이 잡힌 경우
        when(kakaoLocalClient.searchKeyword(anyString())).thenReturn(
                Optional.of(new KakaoLocalClient.KeywordResult("부산 해운대구 중앙공원", null, 35.16, 129.16)));

        List<CoordinateBackfillResultDto> results = service.backfillMissingCoordinates("ADMIN");

        assertThat(results.get(0).applied()).isFalse();
        assertThat(results.get(0).message()).contains("지역 불일치");
        assertThat(festival.getLatitude()).isNull();
    }

    @Test
    void 검색_결과가_없으면_건너뛴다() {
        Festival festival = festival(1L, FestivalRegion.SEOUL, "존재하지 않는 장소 이름 이상함");
        when(festivalRepository.findByLatitudeIsNull()).thenReturn(List.of(festival));
        when(kakaoLocalClient.searchKeyword(anyString())).thenReturn(Optional.empty());

        List<CoordinateBackfillResultDto> results = service.backfillMissingCoordinates("ADMIN");

        assertThat(results.get(0).applied()).isFalse();
        assertThat(results.get(0).message()).contains("검색 결과 없음");
    }

    @Test
    void 도로명주소가_있으면_도로명주소로_지역을_검증한다() {
        Festival festival = festival(1L, FestivalRegion.GYEONGGI, "킨텍스");
        when(festivalRepository.findByLatitudeIsNull()).thenReturn(List.of(festival));
        //지번 주소는 다른 표기라도 도로명 주소가 맞으면 통과해야 한다
        when(kakaoLocalClient.searchKeyword(anyString())).thenReturn(Optional.of(
                new KakaoLocalClient.KeywordResult("경기도 고양시 일산서구 대화동", "경기 고양시 일산서구 킨텍스로 217", 37.67, 126.75)));

        List<CoordinateBackfillResultDto> results = service.backfillMissingCoordinates("ADMIN");

        assertThat(results.get(0).applied()).isTrue();
    }

    @Test
    void 상세주소가_없으면_검색_없이_건너뛴다() {
        Festival festival = festival(1L, FestivalRegion.SEOUL, "");
        when(festivalRepository.findByLatitudeIsNull()).thenReturn(List.of(festival));

        List<CoordinateBackfillResultDto> results = service.backfillMissingCoordinates("ADMIN");

        assertThat(results.get(0).applied()).isFalse();
        assertThat(results.get(0).message()).contains("상세주소 없음");
    }

    @Test
    void ADMIN이_아니면_FORBIDDEN_ADMIN_ROLE() {
        assertThatThrownBy(() -> service.backfillMissingCoordinates("HOST"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
    }
}
