package net.fornwall.android.vulkan.validation;

import com.android.build.api.variant.AndroidComponentsExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaBasePlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AndroidVulkanValidationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final var abiFilters = new HashSet<String>();
        AndroidComponentsExtension<?, ?, ?> androidComponentsExtension = project.getExtensions().getByType(AndroidComponentsExtension.class);
        androidComponentsExtension.onVariants(androidComponentsExtension.selector().all(), variant -> {
            // TODO: Get this in a better way (respect what is resolved, debug/release)
            //System.out.println("#### ON VARIANT: " + variant.getName());
            //System.out.println("#### VARIANT ARTIFACTS: " + variant.getArtifacts());
            //System.out.println("#### VARIANT NATIVE BUILD TYPE: " + variant.getExternalNativeBuild());
            var externalNativeBuild = variant.getExternalNativeBuild();
            //         externalNativeBuild {
            //            cmake {
            //                abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
            //                arguments '-DANDROID_TOOLCHAIN=clang', '-DANDROID_STL=c++_static'
            //            }
            //        }
            abiFilters.addAll(externalNativeBuild.getAbiFilters().get());
        });

        var logger = project.getLogger();

        project.getTasks().whenTaskAdded(addedTask -> {
            if (addedTask.getName().equals("packageDebug")) {
                addedTask.dependsOn(createTask(project, "Debug", abiFilters));
            } else if (addedTask.getName().equals("packageRelease")) {
                addedTask.dependsOn(createTask(project, "Release", abiFilters));
            }
        });
    }

    private static Task createTask(Project project, String debugOrRelease, Set<String> enabledAbis) {
        return project.task("bundleVulkanValidationLayers" + debugOrRelease, (task) -> {
            task.setDescription("Bundles Vulkan Validation Layers with the app");
            task.setGroup(JavaBasePlugin.BUILD_TASK_NAME);

            task.doLast((lastTask) -> {
                var validationLayerVersion = "1.3.268.0";
                var binariesZip = getOrDownloadValidationLayer(project, validationLayerVersion);
                for (String abi : enabledAbis) {
                    putValidationLayerIntoPlace(binariesZip, validationLayerVersion, project, abi, debugOrRelease);
                }
            });
        });
    }

    private static File getOrDownloadValidationLayer(Project project, String validationLayerVersion) {
        var logger = project.getLogger();
        File pluginUserCacheDirectory = new File(project.getGradle().getGradleUserHomeDir(), "android-vulkan-validation");
        if (!pluginUserCacheDirectory.exists()) {
            if (!pluginUserCacheDirectory.mkdirs()) {
                throw new RuntimeException("Cannot create directory: " + pluginUserCacheDirectory.getAbsolutePath());
            }
        }

        File binariesZip = new File(pluginUserCacheDirectory, "android-binaries-vulkan-sdk-" + validationLayerVersion + "-android.zip");
        if (binariesZip.exists()) {
            return binariesZip;
        }

        var urlString = "https://github.com/KhronosGroup/Vulkan-ValidationLayers/releases/download/vulkan-sdk-" + validationLayerVersion + "/android-binaries-vulkan-sdk-" + validationLayerVersion + "-android.zip";
        logger.lifecycle("Downloading " + binariesZip.getName() + " ...");

        URL url;
        try {
            url = new URL(urlString);
            File tmpFile = File.createTempFile("android-vulkan-validation", ".zip");
            try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile)) {
                FileChannel fileChannel = fileOutputStream.getChannel();
                try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream())) {
                    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
            }
            if (!tmpFile.renameTo(binariesZip)) {
                throw new RuntimeException("Cannot return " + tmpFile.getAbsolutePath() + " to " + binariesZip.getAbsolutePath());
            }
            return binariesZip;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
        // https://github.com/KhronosGroup/Vulkan-ValidationLayers/releases/download/vulkan-sdk-1.3.268.0/android-binaries-vulkan-sdk-1.3.268.0-android.zip
    }

    private static void putValidationLayerIntoPlace(File binariesZip, String validationLayerVersion, Project project, String abi, String debugOrRelease) {
        try (var zipFile = new ZipFile(binariesZip)) {
            var zipEntryPath = "android-binaries-vulkan-sdk-" + validationLayerVersion + "/" + abi + "/libVkLayer_khronos_validation.so";
            ZipEntry zipEntry = zipFile.getEntry(zipEntryPath);
            if (zipEntry == null) {
                throw new RemoteException("No such zip entry: " + zipEntryPath);
            }

            var relativeOutPath = "src/main/jniLibs/" + abi + "/libVkLayer_khronos_validation.so";
            var outPath = project.getProjectDir().getAbsolutePath() + "/" + relativeOutPath;
            var outFile = new File(outPath);

            if (debugOrRelease.equals("Release")) {
                if (outFile.exists() && !outFile.delete()) {
                    throw new RuntimeException("Cannot delete: " + outFile.getAbsolutePath());
                }
                return;
            }

            project.getLogger().lifecycle("Bundling " + relativeOutPath);
            if (outFile.exists() && outFile.length() == zipEntry.getSize()) {
                project.getLogger().lifecycle("File already in place");
                return;
            }
            var parentDir = outFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IllegalArgumentException("Cannot create " + parentDir.getAbsolutePath());
            }
            var tmpFile = File.createTempFile("android-vulkan-validation", ".so");
            try (var fileOutputStream = new FileOutputStream(tmpFile)) {
                var outChannel = fileOutputStream.getChannel();
                try (var inChannel = Channels.newChannel(zipFile.getInputStream(zipEntry))) {
                    outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
                }
            }
            Files.move(tmpFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }
}
