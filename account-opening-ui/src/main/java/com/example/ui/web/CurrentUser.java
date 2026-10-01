package com.example.ui.web;

import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Username + Kogito group membership (FOSS/KYC/CDD/BSA) from the "groups" claim Keycloak adds to the ID token. */
public record CurrentUser(String username, List<String> groups) {

    public static CurrentUser from(OidcUser oidcUser) {
        String username = Optional.ofNullable(oidcUser.getPreferredUsername()).orElseGet(oidcUser::getSubject);
        List<String> groups = oidcUser.getClaimAsStringList("groups");
        return new CurrentUser(username, groups != null ? groups : List.of());
    }
}
