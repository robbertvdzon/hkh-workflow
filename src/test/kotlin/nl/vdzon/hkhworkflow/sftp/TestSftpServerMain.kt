package nl.vdzon.hkhworkflow.sftp

import java.nio.file.Files
import java.nio.file.Path

/**
 * Start een sftp-server op een vrije poort om de app met de hand tegen te proberen (zie tools/mock/README.md).
 * Argumenten: de map die de wortel van de server wordt, het bestand voor de hostsleutel en eventueel een vaste poort.
 * Gebruiker en wachtwoord staan in [TestSftpServer].
 */
fun main(args: Array<String>) {
    val root = Path.of(args.getOrElse(0) { "sftp-root" }).toAbsolutePath()
    Files.createDirectories(root)
    val server = TestSftpServer(root, Path.of(args.getOrElse(1) { "sftp-hostkey.ser" }).toAbsolutePath(), args.getOrNull(2)?.toInt() ?: 0)
    println("SFTP-server klaar")
    println("  poort          : ${server.port}")
    println("  gebruiker      : ${TestSftpServer.USER}")
    println("  wachtwoord     : ${TestSftpServer.PASSWORD}")
    println("  vingerafdruk   : ${server.fingerprint}")
    println("  wortel         : $root")
    System.out.flush()
    Thread.currentThread().join()
}
