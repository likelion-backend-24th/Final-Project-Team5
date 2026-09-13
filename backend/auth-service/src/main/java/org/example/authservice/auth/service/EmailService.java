package org.example.authservice.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;


// 실제 이메일을 보내는 역할의 클래스이다!!
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String SENDER_NAME = "FevalGo";
    private static final String SITE_URL = "https://fevalgo.duckdns.org";

    // JavaMailSender 객체를 자바가 자동으로 등록
    private final JavaMailSender javaMailSender;


    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendVerificationCode(String toEmail, String code){
        String subject = "[FevalGo] 이메일 인증코드입니다.";
        send(toEmail, subject, buildPlainText(code), buildHtml(code));
    }

    //순수 텍스트만 보내면 스팸 판정을 받기 쉬워서, 텍스트+HTML을 함께 담은 multipart/alternative로 보낸다.
    //텍스트 본문은 HTML을 못 여는 메일 클라이언트용 대체본이다.
    private void send(String toEmail, String subject, String plainText, String html) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, SENDER_NAME, "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // 비동기라 호출자에게 예외가 전달되지 않는다. 추적 수단은 이 로그뿐이다.
            log.error("메일 발송 실패. to={}, subject={}", toEmail, subject, e);
        }
    }

    private String buildPlainText(String code) {
        return "FevalGo 회원가입 및 비밀번호 재설정을 위한 이메일 인증코드입니다.\n" +
                "인증 화면에 아래의 코드를 입력해 주시기 바랍니다.\n" +
                "인증코드: " + code + "\n\n" +
                "-인증코드 유효기간 안내\n" +
                "● 인증 코드는 1회만 유효합니다.\n" +
                "● 인증 코드는 메일이 발송된 후 5분이 지나면 만료됩니다.\n" +
                "● 인증 코드를 재전송하시는 경우, 이전 인증 코드를 사용하실 수 없습니다.\n\n" +
                "본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.\n" +
                "-------------------------------------------------------\n" +
                "FevalGo 홈페이지: " + SITE_URL;
    }

    //메일 클라이언트는 외부 CSS·이미지를 자주 막으므로 인라인 스타일만 쓰고, 로고도 이미지 대신 텍스트로 넣는다.
    private String buildHtml(String code) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:'Apple SD Gothic Neo','Malgun Gothic',Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f4f6;padding:32px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellspacing="0" cellpadding="0" style="max-width:480px;width:100%%;background:#ffffff;border-radius:16px;overflow:hidden;">
                        <tr>
                          <td style="background:#101a44;padding:20px 28px;">
                            <span style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.3px;">FevalGo</span>
                            <span style="font-size:12px;color:#c7d2fe;margin-left:8px;">페스티벌·행사 예약 플랫폼</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 28px 8px 28px;">
                            <p style="margin:0 0 8px 0;font-size:18px;font-weight:700;color:#111827;">이메일 인증코드</p>
                            <p style="margin:0;font-size:14px;line-height:1.6;color:#4b5563;">
                              FevalGo 회원가입 및 비밀번호 재설정을 위한 인증코드입니다.<br>
                              인증 화면에 아래 코드를 입력해 주세요.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 28px;">
                            <div style="background:#eef2ff;border:1px solid #c7d2fe;border-radius:12px;padding:18px;text-align:center;">
                              <span style="font-size:32px;font-weight:800;letter-spacing:8px;color:#2563eb;">%s</span>
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:4px 28px 24px 28px;">
                            <p style="margin:0 0 6px 0;font-size:13px;font-weight:700;color:#374151;">인증코드 유효기간 안내</p>
                            <ul style="margin:0;padding-left:18px;font-size:13px;line-height:1.7;color:#6b7280;">
                              <li>인증 코드는 1회만 유효합니다.</li>
                              <li>메일이 발송된 후 5분이 지나면 만료됩니다.</li>
                              <li>재전송하시면 이전 인증 코드는 사용할 수 없습니다.</li>
                            </ul>
                            <p style="margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#9ca3af;">
                              본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background:#f9fafb;border-top:1px solid #e5e7eb;padding:14px 28px;font-size:12px;color:#9ca3af;">
                            <a href="%s" style="color:#2563eb;text-decoration:none;">FevalGo 홈페이지</a> · 이 메일은 발신 전용입니다.
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(code, SITE_URL);
    }

    // 6자리 숫자 코드(우리가 회원가입때 입력해야할 인증코드) 생성-> 보안 강도 높임 secureRandom사용
    private static final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        int number = secureRandom.nextInt(1000000);
        return String.format("%06d", number);
    }

}
