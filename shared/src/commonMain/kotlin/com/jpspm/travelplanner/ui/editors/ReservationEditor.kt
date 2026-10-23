package com.jpspm.travelplanner.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.core.money.Currency
import com.jpspm.travelplanner.data.importing.ImportResult
import com.jpspm.travelplanner.data.importing.ReservationImporter
import com.jpspm.travelplanner.ui.components.emoji
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import com.jpspm.travelplanner.resources.import_done
import com.jpspm.travelplanner.resources.import_little
import com.jpspm.travelplanner.resources.import_no_text
import com.jpspm.travelplanner.resources.import_type
import com.jpspm.travelplanner.resources.import_reading
import com.jpspm.travelplanner.core.money.Money
import com.jpspm.travelplanner.core.time.ZonedLocal
import com.jpspm.travelplanner.domain.model.Destination
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.ui.reservation.AttachmentItem
import com.jpspm.travelplanner.ui.reservation.AttachmentsBlock
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import com.jpspm.travelplanner.ui.place.PlaceDraft
import com.jpspm.travelplanner.ui.place.PlaceField
import com.jpspm.travelplanner.domain.model.ReservationField
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import com.jpspm.travelplanner.domain.repository.ReservationInput
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_save
import com.jpspm.travelplanner.resources.error_end_moment_before_start
import com.jpspm.travelplanner.resources.error_invalid_price
import com.jpspm.travelplanner.resources.error_required
import com.jpspm.travelplanner.resources.error_time_required
import com.jpspm.travelplanner.resources.field_date
import com.jpspm.travelplanner.resources.field_notes
import com.jpspm.travelplanner.resources.field_price
import com.jpspm.travelplanner.resources.field_time
import com.jpspm.travelplanner.resources.field_timezone
import com.jpspm.travelplanner.resources.reservation_edit_title
import com.jpspm.travelplanner.resources.reservation_new_title
import com.jpspm.travelplanner.resources.section_details
import com.jpspm.travelplanner.resources.section_payment
import com.jpspm.travelplanner.ui.components.BackButton
import com.jpspm.travelplanner.ui.components.DateField
import com.jpspm.travelplanner.ui.components.FieldRow
import com.jpspm.travelplanner.ui.components.FormTextField
import com.jpspm.travelplanner.ui.components.MoneyInput
import com.jpspm.travelplanner.ui.components.RateState
import com.jpspm.travelplanner.ui.components.rememberRate
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.TimeField
import com.jpspm.travelplanner.ui.components.TimeZoneField
import com.jpspm.travelplanner.ui.components.codeLabel
import com.jpspm.travelplanner.ui.components.endLabel
import com.jpspm.travelplanner.ui.components.label
import com.jpspm.travelplanner.ui.components.placeLabel
import com.jpspm.travelplanner.ui.components.startLabel
import com.jpspm.travelplanner.ui.components.titleLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Data + hora + fuso editáveis de um momento da reserva (início ou fim). */
data class MomentForm(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    val timeZoneTouched: Boolean = false,
) {
    val timeMissing get() = date != null && time == null
    fun toZoned(): ZonedLocal? = if (date != null && time != null) ZonedLocal(date.atTime(time), timeZoneId) else null

    companion object {
        fun of(zoned: ZonedLocal?, fallbackZone: String) = MomentForm(
            date = zoned?.date,
            time = zoned?.dateTime?.time,
            timeZoneId = zoned?.timeZoneId ?: fallbackZone,
            timeZoneTouched = zoned != null,
        )
    }
}

sealed interface AttachmentDraft {
    val item: AttachmentItem

    data class Saved(val attachment: Attachment) : AttachmentDraft {
        override val item get() = AttachmentItem(attachment.id, attachment.kind, attachment.fileName, attachment.filePath)
    }

    data class New(val key: String, val file: PlatformFile, val kind: AttachmentKind) : AttachmentDraft {
        override val item get() = AttachmentItem(key, kind, file.name, file)
    }
}

