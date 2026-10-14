import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

group = "com.anatdx"
version = "0.2.0"

@CacheableTask
abstract class PrepareHexLicenseResources : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyLicense() {
        val target = outputDirectory.file("META-INF/com.anatdx/yukifb-hex/LICENSE").get().asFile
        target.parentFile.mkdirs()
        licenseFile.get().asFile.copyTo(target, overwrite = true)
    }
}

android {
    namespace = "com.anatdx.yukifb.hex"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    resourcePrefix = "yukifb_hex_"
}

androidComponents.onVariants { variant ->
    val licenseResources = tasks.register<PrepareHexLicenseResources>(
        "prepare${variant.name.replaceFirstChar { it.uppercaseChar() }}LicenseResources"
    ) {
        licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
    }
    variant.sources.resources?.addGeneratedSourceDirectory(
        licenseResources,
        PrepareHexLicenseResources::outputDirectory,
    )
}

dependencies {
    api(platform(libs.compose.bom))
    api(project(":yukifb"))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.activity.compose)
    androidTestImplementation(libs.navigationevent.compose)
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF/com.anatdx/yukifb-hex") }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.anatdx", "yukifb-hex", project.version.toString())
    pom {
        name = "YukiFB Hex"
        description = "A paged, backend-agnostic binary editor for Jetpack Compose."
        url = "https://github.com/Anatdx/YukiFB"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers { developer { id = "anatdx"; name = "Anatdx"; url = "https://github.com/Anatdx" } }
        scm {
            url = "https://github.com/Anatdx/YukiFB"
            connection = "scm:git:git://github.com/Anatdx/YukiFB.git"
            developerConnection = "scm:git:ssh://git@github.com/Anatdx/YukiFB.git"
        }
    }
}

extensions.configure<SigningExtension> { useGpgCmd() }
