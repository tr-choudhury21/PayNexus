package com.project.main.payment_platform.user.service;

import com.project.main.payment_platform.user.dto.RegisterUserRequest;
import com.project.main.payment_platform.user.dto.UserResponse;

public interface UserService {

    UserResponse registerUser(RegisterUserRequest request);
}
