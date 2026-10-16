package com.rockettrading.rocket_trading.service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Applies the application's client provisioning to OpenID Connect users such as Google. */
@Service
public class OAuthOidcUserService extends OidcUserService {

  private final OAuthUserService oauthUserService;

  public OAuthOidcUserService(OAuthUserService oauthUserService) {
    this.oauthUserService = oauthUserService;
  }

  @Override
  public OidcUser loadUser(org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest userRequest) {
    OidcUser oidcUser = super.loadUser(userRequest);
    OAuth2UserRequest oauthRequest = new OAuth2UserRequest(
      userRequest.getClientRegistration(),
      userRequest.getAccessToken()
    );
    var processedUser = oauthUserService.processOAuthUser(oauthRequest, oidcUser);

    Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
    claims.putAll(processedUser.getAttributes());
    return new DefaultOidcUser(
      processedUser.getAuthorities(),
      oidcUser.getIdToken(),
      new OidcUserInfo(claims),
      userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()
    );
  }
}