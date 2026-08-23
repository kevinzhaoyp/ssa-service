package com.ssa.apis.security.store;

import com.ssa.apis.security.constant.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内存 Token 存储替代 Redis
 * MVP 版本：用 ConcurrentHashMap + ScheduledExecutor 实现 TTL 过期
 */
@Component
@Slf4j
public class InMemoryTokenStore {

    // 存储 key -> value（带过期时间）
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // 角色权限缓存：roleCode -> Set<permCode>
    private final ConcurrentHashMap<String, Set<String>> rolePermsCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupExecutor;

    public InMemoryTokenStore() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-store-cleanup");
            t.setDaemon(true);
            return t;
        });
        // 每 60 秒清理过期 entry
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 60, 60, TimeUnit.SECONDS);
    }

    private static class CacheEntry {
        final String value;
        final long expireAt; // 0 = 永不过期
        CacheEntry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
        boolean isExpired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }

    // ========== Blacklist ==========

    public boolean isBlacklisted(String jti) {
        CacheEntry entry = cache.get(SecurityConstants.BLACKLIST_TOKEN_PREFIX + jti);
        if (entry == null) return false;
        if (entry.isExpired()) {
            cache.remove(SecurityConstants.BLACKLIST_TOKEN_PREFIX + jti);
            return false;
        }
        return true;
    }

    public void blacklist(String jti, long ttlSeconds) {
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        cache.put(SecurityConstants.BLACKLIST_TOKEN_PREFIX + jti, new CacheEntry("1", expireAt));
    }

    public void blacklistPermanent(String jti) {
        cache.put(SecurityConstants.BLACKLIST_TOKEN_PREFIX + jti, new CacheEntry("1", 0));
    }

    // ========== Refresh Token ==========

    public void saveRefreshToken(String token, String username, long ttlSeconds) {
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        cache.put(SecurityConstants.REFRESH_TOKEN_PREFIX + token, new CacheEntry(username, expireAt));
    }

    public String getRefreshToken(String token) {
        CacheEntry entry = cache.get(SecurityConstants.REFRESH_TOKEN_PREFIX + token);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(SecurityConstants.REFRESH_TOKEN_PREFIX + token);
            return null;
        }
        return entry.value;
    }

    public void deleteRefreshToken(String token) {
        cache.remove(SecurityConstants.REFRESH_TOKEN_PREFIX + token);
    }

    // ========== Role Perms Cache ==========

    public Set<String> getRolePerms(Set<String> roleCodes) {
        Set<String> perms = new HashSet<>();
        for (String roleCode : roleCodes) {
            Set<String> rolePerms = rolePermsCache.get(roleCode);
            if (rolePerms != null) {
                perms.addAll(rolePerms);
            }
        }
        return perms;
    }

    public void putRolePerms(String roleCode, Set<String> perms) {
        rolePermsCache.put(roleCode, perms);
    }

    // ========== Cleanup ==========

    private void cleanupExpired() {
        int count = 0;
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            log.debug("清理过期 token 缓存: {} 条", count);
        }
    }
}
