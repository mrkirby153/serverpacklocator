package cpw.mods.forge.serverpacklocator;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;

public class PackLocator implements IModLocator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path serverModsPath;
    private final Path clientModsPath;
    private final SidedPackHandler serverPackLocator;
    private IModLocator dirLocator;

    private IModLocator clientLocator;

    public PackLocator() {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
        final Path gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        serverModsPath = DirHandler.createOrGetDirectory(gameDir, "servermods");
        clientModsPath = DirHandler.createOrGetDirectory(gameDir, "clientmods");
        serverPackLocator = SidedPackLocator.buildFor(LaunchEnvironmentHandler.INSTANCE.getDist(), serverModsPath, clientModsPath);
        if (!serverPackLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
    }
    @Override
    public List<IModFile> scanMods() {
        boolean successfulDownload = serverPackLocator.waitForDownload();

        final List<IModFile> modFiles = dirLocator.scanMods();
        final IModFile packutil = modFiles.stream()
                .filter(modFile -> "serverpackutility.jar".equals(modFile.getFileName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Something went wrong with the internal utility mod"));

        ArrayList<IModFile> finalModList = new ArrayList<>();
        finalModList.add(packutil);
        if (successfulDownload) {
            finalModList.addAll(serverPackLocator.processModListClientOnly(clientLocator.scanMods()));
            finalModList.addAll(serverPackLocator.processModList(modFiles));
        }

        ModAccessor.statusLine = "ServerPack: " + (successfulDownload ? "loaded" : "NOT loaded");
        return finalModList;
    }

    @Override
    public String name() {
        return "serverpacklocator";
    }

    @Override
    public Path findPath(final IModFile modFile, final String... path) {
        return dirLocator.findPath(modFile, path);
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer) {
        dirLocator.scanFile(modFile, pathConsumer);
    }

    @Override
    public Optional<Manifest> findManifest(final Path file) {
        return dirLocator.findManifest(file);
    }

    @Override
    public void initArguments(final Map<String, ?> arguments) {
        final IModDirectoryLocatorFactory modFileLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory();
        dirLocator = modFileLocator.build(serverModsPath, "serverpack");
        clientLocator = modFileLocator.build(clientModsPath, "serverpack_client");

        if (serverPackLocator.isValid()) {
            serverPackLocator.initialize(dirLocator);
        }

        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        URI targetURI = LamdbaExceptionUtils.uncheck(() -> new URI("file://"+LamdbaExceptionUtils.uncheck(url::toURI).getRawSchemeSpecificPart().split("!")[0]));
        final FileSystem thiszip = LamdbaExceptionUtils.uncheck(() -> FileSystems.newFileSystem(Paths.get(targetURI), getClass().getClassLoader()));
        final Path utilModPath = thiszip.getPath("utilmod", "serverpackutility.jar");
        LamdbaExceptionUtils.uncheck(()->Files.copy(utilModPath, serverModsPath.resolve("serverpackutility.jar"), StandardCopyOption.REPLACE_EXISTING));
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return dirLocator.isValid(modFile);
    }
}
