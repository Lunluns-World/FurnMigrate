plugins {
    application
}

dependencies {
    implementation(project(":common"))
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("world.lunlun.furnmigrate.converter.MainKt")
    applicationName = "converter"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
