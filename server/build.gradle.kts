plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}


val archiveName = "jimmer-dto-lsp"
group = "cn.enaium.jimmer"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.lsp4j)
    implementation(libs.jimmer.dtoCompiler)
    implementation(libs.jimmer.core)
    implementation(libs.jimmer.sql)
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