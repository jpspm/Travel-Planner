package com.jpspm.travelplanner.domain.repository

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Como importar um backup (RF-34). */
enum class ImportMode {
    /** Junta com o que já existe; num conflito, vence a versão alterada por último. */
    MERGE,

    /** Apaga todas as viagens do aparelho e fica só com as do backup. */
    REPLACE,
}

/** O que há num `.zip` de backup, mostrado antes de importar. */
data class BackupSummary(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Instant,
    val tripNames: List<String>,
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Backup manual em `.zip` (D5, RF-33 a RF-35): `manifest.json`, `data.json` e `files/` com anexos e
 * fotos. O formato é versionado por `schemaVersion` para que versões futuras importem backups antigos (RNF-21).
 */
interface BackupRepository {
    /** Gera o `.zip` num arquivo temporário. [tripIds] `null` = todas as viagens. */
    suspend fun export(tripIds: List<String>? = null): PlatformFile

    /** Lê e valida um backup sem alterar nada. Lança [BackupException] se o arquivo não for válido. */
    suspend fun inspect(source: PlatformFile): BackupSummary

    /** Importa o backup. Retorna quantas viagens vieram nele. */
    suspend fun import(source: PlatformFile, mode: ImportMode): Int

    /** Registra que o backup foi salvo ou compartilhado. */
    suspend fun markExported()

    /**
     * `true` quando convém lembrar o usuário de exportar um backup: há dados alterados depois do
     * último backup, que foi há mais de 14 dias (ou nunca aconteceu), e o lembrete não está adiado.
     */
    fun observeReminder(): Flow<Boolean>

    suspend fun snoozeReminder()
}
