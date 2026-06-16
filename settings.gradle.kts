rootProject.name = "purwm"
include(":01_purpur-swm-framework")
include(":02_purpur-swm-partygame")
include(":03_world-converter")
include(":04_plugin-suite")

extension(project(":04_plugin-suite")) {
    build.gradle.kts
}
