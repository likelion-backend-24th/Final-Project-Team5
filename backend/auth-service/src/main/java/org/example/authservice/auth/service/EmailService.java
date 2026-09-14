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

    // 호출자는 DB 커밋을 끝낸 뒤 호출하고, 실패 상태를 별도 트랜잭션으로 기록한다.
    public void sendHelperInvitation(String email, String festivalName, String username,
            java.time.LocalDateTime expiresAt, String link) {
        String escapedName = org.springframework.web.util.HtmlUtils.htmlEscape(festivalName);
        String escapedUsername = org.springframework.web.util.HtmlUtils.htmlEscape(username);
        String escapedLink = org.springframework.web.util.HtmlUtils.htmlEscape(link);
        String plain = "FevalGo 도우미 초대\n" + festivalName + "\n로그인 아이디: " + username
            + "\n만료: " + expiresAt + "\n비밀번호 설정하기: " + link
            + "\n링크는 1회만 사용 가능합니다. 예상하지 못한 메일이면 무시해주세요.";
        String html = "<html><body><h1>FevalGo 도우미 초대</h1><h2>" + escapedName
            + "</h2><p>로그인 아이디: " + escapedUsername + "</p><p>만료: " + expiresAt
            + "</p><p><a style=\"display:inline-block;padding:14px;background:#2563eb;color:white\" href=\""
            + escapedLink + "\">비밀번호 설정하기</a></p><p>링크는 1회만 사용 가능합니다.</p>"
            + "<p>예상하지 못한 메일이면 무시해주세요.</p></body></html>";
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, SENDER_NAME, "UTF-8"));
            helper.setTo(email);
            helper.setSubject("[FevalGo] 도우미 초대");
            helper.setText(plain, html);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // 전송 예외에 수신 주소나 본문이 포함될 수 있어 전달하거나 기록하지 않는다.
            throw new IllegalStateException("도우미 초대 메일 발송 실패");
        }
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
            log.error("메일 발송 실패. 수신 주소와 본문은 기록하지 않습니다.");
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

    //메일 클라이언트는 외부 CSS를 자주 막으므로 인라인 스타일만 쓴다. 이미지는 대부분 클라이언트가 허용하므로
    //로고는 사이트에 이미 올라가 있는 실제 로고 이미지를 그대로 가져와 보여준다.
    private static final String LOGO_URL = SITE_URL + "/brand/logo-horizontal.webp";

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
                            <img src="%s" alt="FevalGo" height="28" style="height:28px;display:block;">
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
                """.formatted(LOGO_URL, code, SITE_URL);
    }

    // 6자리 숫자 코드(우리가 회원가입때 입력해야할 인증코드) 생성-> 보안 강도 높임 secureRandom사용
    private static final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        int number = secureRandom.nextInt(1000000);
        return String.format("%06d", number);
    }

}
