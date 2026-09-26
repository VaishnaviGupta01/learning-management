package com.lms.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPrincipal loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
