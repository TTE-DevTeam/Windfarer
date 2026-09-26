import org.gradle.kotlin.dsl.project

plugins {
    `maven-publish`
    id("buildlogic.java-conventions")
    id("com.gradleup.shadow") version "8.3.6"
    id("io.papermc.hangar-publish-plugin") version "0.1.3"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

dependencies {
    implementation(project(":windfarer-api"))
    compileOnly("org.yaml:snakeyaml:2.0")
}

val platformProjects = listOf(
    ":windfarer-v1_21_8",
    ":windfarer-v1_21_10",
    ":windfarer-v1_21_11",
    ":windfarer-v26_1_2",
    ":windfarer-v26_2",
    ":windfarer-v26_3",
)

// Take the first X projects
val reobfProjects = platformProjects.take(3)
// Take the last X projects
val regularProjects = platformProjects.drop(3)

tasks.shadowJar {
    archiveBaseName.set("Windfarer-${project.version}")
    archiveClassifier.set("")
    archiveVersion.set("")

    dependsOn(
        reobfProjects.map { "$it:reobfJar" } +
                regularProjects.map { "$it:jar" }
    )

    from(
        reobfProjects.map { projectPath ->
            project(projectPath).layout.buildDirectory.file(
                "libs/${projectPath.removePrefix(":")}-${project.version}-reobf.jar"
            )
        }
    )

    from(
        regularProjects.map { projectPath ->
            project(projectPath).layout.buildDirectory.file(
                "libs/${projectPath.removePrefix(":")}-${project.version}.jar"
            )
        }
    )

    dependencies {
        include(project(":windfarer-api"))
    }

    manifest.attributes(
        "paperweight-mappings-namespace" to "mojang"
    )
}

tasks.processResources {
    from(rootProject.file("LICENSE.md"))
    filesMatching("*.yml") {
        expand(mapOf("projectVersion" to project.version))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "net.countercraft"
            artifactId = "movecraft"
            version = "${project.version}"

            artifact(tasks["shadowJar"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/apdevteam/movecraft")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version as String)
        channel.set("Release")
        id.set("Airship-Pirates/Movecraft")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))
        platforms {
            register(io.papermc.hangarpublishplugin.model.Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(listOf("1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3"))
            }
        }
    }
}

description = "Windfarer"
