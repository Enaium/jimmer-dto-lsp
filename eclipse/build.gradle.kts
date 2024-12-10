import kotlin.io.path.Path

plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(fileTree(Path(System.getenv("ECLIPSE")).resolve("plugins")) {
        include("*.jar")
    })
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Bundle-ManifestVersion" to "2",
                "Bundle-Name" to "jimmer-dto-lsp-eclipse",
                "Bundle-Version" to version,
                "Bundle-SymbolicName" to "eclipse;singleton:=true",
                "Bundle-Vendor" to "Enaium",
                "Bundle-RequiredExecutionEnvironment" to "JavaSE-21",
                "Require-Bundle" to listOf(
                    "org.eclipse.ui",
                    "org.eclipse.lsp4e",
                    "org.eclipse.core.contenttype"
                ).joinToString(",").trim(),
            )
        )
    }
}