import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

group = "com.anatdx"
version = "0.2.0"

@CacheableTask
abstract class PrepareLicenseResources : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyLicense() {
        val target = outputDirectory.file("META-INF/com.anatdx/yukifb/LICENSE").get().asFile
        target.parentFile.mkdirs()
        licenseFile.get().asFile.copyTo(target, overwrite = true)
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF/com.anatdx/yukifb")
    }
}

android {
    namespace = "com.anatdx.yukifb"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    resourcePrefix = "yukifb_"

}

androidComponents.onVariants { variant ->
    val licenseResources = tasks.register<PrepareLicenseResources>(
        "prepare${variant.name.replaceFirstChar { it.uppercaseChar() }}LicenseResources"
    ) {
        licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
    }
    variant.sources.resources?.addGeneratedSourceDirectory(
        licenseResources,
        PrepareLicenseResources::outputDirectory,
    )
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.coroutines.core)

    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.navigationevent.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.activity.compose)
    androidTestImplementation(libs.navigationevent.compose)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "com.anatdx",
        artifactId = "yukifb",
        version = project.version.toString(),
    )

    pom {
        name = "YukiFB"
        description = "A backend-agnostic file browser UI for Jetpack Compose."
        inceptionYear = "2026"
        url = "https://github.com/Anatdx/YukiFB"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "anatdx"
                name = "Anatdx"
                email = "an@anatdx.com"
                url = "https://github.com/Anatdx"
            }
        }
        scm {
            url = "https://github.com/Anatdx/YukiFB"
            connection = "scm:git:git://github.com/Anatdx/YukiFB.git"
            developerConnection = "scm:git:ssh://git@github.com/Anatdx/YukiFB.git"
        }
    }
}

extensions.configure<SigningExtension> {
    useGpgCmd()
}
