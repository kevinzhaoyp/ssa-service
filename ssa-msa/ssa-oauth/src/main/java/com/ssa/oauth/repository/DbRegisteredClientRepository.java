package com.ssa.oauth.repository;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
//@DependsOn("initActiveRecordPlugin")
public class DbRegisteredClientRepository implements RegisteredClientRepository {

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("JfinalRegisteredClientRepository does not support save().");
    }

    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        Record record = Db.findFirst(
                "select client_id, client_secret, redirect_uris, grant_types, scopes, " +
                        "access_token_validity, refresh_token_validity, status " +
                        "from sso_client where client_id = ?", clientId);
        if (record == null) {
            return null;
        }
        Integer status = record.getInt("status");
        if (status != null && status == 0) {
            return null;
        }
        return mapToRegisteredClient(record);
    }

    private RegisteredClient mapToRegisteredClient(Record record) {
        String clientId = record.getStr("client_id");
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        String clientSecret = record.getStr("client_secret");
        String redirectUris = record.getStr("redirect_uris");
        String grantTypes = record.getStr("grant_types");
        String scopes = record.getStr("scopes");
        Integer accessTtl = record.getInt("access_token_validity");
        Integer refreshTtl = record.getInt("refresh_token_validity");

        RegisteredClient.Builder builder = RegisteredClient.withId(clientId)
                .clientId(clientId);

        if (clientSecret != null && !clientSecret.isEmpty()) {
            builder.clientSecret(clientSecret);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }

        for (String uri : splitList(redirectUris)) {
            builder.redirectUri(uri);
        }

        for (AuthorizationGrantType grantType : mapGrantTypes(splitList(grantTypes))) {
            builder.authorizationGrantType(grantType);
        }

        for (String scope : splitList(scopes)) {
            builder.scope(scope);
        }

        TokenSettings.Builder tokenSettings = TokenSettings.builder();
        if (accessTtl != null && accessTtl > 0) {
            tokenSettings.accessTokenTimeToLive(Duration.ofSeconds(accessTtl));
        }
        if (refreshTtl != null && refreshTtl > 0) {
            tokenSettings.refreshTokenTimeToLive(Duration.ofSeconds(refreshTtl));
        }

        builder.tokenSettings(tokenSettings.build());
        builder.clientSettings(ClientSettings.builder().build());
        return builder.build();
    }

    private List<String> splitList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return List.of();
        }
        String[] parts = raw.split("[\\n,]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    private Set<AuthorizationGrantType> mapGrantTypes(List<String> types) {
        Set<AuthorizationGrantType> result = new HashSet<>();
        for (String type : types) {
            String value = type.toLowerCase();
            switch (value) {
                case "authorization_code" -> result.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                case "refresh_token" -> result.add(AuthorizationGrantType.REFRESH_TOKEN);
                case "client_credentials" -> result.add(AuthorizationGrantType.CLIENT_CREDENTIALS);
//                case "password" -> result.add(AuthorizationGrantType.PASSWORD);
                default -> result.add(new AuthorizationGrantType(type));
            }
        }
        return result;
    }
}

