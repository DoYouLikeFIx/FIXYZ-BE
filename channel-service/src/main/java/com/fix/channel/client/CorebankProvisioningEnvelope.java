package com.fix.channel.client;

public class CorebankProvisioningEnvelope {

  private Boolean success;
  private CorebankProvisioningData data;

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public CorebankProvisioningData getData() {
    return data;
  }

  public void setData(CorebankProvisioningData data) {
    this.data = data;
  }

  public static class CorebankProvisioningData {

    private Long accountId;
    private String accountNumber;
    private String status;
    private Boolean idempotent;
    private Long memberId;

    public Long getAccountId() {
      return accountId;
    }

    public void setAccountId(Long accountId) {
      this.accountId = accountId;
    }

    public String getAccountNumber() {
      return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
      this.accountNumber = accountNumber;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public Boolean getIdempotent() {
      return idempotent;
    }

    public void setIdempotent(Boolean idempotent) {
      this.idempotent = idempotent;
    }

    public Long getMemberId() {
      return memberId;
    }

    public void setMemberId(Long memberId) {
      this.memberId = memberId;
    }
  }
}
