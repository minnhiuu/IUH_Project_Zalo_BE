package com.bondhub.common.event.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRegisteredEvent {
    private String accountId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Long timestamp;
}
