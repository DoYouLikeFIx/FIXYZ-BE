package com.fix.channel.service;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Notification;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OtpVerification;
import com.fix.channel.entity.SecurityEvent;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.NotificationRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.repository.OtpVerificationRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.vo.AdminSecurityEventCommand;
import com.fix.channel.vo.AdminSecurityEventResult;
import com.fix.channel.vo.CsrfBootstrapCommand;
import com.fix.channel.vo.CsrfBootstrapResult;
import com.fix.channel.vo.NotificationItemVo;
import com.fix.channel.vo.NotificationStreamCommand;
import com.fix.channel.vo.NotificationStreamResult;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.channel.vo.OtpVerifyCommand;
import com.fix.channel.vo.OtpVerifyResult;
import com.fix.channel.vo.SecurityEventItemVo;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelScaffoldService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final OtpVerificationRepository otpVerificationRepository;
  private final OrderSessionRepository orderSessionRepository;
  private final NotificationRepository notificationRepository;
  private final AuditLogRepository auditLogRepository;
  private final SecurityEventRepository securityEventRepository;

  @Transactional(readOnly = true)
  public CsrfBootstrapResult bootstrapCsrf(CsrfBootstrapCommand command, CsrfToken token) {
    return CsrfBootstrapResult.of(token.getToken(), token.getHeaderName(), token.getParameterName(), "SESSION");
  }

  @Transactional
  public OtpVerifyResult verifyOtp(OtpVerifyCommand command) {
    OtpVerification verification = otpVerificationRepository
        .findByMemberId(command.getMemberId(), firstPageByIdDesc(1))
        .stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "otp verification not issued"));

    boolean matched = verification.getOtpCode().equals(command.getOtpCode())
        && verification.getExpiresAt().isAfter(Instant.now());

    if (matched) {
      verification.verify();
      otpVerificationRepository.save(verification);
    } else {
      securityEventRepository.save(SecurityEvent.of(
          command.getMemberId(),
          "OTP_VERIFY_FAILED",
          "0.0.0.0",
          "channel-service",
          "MEDIUM"
      ));
    }

    return OtpVerifyResult.of(matched);
  }

  @Transactional
  public OrderSessionResult createOrderSession(OrderSessionCreateCommand command) {
    OrderSession session = orderSessionRepository.findByClOrdId(command.getClOrdId())
        .orElseGet(() -> orderSessionRepository.save(OrderSession.open(
            command.getMemberId(),
            command.getClOrdId(),
            command.getOrderRef()
        )));

    auditLogRepository.save(AuditLog.of(
        command.getMemberId(),
        "ORDER_SESSION_CREATE",
        "ORDER_SESSION",
        String.valueOf(session.getId()),
        "clOrdId=" + session.getClOrdId()
    ));

    return OrderSessionResult.of(session.getId(), session.getClOrdId(), session.getStatus());
  }

  @Transactional(readOnly = true)
  public OrderSessionResult getOrderSession(OrderSessionQueryCommand command) {
    OrderSession session = null;

    if (command.getSessionId() != null) {
      session = orderSessionRepository.findById(command.getSessionId()).orElse(null);
    }
    if (session == null && command.getClOrdId() != null && !command.getClOrdId().isBlank()) {
      session = orderSessionRepository.findByClOrdId(command.getClOrdId()).orElse(null);
    }
    if (session == null) {
      throw new BusinessException(ErrorCode.CHANNEL_SESSION_NOT_FOUND, "order session not found");
    }

    return OrderSessionResult.of(session.getId(), session.getClOrdId(), session.getStatus());
  }

  @Transactional(readOnly = true)
  public NotificationStreamResult streamNotifications(NotificationStreamCommand command) {
    int limit = resolvePageSize(command.getLimit());
    Pageable pageable = firstPageByIdDesc(limit);
    List<NotificationItemVo> items = (command.getCursorId() == null
        ? notificationRepository.findByMemberId(command.getMemberId(), pageable)
        : notificationRepository.findByMemberIdAndIdLessThan(command.getMemberId(), command.getCursorId(), pageable))
        .stream()
        .map(notification -> NotificationItemVo.of(
            notification.getId(),
            notification.getChannel(),
            notification.getMessage(),
            notification.isDelivered()
        ))
        .toList();

    return NotificationStreamResult.of(items);
  }

  @Transactional(readOnly = true)
  public AdminSecurityEventResult getSecurityEvents(AdminSecurityEventCommand command) {
    int limit = resolvePageSize(command.getLimit());
    Pageable pageable = firstPageByIdDesc(limit);
    List<SecurityEventItemVo> items = (command.getCursorId() == null
        ? securityEventRepository.findByMemberId(command.getMemberId(), pageable)
        : securityEventRepository.findByMemberIdAndIdLessThan(command.getMemberId(), command.getCursorId(), pageable))
        .stream()
        .map(event -> SecurityEventItemVo.of(
            event.getId(),
            event.getEventType(),
            event.getSeverity(),
            event.getIpAddress()
        ))
        .toList();

    return AdminSecurityEventResult.of(items);
  }

  @Transactional
  public void bootstrapNotification(Long memberId, String channel, String message) {
    notificationRepository.save(Notification.pending(memberId, channel, message));
  }

  private int resolvePageSize(Integer requestedLimit) {
    if (requestedLimit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
  }

  private Pageable firstPageByIdDesc(int size) {
    return PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "id"));
  }
}
