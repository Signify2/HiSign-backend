package com.example.backend.mail.support;

import com.example.backend.auth.dto.AuthDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MailSenderResolver {

    public String getLoggedInUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthDto)) {
            return null;
        }

        AuthDto authDto = (AuthDto) principal;
        return StringUtils.hasText(authDto.getName()) ? authDto.getName().trim() : null;
    }

    public String resolveDisplayName(String fallbackName) {
        String loggedInUserName = getLoggedInUserName();
        if (StringUtils.hasText(loggedInUserName)) {
            return loggedInUserName;
        }

        if (StringUtils.hasText(fallbackName)) {
            return fallbackName.trim();
        }

        return "HISign";
    }
}
