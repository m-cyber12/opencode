package dev.opencode.android.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * App-owned project workspaces (spec §13).
 *
 * Every project lives in its own directory under filesDir/projects/<id>.
 * The OpenCode runtime receives exactly this directory as its working scope
 * via the x-opencode-directory header; the rest of the Android filesystem is
 * not visible to the guest by default.
 */
class ProjectRepository(private val context: Context) {
    @Serializable
    data class ProjectMeta(
        val id: String,
        val name: String,
        val createdAtEpochMs: Long,
        val lastOpenedAtEpochMs: Long,
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun projectsRoot(): File = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(id: String): File = File(projectsRoot(), id)

    fun list(): List<ProjectMeta> =
        projectsRoot().listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .mapNotNull { dir -> readMeta(dir) }
            .sortedByDescending { it.lastOpenedAtEpochMs }

    fun create(name: String): ProjectMeta {
        val id = UUID.randomUUID().toString()
        val dir = projectDir(id)
        dir.mkdirs()
        // Note: a git repo makes OpenCode treat this as a first-class project.
        // Git init happens through the guest runtime (POST /project/git/init)
        // once the server is healthy — see ChatViewModel.ensureProjectReady().
        val meta = ProjectMeta(
            id = id,
            name = name,
            createdAtEpochMs = System.currentTimeMillis(),
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
        writeMeta(dir, meta)
        return meta
    }

    /** Creates a starter file so the workspace is never empty. */
    fun ensureStarterFile(meta: ProjectMeta) {
        val readme = File(projectDir(meta.id), "README.md")
        if (!readme.exists()) {
            readme.writeText("# ${meta.name}\n\nCreated with OpenCode for Android.\n")
        }
    }

    fun rename(id: String, newName: String) {
        val dir = projectDir(id)
        readMeta(dir)?.let { writeMeta(dir, it.copy(name = newName)) }
    }

    fun touch(id: String) {
        val dir = projectDir(id)
        readMeta(dir)?.let { writeMeta(dir, it.copy(lastOpenedAtEpochMs = System.currentTimeMillis())) }
    }

    fun delete(id: String) {
        projectDir(id).deleteRecursively()
    }

    fun get(id: String): ProjectMeta? = readMeta(projectDir(id))

    private fun metaFile(dir: File) = File(dir, ".opencode-android-project.json")

    private fun readMeta(dir: File): ProjectMeta? = try {
        json.decodeFromString(ProjectMeta.serializer(), metaFile(dir).readText())
    } catch (_: Exception) {
        null
    }

    private fun writeMeta(dir: File, meta: ProjectMeta) {
        metaFile(dir).writeText(json.encodeToString(meta))
    }
}
