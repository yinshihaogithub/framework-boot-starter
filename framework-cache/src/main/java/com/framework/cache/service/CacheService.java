package com.framework.cache.service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务接口
 */
public interface CacheService {

    /**
     * 设置缓存
     */
    void set(String key, Object value);

    /**
     * 设置缓存（带TTL）
     */
    void set(String key, Object value, long ttl, TimeUnit unit);

    /**
     * 获取缓存
     */
    <T> T get(String key, Class<T> type);

    /**
     * 获取缓存（带缓存加载器，防穿透/击穿）
     * 如果缓存不存在，调用 loader 加载并放入缓存
     */
    <T> T get(String key, Class<T> type, java.util.function.Supplier<T> loader);

    /**
     * 获取缓存（带缓存加载器和TTL）
     */
    <T> T get(String key, Class<T> type, java.util.function.Supplier<T> loader, long ttl, TimeUnit unit);

    /**
     * 删除缓存
     */
    void delete(String key);

    /**
     * 批量删除（通配符）
     */
    void deleteByPattern(String pattern);

    /**
     * 判断 key 是否存在
     */
    boolean exists(String key);

    /**
     * 设置过期时间
     */
    void expire(String key, long ttl, TimeUnit unit);

    /**
     * 获取剩余过期时间（秒）
     */
    long getTtl(String key);

    /**
     * 清空所有缓存（谨慎使用）
     */
    void clear();
}
