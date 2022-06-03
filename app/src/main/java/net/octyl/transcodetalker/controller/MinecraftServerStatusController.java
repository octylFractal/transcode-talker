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

package net.octyl.transcodetalker.controller;

import net.octyl.transcodetalker.data.MinecraftServerStatus;
import net.octyl.transcodetalker.service.MinecraftServerStatusService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/mc/server-status")
public class MinecraftServerStatusController {
    private final MinecraftServerStatusService minecraftServerStatusService;

    public MinecraftServerStatusController(@Qualifier("caching") MinecraftServerStatusService minecraftServerStatusService) {
        this.minecraftServerStatusService = minecraftServerStatusService;
    }

    /**
     * Get the block states for a data version.
     *
     * @return the block states
     */
    @GetMapping("/")
    public MinecraftServerStatus getBlockStates(
        @RequestParam String host,
        @RequestParam(defaultValue = "25565") int port
    ) throws IOException {
        return minecraftServerStatusService.getStatus(host, port);
    }
}
