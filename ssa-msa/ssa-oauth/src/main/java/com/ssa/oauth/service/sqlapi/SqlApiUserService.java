package com.ssa.oauth.service.sqlapi;

import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.ApiResult;
import com.ssa.oauth.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqlApiUserService {

    @Value("${ssa.oauth.sqlapi.user-by-identifier:user.login}")
    private String userByIdentifierApi;

    @Value("${ssa.oauth.sqlapi.user-by-id:user.getUserInfo}")
    private String userByIdApi;

    public Optional<SysUser> loadUserByIdentifier(String identifier) {
        ApiResult<Object> result = ApiRunner.safeRun(userByIdentifierApi, Map.of("username", identifier), false);
        return mapSingleUser(result);
    }

    public Optional<SysUser> loadUserById(String userId) {
        ApiResult<Object> result = ApiRunner.safeRun(userByIdApi, Map.of("user_id", userId), false);
        return mapSingleUser(result);
    }

    private Optional<SysUser> mapSingleUser(ApiResult<Object> result) {
        if (result == null || !result.isOk() || result.getData() == null) {
            if (result != null && !result.isOk()) {
                log.warn("sqlapi user query failed: {}", result.getMsg());
            }
            return Optional.empty();
        }
        if (!(result.getData() instanceof Map<?, ?> raw)) {
            log.warn("sqlapi user query result not a map: {}", result.getData().getClass());
            return Optional.empty();
        }
        return Optional.of(mapToUser(raw));
    }

    private SysUser mapToUser(Map<?, ?> raw) {
        SysUser user = new SysUser();
        user.setUserId(getString(raw, "user_id"));
        user.setUsername(getString(raw, "username"));
        user.setPassword(getString(raw, "password"));
        user.setNickName(getString(raw, "nickname"));
        if (user.getNickName() == null) {
            user.setNickName(getString(raw, "nick_name"));
        }
        user.setRealName(getString(raw, "real_name"));
        user.setPhone(getString(raw, "phone"));
        if (user.getPhone() == null) {
            user.setPhone(getString(raw, "mobile"));
        }
        user.setEmail(getString(raw, "email"));
        user.setAvatar(getString(raw, "avatar"));
        user.setGender(getString(raw, "gender"));
        user.setStatus(parseStatus(raw));
        return user;
    }

    private String getString(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean parseStatus(Map<?, ?> raw) {
        Object value = raw.get("status");
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }
}
