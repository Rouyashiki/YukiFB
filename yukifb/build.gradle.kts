import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

group = "com.anatdx"
version = "0.1.1"

android {
    namespace = "com.anatdx.yukifb"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
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

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "com.anatdx",
        artifactId = "yukifb",
        version = "0.1.1",
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
                name = "Anan"
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
