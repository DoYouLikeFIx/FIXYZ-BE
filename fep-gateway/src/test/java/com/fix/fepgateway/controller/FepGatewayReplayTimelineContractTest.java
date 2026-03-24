package com.fix.fepgateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.web.CommonHeaders;
import com.fix.fepgateway.entity.ReplayCursor;
import com.fix.fepgateway.repository.ReplayCursorRepository;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewayReplayTimelineContractTest {

  private static final String EMPTY_SEQUENCE_HASH =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ReplayCursorRepository replayCursorRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    replayCursorRepository.deleteAllInBatch();
  }

  @Test
  void shouldStartReplayTimelineWithRequestedSeedOffsetAndSpeed() throws Exception {
    String replayId = replayIdFor("timeline-seed-001", "005930");

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-start")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "symbol": "005930",
                  "seed": "timeline-seed-001",
                  "startOffset": 5,
                  "speedFactor": 1.5
                }
                """))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-replay-start"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.replayId").value(replayId))
        .andExpect(jsonPath("$.data.symbol").value("005930"))
        .andExpect(jsonPath("$.data.seed").value("timeline-seed-001"))
        .andExpect(jsonPath("$.data.cursorOffset").value(5))
        .andExpect(jsonPath("$.data.speedFactor").value(1.5))
        .andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andExpect(jsonPath("$.data.emittedCount").value(0))
        .andExpect(jsonPath("$.data.sequenceHash").value(EMPTY_SEQUENCE_HASH));
  }

  @Test
  void shouldReturnRunningReplayTimelineStatusForActiveStream() throws Exception {
    String replayId = replayIdFor("timeline-seed-002", "000660");
    long startOffset = 2L;

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines")
        .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
        .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-start-2")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "symbol": "000660",
              "seed": "timeline-seed-002",
              "startOffset": 2,
              "speedFactor": 1.0
            }
            """));

    MvcResult result = mockMvc.perform(get("/fep-internal/v1/market-data/replay/timelines/{replayId}",
                replayId)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-status"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-replay-status"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.replayId").value(replayId))
        .andExpect(jsonPath("$.data.symbol").value("000660"))
        .andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    long cursorOffset = data.path("cursorOffset").asLong();
    long emittedCount = data.path("emittedCount").asLong();

    assertThat(cursorOffset).isGreaterThanOrEqualTo(startOffset);
    assertThat(emittedCount).isEqualTo(cursorOffset - startOffset);
  }

  @Test
  void shouldPauseAndResumeReplayTimeline() throws Exception {
    String replayId = replayIdFor("timeline-seed-003", "005930");

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-start-3")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "symbol": "005930",
                  "seed": "timeline-seed-003",
                  "startOffset": 1,
                  "speedFactor": 1.0
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines/{replayId}/pause", replayId)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-pause"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAUSED"));

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines/{replayId}/resume", replayId)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-resume"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RUNNING"));
  }

  @Test
  void shouldResumePersistedPausedTimelineWithoutActiveRuntimeStream() throws Exception {
    String replayId = replayIdFor("timeline-seed-004", "005930");
    ReplayCursor replayCursor = ReplayCursor.running(
        replayId,
        "timeline-seed-004",
        "005930",
        new BigDecimal("1.2500")
    );
    replayCursor.moveTo(7L);
    replayCursor.changeStatus("PAUSED");
    replayCursorRepository.saveAndFlush(replayCursor);

    mockMvc.perform(post("/fep-internal/v1/market-data/replay/timelines/{replayId}/resume", replayId)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-replay-resume-persisted"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-replay-resume-persisted"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.replayId").value(replayId))
        .andExpect(jsonPath("$.data.cursorOffset").value(7))
        .andExpect(jsonPath("$.data.speedFactor").value(1.25))
        .andExpect(jsonPath("$.data.status").value("RUNNING"))
        .andExpect(jsonPath("$.data.emittedCount").value(0))
        .andExpect(jsonPath("$.data.sequenceHash").value(EMPTY_SEQUENCE_HASH));
  }

  private String replayIdFor(String seed, String symbol) {
    return UUID.nameUUIDFromBytes((seed + "|" + symbol).getBytes(StandardCharsets.UTF_8)).toString();
  }
}
