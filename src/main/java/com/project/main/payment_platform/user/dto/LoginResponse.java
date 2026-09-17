package com.project.main.payment_platform.user.dto;

import com.project.main.payment_platform.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private String token;
}
