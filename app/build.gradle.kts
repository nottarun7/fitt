import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val signingValues = Properties().apply {
    val path = System.getenv("FITT_SIGNING_PROPERTIES") ?: "${System.getProperty("user.home")}/.fitt/signing/signing.properties"
    val config = file(path)
    if (config.isFile) config.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = System.getenv(key)?.takeIf { it.isNotBlank() } ?: signingValues.getProperty(key)
android {
    namespace = "io.github.nottarun7.fitt"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.nottarun7.fitt"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("fittVersionCode").orNull?.toInt() ?: appVersion.getProperty("versionCode").toInt()
        versionName = providers.gradleProperty("fittVersionName").orNull ?: appVersion.getProperty("versionName")
        testInstrumentationRunner = "io.github.nottarun7.fitt.FittTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        listOf("releaseDistribution" to "RELEASE", "legacyDistribution" to "LEGACY").forEach { (name, channel) ->
            create(name) {
                storeFile = signingValue("FITT_${channel}_STORE_PATH")?.let { file(it) }
                storePassword = signingValue("FITT_${channel}_STORE_PASSWORD")
                keyAlias = signingValue("FITT_${channel}_KEY_ALIAS")
                keyPassword = signingValue("FITT_${channel}_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("releaseDistribution"); isDebuggable = false }
        create("legacy") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("legacyDistribution")
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    sourceSets["main"].assets.srcDir("$rootDir/docs")
}
listOf("Release" to "RELEASE", "Legacy" to "LEGACY").forEach { (variant, channel) ->
    tasks.matching { it.name == "pre${variant}Build" }.configureEach {
        doFirst {
            listOf("STORE_PATH", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").forEach {
                require(!signingValue("FITT_${channel}_$it").isNullOrBlank()) { "Missing $channel signing configuration. See RELEASE.md." }
            }
        }
    }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":interchange"))
    implementation(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.health.connect)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}
