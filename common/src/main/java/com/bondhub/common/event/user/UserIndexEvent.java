package com.bondhub.common.event.user;

import com.bondhub.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIndexEvent {
    private String userId;
    private String phoneNumber;
    private Role role;
}
