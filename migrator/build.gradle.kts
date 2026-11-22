repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.oraxen.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("io.th0rgal:oraxen:1.220.1") { isTransitive = false }
    // No public Maven artifact: the jars the server runs (see libs/README in docs/findings.md §3).
    compileOnly(files("../libs/FurnitureLib-3.2.9.2.jar", "../libs/ProtocolLib.jar"))
    // common is bundled into the plugin jar; its snakeyaml comes with Paper.
    compileOnly(project(":common"))
}

val kotlinVersion = "2.4.10"

tasks.processResources {
    val props = mapOf("version" to project.version, "kotlinVersion" to kotlinVersion)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName.set("FurnMigrate")
    dependsOn(":common:jar")
    from(project(":common").tasks.named<Jar>("jar").map { zipTree(it.archiveFile) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
