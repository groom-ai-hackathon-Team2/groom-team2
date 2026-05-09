// 도파민드(Dopamind) 프로젝트의 Gradle 설정.
// 단일 모듈(app)로 구성된 해커톤 MVP 구조.

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

rootProject.name = "Dopamind"
include(":app")
