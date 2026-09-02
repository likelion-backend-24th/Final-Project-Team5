package org.example.festivalservice.domain.festival;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalService {
    private final FestivalRepository festivalRepository;

    //새 페스티벌(및 티켓 종류) 등록
    @Transactional
    public FestivalResponseDto createFestival(FestivalRequestDto dto){
        return null;
    }


}
