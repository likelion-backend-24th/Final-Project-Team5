package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.domain.festival.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/internal/v1/festivals")
public class SettlementContextController {
    private final FestivalRepository repository;
    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}") private String token;
    public record Context(Long festivalId, Long hostUserId, String name, Instant eligibleAt, String status) {
        static Context from(Festival f) {
            return new Context(f.getId(), f.getHostUserId(), f.getName(),
                    f.getEndAt().atZone(ZoneId.of("Asia/Seoul")).toInstant().plus(Duration.ofHours(24)),
                    f.getFestivalStatus().name());
        }
    }
    @GetMapping("/settlement-candidates")
    public List<Context> candidates(@RequestHeader("Authorization") String auth,
                                    @RequestParam(defaultValue = "0") int page) {
        verify(auth);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return repository.findSettlementCandidates(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(24),
                PageRequest.of(page, 100)).stream().map(Context::from).toList();
    }
    @GetMapping("/{id}/settlement-context")
    public Context context(@PathVariable Long id, @RequestHeader("Authorization") String auth) {
        verify(auth);
        return Context.from(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
    private void verify(String auth) {
        if (!auth.equals("Bearer " + token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