data class ReservationForm(
    val type: ReservationType = ReservationType.OTHER,
    val title: String = "",
    val details: Map<String, String> = emptyMap(),
    val place: PlaceDraft? = null,
    val start: MomentForm = MomentForm(),
    val end: MomentForm = MomentForm(),
    val confirmationCode: String = "",
    val price: String = "",
    /** Moeda do valor digitado; ao salvar, o valor é convertido para BRL (RF-48). */
    val priceCurrency: Currency = Currency.BRL,
    val notes: String = "",
    /** Anexos já salvos que continuam, e os novos escolhidos; só são gravados ao salvar. */
    val attachments: List<AttachmentDraft> = emptyList(),
    val removedAttachmentIds: Set<String> = emptySet(),
    val showErrors: Boolean = false,
) {
    /** Troca o tipo (só na importação, quando a leitura pode ter errado): título e detalhes que não servem ficam de fora ao salvar. */
    fun withType(newType: ReservationType) = copy(type = newType)

    val titleError get() = type.hasTitleField && title.isBlank()
    val priceError get() = price.isNotBlank() && Money.parse(price) == null
    val endBeforeStart: Boolean
        get() {
            val s = start.toZoned() ?: return false
            val e = end.toZoned() ?: return false
            return e.toInstant() < s.toInstant()
        }
    val isValid get() = !titleError && !priceError && !start.timeMissing && !(type.hasEnd && (end.timeMissing || endBeforeStart))
}

/** Como foi a leitura do PDF importado, para o aviso no topo do formulário. */
data class ImportNotice(val fileName: String, val readText: Boolean, val recognized: Int)

/** Horários padrão ao escolher a data, quando o tipo tem um horário típico. */
private fun ReservationType.defaultStartTime(): LocalTime? = if (this == ReservationType.LODGING) LocalTime(14, 0) else null
private fun ReservationType.defaultEndTime(): LocalTime? = if (this == ReservationType.LODGING) LocalTime(12, 0) else null

