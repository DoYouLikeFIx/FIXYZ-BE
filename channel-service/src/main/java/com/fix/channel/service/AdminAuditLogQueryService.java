package com.fix.channel.service;

import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.Member;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.MemberRepository;
import com.fix.channel.vo.AdminAuditLogItemVo;
import com.fix.channel.vo.AdminAuditLogQueryCommand;
import com.fix.channel.vo.AdminAuditLogQueryResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditLogQueryService {

  private final AuditLogRepository auditLogRepository;
  private final MemberRepository memberRepository;
  private final AdminAuditActionMapper adminAuditActionMapper;

  public AdminAuditLogQueryService(
      AuditLogRepository auditLogRepository,
      MemberRepository memberRepository,
      AdminAuditActionMapper adminAuditActionMapper
  ) {
    this.auditLogRepository = auditLogRepository;
    this.memberRepository = memberRepository;
    this.adminAuditActionMapper = adminAuditActionMapper;
  }

  public AdminAuditLogQueryResult query(AdminAuditLogQueryCommand command) {
    Pageable pageable = PageRequest.of(
        command.getPage(),
        command.getSize(),
        Sort.by(Sort.Direction.DESC, "createdAt")
    );

    List<String> storedActions = adminAuditActionMapper.storedActionsForCanonical(command.getEventType());
    List<String> actionFilter = (command.getEventType() != null && !command.getEventType().isBlank())
      ? storedActions
      : null;
    Page<AuditLog> page = auditLogRepository.findAdminAuditLogs(
      pageable,
      command.getFrom(),
      command.getTo(),
      command.getMemberId(),
      actionFilter
    );
    Map<Long, Member> memberMap = resolveMembers(page.getContent());

    List<AdminAuditLogItemVo> content = page.getContent().stream()
        .map(log -> toItem(log, memberMap.get(log.getMemberId())))
        .toList();
    return AdminAuditLogQueryResult.of(
        content,
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize()
    );
  }

  private Map<Long, Member> resolveMembers(List<AuditLog> logs) {
    Set<Long> memberIds = new HashSet<>();
    for (AuditLog log : logs) {
      if (log.getMemberId() != null) {
        memberIds.add(log.getMemberId());
      }
    }
    Map<Long, Member> members = new HashMap<>();
    if (memberIds.isEmpty()) {
      return members;
    }
    for (Member member : memberRepository.findAllById(memberIds)) {
      members.put(member.getId(), member);
    }
    return members;
  }

  private AdminAuditLogItemVo toItem(AuditLog log, Member member) {
    return AdminAuditLogItemVo.of(
        log.getAuditUuid(),
        log.getMemberId(),
        member == null ? null : member.getMemberNo(),
        member == null ? null : member.getEmail(),
        adminAuditActionMapper.canonicalize(log.getAction()),
        log.getIpAddress(),
        log.getUserAgent(),
        log.getDetail(),
        extractClOrdId(log.getDetail()),
        log.getOrderSessionId(),
        log.getCreatedAt()
    );
  }

  private String extractClOrdId(String detail) {
    if (detail == null || detail.isBlank()) {
      return null;
    }
    String marker = "clOrdId=";
    int start = detail.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
    if (start < 0) {
      return null;
    }
    String raw = detail.substring(start + marker.length());
    int commaIndex = raw.indexOf(',');
    String value = commaIndex >= 0 ? raw.substring(0, commaIndex) : raw;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
