package com.ssa.oauth.service.sqlapi;

import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqlApiPermissionService {

    @Value("${ssa.oauth.sqlapi.user-permissions:user.permissions}")
    private String userPermissionsApi;

    @Value("${ssa.oauth.sqlapi.user-roles:user.roles}")
    private String userRolesApi;

    public boolean hasPermission(String userId, String permissionCode) {
        return getUserPermissions(userId).stream().anyMatch(permissionCode::equals);
    }

    public boolean hasAnyPermission(String userId, List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return false;
        }
        List<String> userPermissions = getUserPermissions(userId);
        for (String code : permissionCodes) {
            if (userPermissions.contains(code)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(String userId, List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return false;
        }
        List<String> userPermissions = getUserPermissions(userId);
        return userPermissions.containsAll(permissionCodes);
    }

    public List<String> getUserPermissions(String userId) {
        ApiResult<Object> result = ApiRunner.safeRun(userPermissionsApi, Map.of("user_id", userId), false);
        return mapList(result, "permission_code");
    }

    public boolean hasRole(String userId, String roleCode) {
        return getUserRoles(userId).stream().anyMatch(roleCode::equals);
    }

    public List<String> getUserRoles(String userId) {
        ApiResult<Object> result = ApiRunner.safeRun(userRolesApi, Map.of("user_id", userId), false);
        return mapList(result, "role_code");
    }

    private List<String> mapList(ApiResult<Object> result, String key) {
        if (result == null || !result.isOk() || result.getData() == null) {
            if (result != null && !result.isOk()) {
                log.warn("sqlapi query failed: {}", result.getMsg());
            }
            return List.of();
        }
        Object data = result.getData();
        if (data instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof Map<?, ?> mapItem) {
                    Object value = mapItem.get(key);
                    if (value != null) {
                        values.add(String.valueOf(value));
                    }
                }
            }
            return values;
        }
        log.warn("sqlapi list result not a collection: {}", data.getClass());
        return List.of();
    }
}

