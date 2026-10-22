package com.jpspm.travelplanner.platform

// O iOS não tem API de zip pública no Foundation. Questão em aberto no ADR-001 (seção 10):
// usar uma biblioteca multiplataforma ou um `actual` nativo. Até lá, o backup só funciona no Android.

actual suspend fun writeZip(targetPath: String, entries: List<ZipSource>) {
    throw UnsupportedOperationException("Backup ainda não disponível no iOS")
}

actual suspend fun readZip(sourcePath: String, onEntry: suspend (name: String, bytes: ByteArray) -> Unit) {
    throw UnsupportedOperationException("Backup ainda não disponível no iOS")
}
