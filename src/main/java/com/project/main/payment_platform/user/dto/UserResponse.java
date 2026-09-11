package com.project.main.payment_platform.user.dto;


import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private UserStatus status;
    private OffsetDateTime createdAt;
}
