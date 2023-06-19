import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object Meta {
	const val projectVersion = "0.2.0"
    const val description =
        "Create moderation actions with ease, reduce code duplication and making things easy in KordEx discord bots"
    const val githubRepo = "HyacinthBots/discord-moderation-actions"
    const val release = "https://s01.oss.sonatype.org/content/repositories/releases/"
    const val snapshot = "https://s01.oss.sonatype.org/content/repositories/snapshots/"

    val version: String
        get() {
            val tag = System.getenv("GITHUB_TAG_NAME")
            val branch = System.getenv("GITHUB_BRANCH_NAME")
            return when {
                !tag.isNullOrBlank() -> tag
                !branch.isNullOrBlank() && branch.startsWith("refs/heads/") ->
					"$projectVersion-SNAPSHOT"

                else -> "undefined"
            }
        }

    val isSnapshot: Boolean get() = version.endsWith("-SNAPSHOT")
    val isRelease: Boolean get() = !isSnapshot && !isUndefined
    private val isUndefined: Boolean get() = version == "undefined"
}

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `java-library`
    `maven-publish`
    signing

    alias(libs.plugins.kotlin)

    alias(libs.plugins.detekt)
    alias(libs.plugins.git.hooks)
    alias(libs.plugins.licenser)
    alias(libs.plugins.binary.compatibility.validator)
}

group = "org.hyacinthbots"
version = Meta.projectVersion
val javaVersion = 17

repositories {
    mavenCentral()

    maven {
        name = "Sonatype Snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        name = "Sonatype Snapshots (Legacy)"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    detektPlugins(libs.detekt)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kordex)

    implementation(libs.logback)
    implementation(libs.logging)
}

gitHooks {
    setHooks(
        mapOf("pre-commit" to "updateLicenses apiCheck detekt")
    )
}

kotlin {
    explicitApi()
	jvmToolchain(javaVersion)
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            languageVersion.set(KotlinVersion.fromVersion(libs.plugins.kotlin.get().version.requiredVersion.substringBeforeLast(".")))
            incremental = true
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }

	withType<JavaCompile> {
		options.encoding = "UTF-8"
		options.isDeprecation = true
		options.release.set(javaVersion)
	}

	wrapper {
		distributionType = Wrapper.DistributionType.ALL
	}
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")

    autoCorrect = true
}

license {
    setHeader(rootProject.file("HEADER"))
    include("**/*.kt")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = Meta.version
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set(project.name)
                description.set(Meta.description)
                url.set("https://github.com/${Meta.githubRepo}")

                organization {
                    name.set("HyacinthBots")
                    url.set("https://github.com/HyacinthBots")
                }

                developers {
                    developer {
                        name.set("The HyacinthBots team")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/${Meta.githubRepo}/issues")
                }

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://mit-license.org/")
                    }
                }

                scm {
                    url.set("https://github.com/${Meta.githubRepo}.git")
                    connection.set("scm:git:git://github.com/${Meta.githubRepo}.git")
                    developerConnection.set("scm:git:git://github.com/#${Meta.githubRepo}.git")
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(if (Meta.isSnapshot) Meta.snapshot else Meta.release)

            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}

if (Meta.isRelease) {
    signing {
        val signingKey = providers.environmentVariable("GPG_SIGNING_KEY")
        val signingPass = providers.environmentVariable("GPG_SIGNING_PASS")

        if (signingKey.isPresent && signingPass.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), signingPass.get())
            val extension = extensions.getByName("publishing") as PublishingExtension
            sign(extension.publications)
        }
    }
}

