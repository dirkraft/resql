package dirkraft.resql

import kotliquery.HikariCP
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

open class TestInstance {

  companion object {
    private val tmpDir: File
    private val pgProc: Process

    init {
      Runtime.getRuntime().addShutdownHook(Thread(Runnable {
        cleanup()
      }))

      tmpDir = Files.createTempDirectory("resql-test").toFile()
      println("Test db instance: ${tmpDir.absolutePath}")
      try {
        ProcessBuilder().inheritIO().command(
          "/usr/lib/postgresql/11/bin/initdb",
          "-D", tmpDir.absolutePath
        ).start().waitFor(1, TimeUnit.MINUTES)

        pgProc = ProcessBuilder().inheritIO().command(
          "/usr/lib/postgresql/11/bin/postgres",
          "-k", tmpDir.absolutePath,
          "-D", tmpDir.absolutePath
        ).start()

        for (i in 0..20) {
          Thread.sleep(500)
          val p = Runtime.getRuntime().exec(arrayOf(
            "psql", "-h", "localhost", "postgres", "-c", "select 1"
          ))
          p.waitFor(5, TimeUnit.SECONDS)
          if (p.exitValue() == 0) {
            break
          } else if (i == 20) {
            cleanup()
            throw Exception("Postgres did not successfully start in a timely fashion.")
          }
        }

        HikariCP.default("jdbc:postgresql://localhost:5432/postgres",
          System.getProperty("user.name"), "")
        println("Postgres is ready.")

      } catch (e: Exception) {
        e.printStackTrace()
        throw e
      }
    }

    fun cleanup() {
      println("Cleaning up.")
      pgProc.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
      tmpDir.deleteRecursively()
    }
  }
}