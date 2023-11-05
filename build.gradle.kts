plugins {
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "net.fornwall.android-vulkan-validation"
            implementationClass = "net.fornwall.android.vulkan.validation.AndroidVulkanValidationPlugin"
            displayName = "Android Vulkan Validation"
            description = "Downloads and bundles Vulkan Validation Layers within the app"
        }
    }
}

dependencies {
    api("com.android.tools.build:gradle-api:8.1.2")
    runtimeOnly("com.android.tools.build:gradle:8.1.2")
}