package com.jpspm.travelplanner.domain.places

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Horário de funcionamento no formato `opening_hours` do OpenStreetMap (RF-40, RF-41), num
 * interpretador simplificado, sem biblioteca (RNF-22). Entende o caso comum:
 *
 * - dias e faixas de dias: `Mo-Fr`, `Sa,Su`, `Mo-We,Fr`; sem dias = todos os dias;
 * - horários: `09:00-18:00`, vários no dia `09:00-12:00,14:00-18:00`, passando da meia-noite `18:00-02:00`;
 * - `off`/`closed`, `24/7`;
 * - regras separadas por `;`, em que a regra seguinte substitui as anteriores nos dias que ela cita.
 *
 * Qualquer outra coisa (feriados `PH`, meses, datas, `sunrise`, `+`, comentários) faz [parse] devolver
 * `null`: sem entender o horário, o app não avisa nada (melhor não avisar do que avisar errado).
 */
class OpeningHours private constructor(
    /** Minutos abertos de cada dia, como intervalos [início, fim) em minutos desde 00:00. */
    private val week: Map<DayOfWeek, List<IntRange>>,
) {
    fun isOpenAt(day: DayOfWeek, time: LocalTime): Boolean {
        val minute = time.hour * 60 + time.minute
        return week[day].orEmpty().any { minute in it }
    }

    fun isClosedAllDay(day: DayOfWeek): Boolean = week[day].isNullOrEmpty()

    companion object {
        private val DAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        private val DAY_RANGE = Regex("""^(Mo|Tu|We|Th|Fr|Sa|Su)(?:-(Mo|Tu|We|Th|Fr|Sa|Su))?$""")
        private val TIME_RANGE = Regex("""^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$""")

        fun parse(text: String?): OpeningHours? {
            val source = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (source == "24/7") return OpeningHours(DayOfWeek.entries.associateWith { listOf(0 until 1440) })
            val week = DayOfWeek.entries.associateWith { mutableListOf<IntRange>() }.toMutableMap()
            // Sobras de faixas que passam da meia-noite, para somar ao dia seguinte depois das regras.
            val spill = DayOfWeek.entries.associateWith { mutableListOf<IntRange>() }

            for (rawRule in source.split(';')) {
                val rule = rawRule.trim()
                if (rule.isEmpty()) continue
                val parts = rule.split(Regex("\\s+"), limit = 2)
                val (daysPart, timesPart) = when {
                    parts.size == 1 && (DAY_RANGE.matches(parts[0].split(',').first())) -> parts[0] to ""
                    parts.size == 1 -> "" to parts[0]
                    else -> parts[0] to parts[1]
                }
                val days = parseDays(daysPart) ?: return null
                val ranges = parseTimes(timesPart) ?: return null
                for (day in days) {
                    // Regra posterior substitui as anteriores nesse dia.
                    week.getValue(day).clear()
                    spill.getValue(day).clear()
                    for (r in ranges) {
                        if (r.last >= 1440) {
                            week.getValue(day) += r.first until 1440
                            spill.getValue(day) += 0 until (r.last + 1 - 1440)
                        } else {
                            week.getValue(day) += r
                        }
                    }
                }
            }
            spill.forEach { (day, ranges) ->
                val next = DayOfWeek.entries[(day.ordinal + 1) % 7]
                week.getValue(next) += ranges
            }
            return OpeningHours(week)
        }

        /** `""` = todos os dias; `null` = formato não suportado. */
        private fun parseDays(text: String): List<DayOfWeek>? {
            if (text.isEmpty()) return DayOfWeek.entries
            return text.split(',').flatMap { part ->
                val m = DAY_RANGE.matchEntire(part.trim()) ?: return null
                val from = DAYS.indexOf(m.groupValues[1])
                val to = m.groupValues[2].takeIf { it.isNotEmpty() }?.let(DAYS::indexOf) ?: from
                // Faixa que dá a volta na semana, ex.: `Fr-Mo`.
                val indices = if (to >= from) (from..to).toList() else (from..6).toList() + (0..to).toList()
                indices.map { DayOfWeek.entries[it] }
            }.distinct()
        }

        /** Faixas em minutos; o fim pode passar de 1440 quando atravessa a meia-noite. `null` = não suportado. */
        private fun parseTimes(text: String): List<IntRange>? {
            val t = text.trim()
            if (t == "off" || t == "closed") return emptyList()
            if (t.isEmpty()) return listOf(0 until 1440)
            return t.split(',').map { part ->
                val m = TIME_RANGE.matchEntire(part.trim()) ?: return null
                val start = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                var end = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
                if (start >= 1440 || end > 1440 + 24 * 60) return null
                if (end <= start) end += 1440 // passa da meia-noite
                start until end
            }
        }

        /** Texto legível em pt-BR: `Mo-Fr 09:00-18:00; Su off` → `seg–sex 09:00–18:00 · dom fechado`. */
        fun describe(text: String): String {
            val names = mapOf("Mo" to "seg", "Tu" to "ter", "We" to "qua", "Th" to "qui", "Fr" to "sex", "Sa" to "sáb", "Su" to "dom", "PH" to "feriados")
            var s = text
            names.forEach { (en, pt) -> s = s.replace(Regex("\\b$en\\b"), pt) }
            return s.replace("24/7", "24 horas").replace(Regex("\\b(off|closed)\\b"), "fechado")
                .replace(Regex("(\\d{2}:\\d{2})-(\\d{2}:\\d{2})"), "$1–$2")
                .replace(Regex("(seg|ter|qua|qui|sex|sáb|dom)-(seg|ter|qua|qui|sex|sáb|dom)"), "$1–$2")
                .split(';').joinToString(" · ") { it.trim() }
        }
    }
}
