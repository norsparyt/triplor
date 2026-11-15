package com.triplor.common.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.triplor.common.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Builds a {@link CurrentUser} from the JWT that Spring Security has already verified. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required");
        }
        Jwt jwt = token.getToken();
        List<String> roles = jwt.getClaimAsStringList(TriplorClaims.ROLES);
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                roles == null ? Set.of() : Set.copyOf(roles),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(TriplorClaims.VERIFIED)));
    }
}
