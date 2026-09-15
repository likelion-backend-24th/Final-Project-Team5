package org.example.paymentservice.domain.settlement;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;

class SettlementSchedulerTest {
    @Test void schedulesOneLedgerPerFestivalRegardlessOfChannel() {
        var service = mock(SettlementService.class); var repository = mock(SettlementRepository.class); var client = mock(FestivalSettlementClient.class);
        when(repository.findByStatusIn(any())).thenReturn(List.of());
        when(client.candidates(0)).thenReturn(List.of(new FestivalSettlementClient.Context(42L, 10L, "행사", Instant.EPOCH, "CLOSED")));
        new SettlementScheduler(service, repository, client).run();
        verify(service).calculateFestival(42L, false);
        verify(service, never()).calculateFestival(anyLong(), eq(true));
    }
}
