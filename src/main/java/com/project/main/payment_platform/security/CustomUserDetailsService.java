package com.project.main.payment_platform.security;

import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class CustomUserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetails loadUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        return new CustomUserDetails(user);
    }
}
