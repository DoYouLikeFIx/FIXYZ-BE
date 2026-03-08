package com.fix.channel.vo;

public class MemberProfileUpdateCommand {

  private final String name;

  private MemberProfileUpdateCommand(String name) {
    this.name = name;
  }

  public static MemberProfileUpdateCommand of(String name) {
    return new MemberProfileUpdateCommand(name);
  }

  public String getName() {
    return name;
  }
}
