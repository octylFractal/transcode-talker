package net.octyl.transcodetalker.data;

public sealed interface MinecraftServerStatus {
    boolean isOnline();

    record Online(UsableComponent motd, int latency) implements MinecraftServerStatus {
        @Override
        public boolean isOnline() {
            return true;
        }
    }

    record Offline() implements MinecraftServerStatus {
        @Override
        public boolean isOnline() {
            return false;
        }
    }
}
