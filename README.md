# Android Vulkan Validation Gradle Plugin
Gradle plugin for bundling vulkan validation layers with Android apps.

## Development resources
- [Developing Custom Gradle Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Android: Write Gradle plugins](https://developer.android.com/build/extend-agp)

## Using a locally built version of this plugin
Run `./gradlew assemble` to create `./build/libs/android-vulkan-validation.jar`.

In the project where this plugin should be used:

```kotlin
buildscript {
    repositories { .. }
    dependencies {
        ...
        classpath files('path/to/android-vulkan-valiation/build/libs/android-vulkan-validation.jar')
    }
}
```

Now the plugin can be applied normally:

```kotlin
apply plugin: 'net.fornwall.android-vulkan-validation'
```