plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

group = "com.anatdx"
version = "0.1.0-SNAPSHOT"

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
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
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifactId = "yukifb"
                pom {
                    name = "YukiFB"
                    description = "A backend-agnostic file browser UI for Jetpack Compose."
                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        }
                    }
                }
            }
        }
    }
}
