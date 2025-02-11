import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    antlr
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
    implementation(libs.jackson.kotlin)
    implementation(libs.asm.tree)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.tomlj)
    antlr(libs.antlr)

    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
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