package com.bondhub.common.event.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private String userId;
    private String accountId;
    private String fullName;
    private String bio;
    private Set<String> initialInterests;
    private LocalDate dob;
    private String gender;
    private Long timestamp;
}
