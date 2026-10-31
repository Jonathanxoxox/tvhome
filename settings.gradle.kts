pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Die ~150 MB große Android-Laufzeit für Robolectric kommt von Googles Spiegel von Maven
        // Central, der nicht drosselt; Maven Central bricht solche Downloads gelegentlich ab.
        exclusiveContent {
            forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2/") }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
    }
}

rootProject.name = "tvhome"
include(":app")
