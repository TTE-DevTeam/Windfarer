plugins {
    id("buildlogic.java-conventions")
    id("io.papermc.paperweight.userdev")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

dependencies {
    api(project(":windfarer-api"))
    paperweight.paperDevBundle("26.3.build.+")
}

description = "Windfarer-v26_3"
