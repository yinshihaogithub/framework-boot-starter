package com.framework.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 权限缓存服务
 * 缓存用户的角色和权限列表，减少 DB 查询
 * 变更时主动刷新
 */
@Slf4j
public class PermissionCacheService {

    private static final String ROLE_CACHE_PREFIX = "framework:perm:roles:";
    private static final String PERM_CACHE_PREFIX = "framework:perm:perms:";
    private static final long CACHE_TTL = 3600; // 1小时
    private static final long SCAN_COUNT = 500;
    private static final int DELETE_BATCH_SIZE = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public PermissionCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 缓存用户角色
     */
    public void cacheRoles(Long userId, String[] roles) {
        try {
            String json = objectMapper.writeValueAsString(roles);
            redis.opsForValue().set(ROLE_CACHE_PREFIX + userId, json, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[权限缓存] 缓存角色失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取用户角色（从缓存）
     */
    public String[] getRoles(Long userId) {
        try {
            String json = redis.opsForValue().get(ROLE_CACHE_PREFIX + userId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, String[].class);
        } catch (Exception e) {
            log.warn("[权限缓存] 读取角色失败 userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 缓存用户权限
     */
    public void cachePermissions(Long userId, String[] permissions) {
        try {
            String json = objectMapper.writeValueAsString(permissions);
            redis.opsForValue().set(PERM_CACHE_PREFIX + userId, json, CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[权限缓存] 缓存权限失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取用户权限（从缓存）
     */
    public String[] getPermissions(Long userId) {
        try {
            String json = redis.opsForValue().get(PERM_CACHE_PREFIX + userId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, String[].class);
        } catch (Exception e) {
            log.warn("[权限缓存] 读取权限失败 userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 刷新用户权限缓存（角色或权限变更时调用）
     */
    public void refresh(Long userId) {
        try {
            redis.delete(ROLE_CACHE_PREFIX + userId);
            redis.delete(PERM_CACHE_PREFIX + userId);
            log.info("[权限缓存] 已刷新 userId={}", userId);
        } catch (Exception e) {
            log.warn("[权限缓存] 刷新失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 批量刷新权限缓存
     */
    public void refreshBatch(List<Long> userIds) {
        for (Long userId : userIds) {
            refresh(userId);
        }
    }

    /**
     * 判断用户是否拥有指定角色
     */
    public boolean hasRole(Long userId, String role) {
        String[] roles = getRoles(userId);
        if (roles == null) {
            return false;
        }
        return Arrays.asList(roles).contains(role);
    }

    /**
     * 判断用户是否拥有指定权限
     */
    public boolean hasPermission(Long userId, String permission) {
        String[] permissions = getPermissions(userId);
        if (permissions == null) {
            return false;
        }
        return Arrays.asList(permissions).contains(permission);
    }

    /**
     * 清除所有权限缓存
     */
    public void clearAll() {
        try {
            DeleteResult roleResult = deleteByPattern(ROLE_CACHE_PREFIX + "*");
            DeleteResult permissionResult = deleteByPattern(PERM_CACHE_PREFIX + "*");
            long deleted = roleResult.deleted() + permissionResult.deleted();
            if (roleResult.success() && permissionResult.success()) {
                log.info("[权限缓存] 已清空所有缓存 count={}", deleted);
            } else {
                log.warn("[权限缓存] 清空失败 deleted={}, roleSuccess={}, permissionSuccess={}",
                        deleted, roleResult.success(), permissionResult.success());
            }
        } catch (Exception e) {
            log.warn("[权限缓存] 清空失败 error={}", e.getMessage());
        }
    }

    private DeleteResult deleteByPattern(String pattern) {
        long deleted = 0;
        boolean success = true;
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build())) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= DELETE_BATCH_SIZE) {
                    DeleteResult batchResult = deleteBatch(batch);
                    deleted += batchResult.deleted();
                    success = success && batchResult.success();
                }
            }
            DeleteResult batchResult = deleteBatch(batch);
            deleted += batchResult.deleted();
            success = success && batchResult.success();
        } catch (Exception e) {
            log.warn("[权限缓存] 扫描清理失败 pattern={}, error={}", pattern, e.getMessage());
            success = false;
        }
        return new DeleteResult(deleted, success);
    }

    private DeleteResult deleteBatch(Collection<String> keys) {
        if (keys.isEmpty()) {
            return new DeleteResult(0, true);
        }
        List<String> batch = new ArrayList<>(keys);
        keys.clear();
        try {
            Long deleted = redis.delete(batch);
            return new DeleteResult(deleted == null ? 0 : deleted, true);
        } catch (Exception e) {
            log.warn("[权限缓存] 批量删除失败 count={}, error={}", batch.size(), e.getMessage());
            return new DeleteResult(0, false);
        }
    }

    private record DeleteResult(long deleted, boolean success) {
    }
}
