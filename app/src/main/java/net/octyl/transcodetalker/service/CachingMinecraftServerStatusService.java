/*
 * Copyright (c) Octavia Togami <https://octyl.net>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.octyl.transcodetalker.service;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.octyl.transcodetalker.data.MinecraftServerStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Qualifier("caching")
public class CachingMinecraftServerStatusService implements MinecraftServerStatusService {
    private record CacheKey(String host, int port) {
    }

    private final MinecraftServerStatusService delegate;
    private final Cache<CacheKey, MinecraftServerStatus> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build();

    public CachingMinecraftServerStatusService(@Qualifier("simple") MinecraftServerStatusService delegate) {
        this.delegate = delegate;
    }

    @Override
    public MinecraftServerStatus getStatus(String host, int port) throws IOException {
        try {
            return cache.get(new CacheKey(host, port), () -> delegate.getStatus(host, port));
        } catch (ExecutionException e) {
            var cause = e.getCause();
            Throwables.throwIfUnchecked(cause);
            Throwables.throwIfInstanceOf(cause, IOException.class);
            throw new RuntimeException(cause);
        }
    }
}
