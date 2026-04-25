package week03.shared.config

import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AppEnv {
    private val dotenv: Dotenv = run {
        val envDir = findNearestEnvDirectory()
        val config = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .filename(".env")
        if (envDir != null) {
            config.directory(envDir.toString())
        }
        config.load()
    }

    fun get(name: String): String? = System.getenv(name) ?: dotenv[name]

    fun get(name: String, default: String): String = get(name) ?: default

    fun require(name: String): String = get(name)
        ?: error("Environment variable '$name' is required. Add it to .env")

    fun hasYandexCredentials(): Boolean =
        !get("YANDEX_API_KEY").isNullOrBlank() && !get("YANDEX_FOLDER_ID").isNullOrBlank()

    private fun findNearestEnvDirectory(): Path? {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(8) {
            if (current == null) return null
            if (Files.exists(current.resolve(".env"))) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
