package org.example.paymentservice.domain.settlement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.example.paymentservice.domain.payment.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SettlementContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void easyPayOfficialShape() throws Exception {
        var response = mapper.readValue("""
                {"method":{"type":"EASY_PAY","provider":"KAKAOPAY","easyPayMethod":{"type":"CARD","card":{"brand":"LOCAL"}}}}
                """, PortOnePaymentResponse.class);
        assertThat(response.method().provider()).isEqualTo("KAKAOPAY");
        assertThat(response.method().easyPayMethod().type()).isEqualTo("CARD");
        var payment = Payment.builder().ticketAmount(100000).build();
        payment.snapshotApproval(response.method().type(), response.method().provider(), java.time.Instant.now(), true);
        assertThat(payment.getPayMethodCategory()).isEqualTo(PaymentMethodCategory.EASY_PAY);
        assertThat(payment.getPlatformFee()).isEqualTo(7500);
        assertThat(payment.totalAmount()).isEqualTo(100000);
    }
    @Test void reservationContractRetainsSettlementFields() throws Exception {
        var r = mapper.readValue("""
                {"reservationId":1,"userId":2,"status":"CONFIRMED","totalAmount":10000,"ticketTypeId":3,
                 "quantity":2,"expiresAt":"2026-01-01T00:00:00Z","festivalId":4,"hostUserId":5,
                 "unitPrice":5000,"refundedQuantity":1,"paymentId":"p1"}
                """, ReservationForPaymentResponse.class);
        assertThat(r.festivalId()).isEqualTo(4); assertThat(r.hostUserId()).isEqualTo(5);
        assertThat(r.unitPrice()).isEqualTo(5000); assertThat(r.refundedQuantity()).isEqualTo(1);
    }
}
