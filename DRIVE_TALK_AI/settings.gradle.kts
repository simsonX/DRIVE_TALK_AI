pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") } }
}
rootProject.name = "DRIVE_TALK_AI"
include(":app", ":pleos-adapters")
