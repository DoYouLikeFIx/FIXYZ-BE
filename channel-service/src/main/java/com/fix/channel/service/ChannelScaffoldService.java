package com.fix.channel.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fix.channel.entity.Notification;
import com.fix.channel.repository.NotificationRepository;
import com.fix.channel.repository.SecurityEventRepository;
import com.fix.channel.vo.AdminSecurityEventCommand;
import com.fix.channel.vo.AdminSecurityEventResult;
import com.fix.channel.vo.CsrfBootstrapCommand;
import com.fix.channel.vo.CsrfBootstrapResult;
import com.fix.channel.vo.NotificationItemVo;
import com.fix.channel.vo.NotificationStreamCommand;
import com.fix.channel.vo.NotificationStreamResult;
import com.fix.channel.vo.SecurityEventItemVo;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChannelScaffoldService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final NotificationRepository notificationRepository;
  private final SecurityEventRepository securityEventRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public CsrfBootstrapResult bootstrapCsrf(CsrfBootstrapCommand command, CsrfToken token) {
    return CsrfBootstrapResult.of(token.getToken(), token.getHeaderName(), token.getParameterName(), "SESSION");
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
            notification.isDelivered(),
            notification.getReadAt()
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
    Objects.requireNonNull(notificationRepository.save(Notification.pending(memberId, channel, message)));
  }

  @Transactional
  public NotificationItemVo markNotificationRead(Long memberId, Long notificationId) {
    Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_OWNERSHIP_MISMATCH, "access denied"));
    notification.markRead(Instant.now(clock));
    return NotificationItemVo.of(
        notification.getId(),
        notification.getChannel(),
        notification.getMessage(),
        notification.isDelivered(),
        notification.getReadAt()
    );
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
