package com.fix.common.validation;

public final class ContractPatterns {

  public static final String UUID_V4 =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
  public static final String SIX_DIGIT_SYMBOL = "^\\d{6}$";
  public static final int REPLAY_REASON_MIN_LENGTH = 30;

  private ContractPatterns() {
  }

  public static boolean isUuidV4(String value) {
    return value != null && value.matches(UUID_V4);
  }
}
