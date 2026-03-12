package com.fix.channel.client;

public interface CorebankProvisioningClient {

  CorebankLinkedAccountProfile provisionDefaultAccount(Long memberId, String memberNo, String email, String correlationId);

  CorebankLinkedAccountProfile fetchDefaultAccountProfile(Long memberId, String correlationId);
}