class ReservationEditorViewModel(
    private val tripId: String,
    private val reservationId: String?,
    type: String?,
    initialDate: String?,
    private val ideaId: String?,
    private val fromImport: Boolean,
    private val importer: ReservationImporter,
    private val ideas: IdeaRepository,
    private val trips: TripRepository,
    private val reservations: ReservationRepository,
    private val destinationsRepo: DestinationRepository,
) : ViewModel() {

    var form by mutableStateOf(ReservationForm(type = type?.let(ReservationType::valueOf) ?: ReservationType.OTHER))
        private set
    var destinations by mutableStateOf(emptyList<Destination>())
        private set
    val isNew get() = reservationId == null
    private var ideaPlaceId: String? = null
    var importNotice by mutableStateOf<ImportNotice?>(null)
        private set
    var importing by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            destinations = destinationsRepo.observeDestinations(tripId).first()
            val existing = reservationId?.let { reservations.getReservation(it) }
            val imported = if (!fromImport) null else importer.takePending()?.let { pending ->
                if (pending.read) pending
                else {
                    importing = true
                    try { importer.read(pending.files) } finally { importing = false }
                }
            }
            form = if (imported != null) {
                fromImport(imported)
            } else if (existing != null) {
                val zone = defaultTimeZoneFor(existing.start?.date, destinations)
                ReservationForm(
                    type = existing.type,
                    title = if (existing.type.hasTitleField) existing.title else "",
                    details = existing.details,
                    place = PlaceDraft.of(existing.place),
                    start = MomentForm.of(existing.start, zone),
                    end = MomentForm.of(existing.end, existing.end?.timeZoneId ?: zone),
                    confirmationCode = existing.confirmationCode,
                    price = (existing.priceOriginalCents ?: existing.priceCents)?.let(Money::toInput).orEmpty(),
                    priceCurrency = existing.priceCurrency,
                    notes = existing.notes,
                    attachments = existing.attachments.map { AttachmentDraft.Saved(it) },
                )
            } else {
                // Aberta a partir de um dia do roteiro: o início já vem nesse dia.
                val date = initialDate?.let(LocalDate::parse)
                val zone = defaultTimeZoneFor(date ?: trips.getTrip(tripId)?.startDate, destinations)
                val idea = ideaId?.let { ideas.get(it) }
                ideaPlaceId = idea?.place?.id
                form.copy(
                    start = MomentForm(date = date, time = date?.let { form.type.defaultStartTime() }, timeZoneId = idea?.place?.timeZoneId ?: zone),
                    end = MomentForm(timeZoneId = idea?.place?.timeZoneId ?: zone),
                    title = if (form.type.hasTitleField) idea?.title.orEmpty() else "",
                    place = PlaceDraft.of(idea?.place),
                    notes = idea?.notes.orEmpty(),
                )
            }
        }
    }

    /** Formulário preenchido com o que foi lido do PDF; todos os arquivos recebidos viram anexos. */
    private suspend fun fromImport(imported: ImportResult): ReservationForm {
        val draft = imported.draft
        val type = draft?.type ?: form.type
        val tripStart = trips.getTrip(tripId)?.startDate
        fun moment(date: LocalDate?, time: LocalTime?, zone: String?) = MomentForm(
            date = date,
            time = time,
            timeZoneId = zone ?: defaultTimeZoneFor(date ?: draft?.startDate ?: tripStart, destinations),
            timeZoneTouched = zone != null,
        )
        imported.sourceName?.let { importNotice = ImportNotice(it, draft != null, draft?.recognized ?: 0) }
        return ReservationForm(
            type = type,
            title = draft?.title.orEmpty(),
            details = draft?.details.orEmpty().mapKeys { it.key.key },
            start = moment(draft?.startDate, draft?.start?.time, draft?.startZone),
            end = moment(draft?.endDate, draft?.end?.time, draft?.endZone),
            confirmationCode = draft?.confirmationCode.orEmpty(),
            price = draft?.priceCents?.let(Money::toInput).orEmpty(),
            priceCurrency = draft?.currency ?: Currency.BRL,
            notes = draft?.address?.let { "📍 $it" }.orEmpty(),
            attachments = imported.files.map { AttachmentDraft.New(newId(), it.file, if (it.isPdf) AttachmentKind.PDF else AttachmentKind.QR) },
        )
    }

    fun changeType(type: ReservationType) = update { it.withType(type) }

    fun update(transform: (ReservationForm) -> ReservationForm) {
        form = transform(form)
    }

    fun addAttachment(file: PlatformFile, kind: AttachmentKind) = update {
        it.copy(attachments = it.attachments + AttachmentDraft.New(newId(), file, kind))
    }

    fun removeAttachment(item: AttachmentItem) = update { f ->
        val draft = f.attachments.firstOrNull { it.item.key == item.key } ?: return@update f
        f.copy(
            attachments = f.attachments - draft,
            removedAttachmentIds = if (draft is AttachmentDraft.Saved) f.removedAttachmentIds + draft.attachment.id else f.removedAttachmentIds,
        )
    }

    fun setDetail(field: ReservationField, value: String) = update { it.copy(details = it.details + (field.key to value)) }

    /** Lugar escolhido na busca preenche o título vazio (nome do hotel, restaurante...) e sugere o fuso. */
    fun setPlace(place: PlaceDraft?) = update { f ->
        val zone = place?.input?.timeZoneId
        f.copy(
            place = place,
            title = if (f.type.hasTitleField) f.title.ifBlank { place?.input?.name.orEmpty() } else f.title,
            start = if (zone != null && !f.start.timeZoneTouched) f.start.copy(timeZoneId = zone) else f.start,
            end = if (zone != null && !f.end.timeZoneTouched) f.end.copy(timeZoneId = zone) else f.end,
        )
    }

    fun setStart(transform: (MomentForm) -> MomentForm) = update { f ->
        val start = transform(f.start).let { m ->
            m.copy(
                time = m.time ?: f.type.defaultStartTime().takeIf { m.date != null },
                timeZoneId = if (m.timeZoneTouched || m.date == null) m.timeZoneId else defaultTimeZoneFor(m.date, destinations),
            )
        }
        // O fim acompanha o início enquanto não for preenchido.
        val end = if (f.end.date == null && start.date != null) {
            f.end.copy(timeZoneId = if (f.end.timeZoneTouched) f.end.timeZoneId else start.timeZoneId)
        } else f.end
        f.copy(start = start, end = end)
    }

    fun setEnd(transform: (MomentForm) -> MomentForm) = update { f ->
        val end = transform(f.end).let { m ->
            m.copy(
                time = m.time ?: f.type.defaultEndTime().takeIf { m.date != null },
                timeZoneId = if (m.timeZoneTouched || m.date == null) m.timeZoneId else defaultTimeZoneFor(m.date, destinations),
            )
        }
        f.copy(end = end)
    }

    /** [rate] converte o valor pago para BRL; sem cotação de uma moeda estrangeira, não salva. */
    fun save(rate: RateState, onDone: (String) -> Unit) {
        val f = form
        val cents = Money.parse(f.price)
        val brl = cents?.let { rate.toBrl(it) }
        if (!f.isValid || (cents != null && brl == null)) {
            form = f.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            val id = reservations.save(
                tripId, reservationId,
                ReservationInput(
                    type = f.type,
                    title = f.title,
                    place = f.place?.input ?: PlaceInput.None,
                    start = f.start.toZoned(),
                    end = f.end.toZoned(),
                    confirmationCode = f.confirmationCode,
                    priceCents = brl,
                    notes = f.notes,
                    details = f.details,
                    reusePlaceId = f.place?.let { ideaPlaceId },
                    priceCurrency = if (cents == null) Currency.BRL else f.priceCurrency,
                    priceOriginalCents = cents?.takeIf { f.priceCurrency != Currency.BRL },
                ),
            )
            // A ideia virou reserva: sai da lista (o lugar continua, agora usado pela reserva).
            ideaId?.let { ideas.delete(it) }
            f.removedAttachmentIds.forEach { reservations.deleteAttachment(it) }
            f.attachments.filterIsInstance<AttachmentDraft.New>().forEach { reservations.addAttachment(id, it.file, it.kind) }
            onDone(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationEditorScreen(
    tripId: String,
    reservationId: String?,
    type: String?,
    date: String? = null,
    ideaId: String? = null,
    fromImport: Boolean = false,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ReservationEditorViewModel = koinViewModel { parametersOf(tripId, reservationId, type, date, ideaId, fromImport) },
) {
    val form = viewModel.form
    val typeName = stringResource(form.type.label)
    val priceRate = rememberRate(form.priceCurrency, form.start.date)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) Res.string.reservation_new_title else Res.string.reservation_edit_title, typeName))
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(onClick = { viewModel.save(priceRate, onSaved) }) { Text(stringResource(Res.string.action_save)) }
                },
            )
        },
    ) { padding ->
        val suggestions = timeZoneSuggestions(viewModel.destinations)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            if (viewModel.importing) {
                Text(stringResource(Res.string.import_reading), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            viewModel.importNotice?.let { notice -> ImportBanner(notice, form.type, viewModel::changeType) }
            if (form.type.hasTitleField) {
                FormTextField(
                    label = stringResource(form.type.titleLabel),
                    value = form.title,
                    onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                    isError = form.showErrors && form.titleError,
                    supportingText = if (form.showErrors && form.titleError) stringResource(Res.string.error_required) else null,
                    capitalization = KeyboardCapitalization.Words,
                )
            }
            form.type.fields.forEach { field ->
                FormTextField(
                    label = stringResource(field.label),
                    value = form.details[field.key].orEmpty(),
                    onValueChange = { viewModel.setDetail(field, it) },
                    keyboardType = if (field == ReservationField.PEOPLE) KeyboardType.Number else KeyboardType.Text,
                    capitalization = when (field) {
                        ReservationField.FLIGHT_NUMBER, ReservationField.ORIGIN, ReservationField.DESTINATION,
                        ReservationField.GATE, ReservationField.TERMINAL, ReservationField.SEAT,
                        -> KeyboardCapitalization.Characters
                        else -> KeyboardCapitalization.Sentences
                    },
                )
            }
            if (form.type.hasPlace) {
                SectionHeader(stringResource(form.type.placeLabel), Modifier.padding(top = 8.dp))
                PlaceField(
                    draft = form.place,
                    onChange = viewModel::setPlace,
                    near = nearFor(form.start.date, viewModel.destinations),
                    searchQuery = form.title,
                )
            }

            SectionHeader(stringResource(form.type.startLabel), Modifier.padding(top = 8.dp))
            MomentFields(form.start, form.showErrors, suggestions, viewModel::setStart)

            if (form.type.hasEnd) {
                SectionHeader(stringResource(form.type.endLabel), Modifier.padding(top = 8.dp))
                MomentFields(form.end, form.showErrors, suggestions, viewModel::setEnd)
                if (form.showErrors && form.endBeforeStart) {
                    Text(
                        stringResource(Res.string.error_end_moment_before_start),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }

            AttachmentsBlock(
                items = form.attachments.map { it.item },
                onAdd = viewModel::addAttachment,
                onRemove = viewModel::removeAttachment,
            )

            SectionHeader(stringResource(Res.string.section_details), Modifier.padding(top = 8.dp))
            FormTextField(
                label = stringResource(form.type.codeLabel),
                value = form.confirmationCode,
                onValueChange = { v -> viewModel.update { it.copy(confirmationCode = v) } },
                capitalization = KeyboardCapitalization.Characters,
            )
            FormTextField(
                label = stringResource(Res.string.field_notes),
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                singleLine = false,
                minLines = 3,
            )

            SectionHeader(stringResource(Res.string.section_payment), Modifier.padding(top = 8.dp))
            MoneyInput(
                label = stringResource(Res.string.field_price),
                amount = form.price,
                onAmountChange = { v -> viewModel.update { it.copy(price = v) } },
                currency = form.priceCurrency,
                onCurrencyChange = { c -> viewModel.update { it.copy(priceCurrency = c) } },
                rate = priceRate,
                isError = form.showErrors && form.priceError,
                errorText = stringResource(Res.string.error_invalid_price),
            )
        }
    }
}

/** Aviso da importação e, como a leitura pode errar o tipo, a troca de tipo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportBanner(notice: ImportNotice, type: ReservationType, onTypeChange: (ReservationType) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notice.readText && notice.recognized > 1) androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
            else androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !notice.readText -> stringResource(Res.string.import_no_text, notice.fileName)
                    notice.recognized <= 1 -> stringResource(Res.string.import_little, notice.fileName)
                    else -> stringResource(Res.string.import_done, notice.fileName)
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(Res.string.import_type), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReservationType.entries.forEach { t ->
                    FilterChip(selected = t == type, onClick = { onTypeChange(t) }, label = { Text("${t.emoji} ${stringResource(t.label)}") })
                }
            }
        }
    }
}

@Composable
private fun MomentFields(
    moment: MomentForm,
    showErrors: Boolean,
    suggestions: List<String>,
    onChange: ((MomentForm) -> MomentForm) -> Unit,
) {
    FieldRow(
        first = { m ->
            DateField(
                label = stringResource(Res.string.field_date),
                date = moment.date,
                onDateChange = { d -> onChange { it.copy(date = d) } },
                modifier = m,
            )
        },
        second = { m ->
            TimeField(
                label = stringResource(Res.string.field_time),
                time = moment.time,
                onTimeChange = { t -> onChange { it.copy(time = t) } },
                modifier = m,
                isError = showErrors && moment.timeMissing,
                supportingText = if (showErrors && moment.timeMissing) stringResource(Res.string.error_time_required) else null,
            )
        },
    )
    if (moment.date != null) {
        TimeZoneField(
            label = stringResource(Res.string.field_timezone),
            timeZoneId = moment.timeZoneId,
            onTimeZoneChange = { z -> onChange { it.copy(timeZoneId = z, timeZoneTouched = true) } },
            suggestions = suggestions,
        )
    }
}
