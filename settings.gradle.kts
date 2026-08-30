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
    }
}

rootProject.name = "Gecko"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:network")
include(":core:provider")
include(":domain")
include(":core:data")
include(":core:testing")
include(":core:markdown")
include(":feature:chat")
include(":feature:settings")
