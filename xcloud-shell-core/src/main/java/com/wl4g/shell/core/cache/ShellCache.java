/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.shell.core.cache;

import static com.wl4g.component.common.lang.ClassUtils2.resolveClassNameNullable;
import static java.util.Objects.nonNull;

import java.util.List;

/**
 * {@link ShellCache}
 * 
 * @author Wangl.sir &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @version 2021-06-30 v1.0.0
 * @see v1.0.0
 */
public interface ShellCache {

    default <V> V hget(String key, Class<V> valueClass) {
        throw new UnsupportedOperationException();
    }

    default <V> List<V> hgetAll(Class<V> valueClass) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean hset(String key, V value) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean hsetnx(String key, V value) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean hdel(String key) {
        throw new UnsupportedOperationException();
    }

    default <V> V get(String key, Class<V> valueClass) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean set(String key, V value, long expireMs) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean setnx(String key, V value, long expireMs) {
        throw new UnsupportedOperationException();
    }

    default <V> boolean del(String key) {
        throw new UnsupportedOperationException();
    }

    default Object eval(String script, List<String> keys, List<String> args) {
        throw new UnsupportedOperationException();
    }

    public static class Factory {

        public static ShellCache build(Object cacheClientObj) {
            ShellCache shellCache = new MemoryShellCache();
            if (nonNull(cacheClientObj)) {
                if (nonNull(JEDIS_CLIENT_CLASS) && JEDIS_CLIENT_CLASS.isInstance(cacheClientObj)) {
                    shellCache = new JedisClientShellCache(cacheClientObj);
                } else if (nonNull(JEDIS_CLUSTER_CLASS) && JEDIS_CLUSTER_CLASS.isInstance(cacheClientObj)) {
                    shellCache = new NativeJedisShellCache(cacheClientObj);
                } else if (nonNull(JEDIS_CLASS) && JEDIS_CLASS.isInstance(cacheClientObj)) {
                    shellCache = new NativeJedisShellCache(cacheClientObj);
                } else if (nonNull(REDIS_TEMPLATE_CLASS) && REDIS_TEMPLATE_CLASS.isInstance(cacheClientObj)) {
                    shellCache = new RedisTemplateShellCache(cacheClientObj);
                }
            }
            return shellCache;
        }

        public static final Class<?> JEDIS_CLIENT_CLASS = resolveClassNameNullable(
                "com.wl4g.component.support.cache.jedis.JedisClient");
        public static final Class<?> JEDIS_CLUSTER_CLASS = resolveClassNameNullable("redis.clients.jedis.JedisCluster");
        public static final Class<?> JEDIS_CLASS = resolveClassNameNullable("redis.clients.jedis.Jedis");
        public static final Class<?> REDIS_TEMPLATE_CLASS = resolveClassNameNullable(
                "org.springframework.data.redis.core.RedisTemplate");
    }

}