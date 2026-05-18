import java.io.File
import java.util.zip.ZipFile

fun main() {
    val file = File("floodgate.jar")
    if (!file.exists()) {
        println("File not found")
        return
    }
    ZipFile(file).use { zip ->
        val entry = zip.getEntry("plugin.yml") ?: zip.getEntry("bungee.yml") ?: zip.getEntry("velocity-plugin.json") ?: zip.getEntry("paper-plugin.yml")
        if (entry != null) {
            println("Found entry: ${entry.name}")
            val content = zip.getInputStream(entry).bufferedReader().readText()
            val name = content.lineSequence()
                .map { it.substringBefore('#').trim() }
                .firstOrNull { it.startsWith("name:") }
                ?.substringAfter(':')
                ?.trim()
                ?.trim('"', '\'')
            println("Name: $name")
        } else {
            println("No plugin.yml found")
        }
    }
}
