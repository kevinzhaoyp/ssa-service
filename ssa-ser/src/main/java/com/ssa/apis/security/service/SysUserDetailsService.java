package com.ssa.apis.security.service;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.ssa.apis.security.model.SysUserDetails;
import com.ssa.apis.security.model.dto.UserAuthInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统用户认证
 *
 * @author haoxr
 */
@Service
@RequiredArgsConstructor
public class SysUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? null : username.trim().toLowerCase();
        if (normalized == null || normalized.isEmpty()) {
            throw new UsernameNotFoundException("empty username");
        }

        Record rec = Db.use().findFirst("select * from sys_user where username_lower = ?", normalized);
        if (rec == null) {
            throw new UsernameNotFoundException("not found the user:" + username);
        }

        Integer statusVal = rec.getInt("status");
        boolean status = statusVal != null && statusVal == 1;
        if (!status) {
            throw new DisabledException("user disabled:" + username);
        }

        Long userId = rec.getLong("user_id");
        Set<String> roles = loadRoles(userId);
        Set<String> perms = loadPerms(userId);

        UserAuthInfo userAuthInfo = new UserAuthInfo();
        userAuthInfo.setUserId(userId == null ? null : String.valueOf(userId));
        userAuthInfo.setUsername(rec.getStr("username"));
        userAuthInfo.setNickname(rec.getStr("display_name"));
        userAuthInfo.setRealName(rec.getStr("real_name"));
        userAuthInfo.setPassword(rec.getStr("password"));
        userAuthInfo.setOrgName(rec.getStr("org_name"));
        userAuthInfo.setStatus(1);
        userAuthInfo.setRoles(roles);
        userAuthInfo.setPerms(perms);
        userAuthInfo.setDataScope(1);
        // 从数据库读取 first_login 标记
        Boolean firstLogin = rec.getBoolean("first_login");
        userAuthInfo.setFirstLogin(firstLogin != null && firstLogin);
        return new SysUserDetails(userAuthInfo);
    }

    private Set<String> loadRoles(Long userId) {
        Set<String> roles = new HashSet<>();
        if (userId == null) return roles;
        List<Record> roleRecords = Db.use().find(
                "select r.role_key from sys_role r " +
                        "join sys_user_role ur on ur.role_id = r.role_id " +
                        "where ur.user_id = ?",
                userId
        );
        for (Record roleRecord : roleRecords) {
            String roleKey = roleRecord.getStr("role_key");
            if (roleKey == null || roleKey.isEmpty()) continue;
            if (roleKey.startsWith("ROLE_")) {
                roleKey = roleKey.substring("ROLE_".length());
            }
            roles.add(roleKey);
        }
        return roles;
    }

    private Set<String> loadPerms(Long userId) {
        Set<String> perms = new HashSet<>();
        if (userId == null) return perms;
//        List<Record> permRecords = Db.use().find(
//                "select p.code from sys_permission p " +
//                        "join sys_role_permission rp on rp.perm_id = p.perm_id " +
//                        "join sys_user_role ur on ur.role_id = rp.role_id " +
//                        "where ur.user_id = ? and p.code is not null",
//                userId
//        );
//        for (Record permRecord : permRecords) {
//            String code = permRecord.getStr("code");
//            if (code != null && !code.isEmpty()) {
//                perms.add(code);
//            }
//        }
        return perms;
    }
}
