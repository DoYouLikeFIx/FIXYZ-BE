package com.fix.channel.client;

public interface CorebankProvisioningClient {

  void provisionDefaultAccount(Long memberId, String memberNo, String email, String correlationId);
}
