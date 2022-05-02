package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.minecraftforge.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.function.BiFunction;

enum SidedPackLocator {
    CLIENT(ClientSidedPackHandler::new), DEDICATED_SERVER(ServerSidedPackHandler::new);
    private final BiFunction<Path, Path, SidedPackHandler> handler;

    SidedPackLocator(final BiFunction<Path, Path, SidedPackHandler> handler) {
        this.handler = handler;
    }

    public static SidedPackHandler buildFor(Dist side, final Path serverModsPath,
        final Path clientModsPath) {
        return valueOf(side.toString()).withDirs(serverModsPath, clientModsPath);
    }

    private SidedPackHandler withDirs(final Path serverModsPath, final Path clientModsPath) {
        return handler.apply(serverModsPath, clientModsPath);
    }

}
