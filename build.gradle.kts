plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "cn.enaium.jimmer"
    version = rootProject.property("version").toString()
}