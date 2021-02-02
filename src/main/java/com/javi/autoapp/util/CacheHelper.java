package com.javi.autoapp.util;

import java.util.Collection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class CacheHelper {
    public static void bustCache(CacheManager cacheManager) {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        cacheNames.forEach(cacheName -> CacheHelper.getCacheAndClear(cacheManager, cacheName));
    }

    private static void getCacheAndClear(CacheManager cacheManager, final String cacheName) {
        final Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalArgumentException("invalid cache name: " + cacheName);
        }
        cache.clear();
    }
}
