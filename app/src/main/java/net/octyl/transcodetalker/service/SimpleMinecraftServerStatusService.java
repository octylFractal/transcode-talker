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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.octyl.transcodetalker.adventure.HtmlSerializer;
import net.octyl.transcodetalker.data.MinecraftServerStatus;
import net.octyl.transcodetalker.data.UsableComponent;
import net.octyl.transcodetalker.util.MinecraftProtocol;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

@Component
@Qualifier("simple")
public class SimpleMinecraftServerStatusService implements MinecraftServerStatusService {

    private record ConnectionCheckResult(String response, long latency) {
    }

    private record StatusResult(
        Version version,
        Players players,
        JsonNode description,
        String favicon
    ) {
    }

    private record Version(
        String name,
        int protocol
    ) {
    }

    private record Players(
        int max,
        int online,
        List<Player> sample
    ) {
    }

    private record Player(
        String name,
        String id
    ) {
    }

    private static final Logger LOGGER = LogManager.getLogger();

    private final ObjectMapper mapper;

    public SimpleMinecraftServerStatusService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MinecraftServerStatus getStatus(String host, int port) {
        try {
            var response = checkConnection(host, port);
            int latencyInt = (int) Long.max(0, Long.min(response.latency, Integer.MAX_VALUE));
            try {
                var responseContent = mapper.readValue(response.response, StatusResult.class);
                var componentResult = responseContent.description == null
                    ? UsableComponent.NONE
                    : deserializeComponent(responseContent.description);
                return new MinecraftServerStatus.Online(
                    componentResult,
                    latencyInt
                );
            } catch (JacksonException e) {
                LOGGER.warn("Failed to parse response", e);
                return new MinecraftServerStatus.Online(
                    UsableComponent.NONE,
                    latencyInt
                );
            }
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to connect to %s:%d".formatted(host, port), e);
            }
            return new MinecraftServerStatus.Offline();
        }
    }

    private UsableComponent deserializeComponent(JsonNode component) throws IOException {
        // Gotta round-trip it. If the performance really sucks, maybe later I'll rewrite this.
        String json = mapper.writeValueAsString(component);
        var advComponent = GsonComponentSerializer.gson().deserialize(json).compact();
        // Special case: if the component is just a single text component, de-legacy it.
        if (advComponent.children().isEmpty() && advComponent instanceof TextComponent text) {
            advComponent = LegacyComponentSerializer.legacySection().deserialize(text.content());
        }
        // Serialize it in our three forms.
        return new UsableComponent(
            PlainTextComponentSerializer.plainText().serialize(advComponent),
            HtmlSerializer.serializeToHtml(advComponent),
            mapper.readValue(GsonComponentSerializer.gson().serialize(advComponent), new TypeReference<>() {
            })
        );
    }

    private ConnectionCheckResult checkConnection(String host, int port) throws IOException {
        try (var socket = new Socket()) {
            socket.setSoTimeout(500);
            socket.connect(new InetSocketAddress(host, port), 1000);

            try (
                var rawOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                var rawIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()))
            ) {
                // Send Handshake
                writePacket(rawOut, 0x00, dataOut -> {
                    MinecraftProtocol.writeVarInt(dataOut, -1); // No protocol version specified
                    MinecraftProtocol.writeString(dataOut, host);
                    dataOut.writeShort(port);
                    MinecraftProtocol.writeVarInt(dataOut, 1); // Next state: Status
                });

                // Send Request
                writePacket(rawOut, 0x00, dataOut -> {
                    // There's nothing.
                });

                // Read Response
                String response = readPacket(rawIn, 0x00, MinecraftProtocol::readString);

                // Test Ping
                long start = System.currentTimeMillis();
                writePacket(rawOut, 0x01, dataOut -> {
                    dataOut.writeLong(start); // Arbitrary Payload
                });
                long payload = readPacket(rawIn, 0x01, DataInput::readLong);
                if (payload != start) {
                    throw new IOException("Invalid payload");
                }
                long latency = System.currentTimeMillis() - start;

                return new ConnectionCheckResult(response, latency);
            }
        }
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutput dataOut) throws IOException;
    }

    @FunctionalInterface
    private interface PacketReader<R> {
        R read(DataInput dataIn) throws IOException;
    }

    private void writePacket(DataOutputStream stream, int packetId, PacketWriter writer) throws IOException {
        var dataOut = ByteStreams.newDataOutput();
        MinecraftProtocol.writeVarInt(dataOut, packetId);
        writer.write(dataOut);
        var data = dataOut.toByteArray();
        MinecraftProtocol.writeVarInt(stream, data.length);
        stream.write(data);
        stream.flush();
    }

    private <R> R readPacket(DataInputStream stream, int expectedPacketId, PacketReader<R> reader) throws IOException {
        var length = MinecraftProtocol.readVarInt(stream);
        byte[] data = new byte[length];
        stream.readFully(data);

        var dataIn = ByteStreams.newDataInput(data);
        var packetId = MinecraftProtocol.readVarInt(dataIn);
        if (packetId != expectedPacketId) {
            throw new IOException("Expected packet id " + expectedPacketId + ", got " + packetId);
        }
        return reader.read(dataIn);
    }
}
