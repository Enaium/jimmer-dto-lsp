plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

val archiveName = "jimmer-dto-lsp"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.lsp4j)
    implementation(libs.jimmer.dtoCompiler)
    implementation(libs.jimmer.core)
    implementation(libs.jimmer.coreKotlin)
    implementation(libs.jimmer.sql)
    implementation(libs.jimmer.sqlKotlin)
    implementation(libs.jackson)

    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName = archiveName
}

tasks.jar {
    archiveBaseName = archiveName
    dependsOn(tasks.shadowJar)
}

tasks.register<Exec>("runVSCode") {
    workingDir = rootProject.rootDir
    commandLine(
        "powershell",
        "scripts/vscode.ps1",
        tasks.shadowJar.get().archiveFile.get().asFile.absolutePath,
        version
    )
    dependsOn(tasks.shadowJar)
}

tasks.register<Exec>("embed") {
    workingDir = rootProject.rootDir
    commandLine(
        "powershell",
        "scripts/embed.ps1",
        tasks.shadowJar.get().archiveFile.get().asFile.absolutePath
    )
    dependsOn(tasks.shadowJar)
}