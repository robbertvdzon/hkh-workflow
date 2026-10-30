package nl.vdzon.hkhworkflow.ftp

import java.nio.file.Files
import java.nio.file.Path

/**
 * Start een ftp-server op een vrije poort om de app met de hand tegen te proberen (zie tools/mock/README.md).
 * Argumenten: de map die de wortel van de server wordt en eventueel een vaste poort. Gebruiker en wachtwoord staan in [TestFtpServer].
 */
fun main(args: Array<String>) {
    val root = Path.of(args.getOrElse(0) { "ftp-root" }).toAbsolutePath()
    Files.createDirectories(root)
    val server = TestFtpServer(root, args.getOrNull(1)?.toInt() ?: 0)
    println("FTP-server klaar")
    println("  poort          : ${server.port}")
    println("  gebruiker      : ${TestFtpServer.USER}")
    println("  wachtwoord     : ${TestFtpServer.PASSWORD}")
    println("  wortel         : $root")
    System.out.flush()
    Thread.currentThread().join()
}
