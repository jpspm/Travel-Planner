package com.jpspm.travelplanner.platform

/** Conteúdo de uma entrada do `.zip`: bytes em memória ou um arquivo do disco (copiado em streaming). */
sealed interface ZipSource {
    val name: String

    class Bytes(override val name: String, val bytes: ByteArray) : ZipSource

    class File(override val name: String, val path: String) : ZipSource
}

/** Cria o `.zip` em [targetPath] (caminho absoluto) com as [entries]. */
expect suspend fun writeZip(targetPath: String, entries: List<ZipSource>)

/**
 * Percorre as entradas do `.zip` em [sourcePath]. [onEntry] recebe o nome e o conteúdo de cada arquivo;
 * pastas são ignoradas.
 */
expect suspend fun readZip(sourcePath: String, onEntry: suspend (name: String, bytes: ByteArray) -> Unit)
