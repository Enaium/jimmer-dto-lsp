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