package com.rockettrading.rocket_trading.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response returned by OAuth callback endpoint
 * Frontend uses this to extract user info after GitHub OAuth redirect
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthLoginResponse(
        Boolean success,
        Long clientId,
        String email,
        String name,
        String accessToken,
        String tokenType,
        Long expiresIn,
        String provider,
        String error
) {
    /**
     * Create success response with user info
     */
    public static OAuthLoginResponse success(Long clientId, String email, String name, 
                                            String accessToken, Long expiresIn) {
        return new OAuthLoginResponse(
                true,
                clientId,
                email,
                name,
                accessToken,
                "Bearer",
                expiresIn,
                "github",
                null
        );
    }

    /**
     * Create error response
     */
    public static OAuthLoginResponse error(String error) {
        return new OAuthLoginResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                error
        );
    }
}
