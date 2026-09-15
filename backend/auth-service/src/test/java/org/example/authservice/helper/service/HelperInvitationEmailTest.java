package org.example.authservice.helper.service;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.example.authservice.auth.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Properties;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class HelperInvitationEmailTest {
    @Test void escapesDynamicHtmlAndIncludesPlainTextAlternative() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailService service = new EmailService(sender);
        ReflectionTestUtils.setField(service, "fromEmail", "sender@example.com");
        service.sendHelperInvitation("person@example.com", "<script>unsafe</script> & 행사", "helper-test@helper.local",
            LocalDateTime.of(2030, 5, 1, 12, 0), "https://example.com/helper-invite/test");
        verify(sender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).contains("FevalGo", "도우미 초대");
        String html = findPart(message.getContent(), "text/html");
        assertThat(html).contains("&lt;script&gt;", "&amp;", "비밀번호 설정하기", "helper-test@helper.local", "1회").doesNotContain("<script>");
        assertThat(findPart(message.getContent(), "text/plain")).contains("예상하지 못한", "https://example.com/helper-invite/test");
    }
    @Test void mailErrorsDoNotExposeRecipientOrLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new IllegalStateException("sensitive-link-and-recipient"));
        EmailService service = new EmailService(sender);
        assertThatThrownBy(() -> service.sendHelperInvitation("person@example.com", "행사", "helper-test@helper.local", LocalDateTime.now(), "https://example.com/secret"))
            .isInstanceOf(IllegalStateException.class).hasMessage("도우미 초대 메일 발송 실패").hasNoCause();
    }
    private String findPart(Object content, String type) throws Exception {
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                var part = multipart.getBodyPart(i);
                if (part.isMimeType(type)) return (String) part.getContent();
                String found = findPart(part.getContent(), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
