plugins { java }
group = "de.openai.villageroles"
version = "0.2.0-spigot1"
repositories { maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
dependencies { compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.jar { archiveBaseName.set("VillageRolesSpigot") }
