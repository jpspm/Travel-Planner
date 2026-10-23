package com.jpspm.travelplanner.ui.place

import com.jpspm.travelplanner.core.time.TimeZoneSuggester
import com.jpspm.travelplanner.domain.model.PhotoInput
import com.jpspm.travelplanner.domain.model.Place
import com.jpspm.travelplanner.domain.model.PlaceInput
import com.jpspm.travelplanner.domain.repository.LatLng
import com.jpspm.travelplanner.domain.repository.PlaceCandidate
import com.jpspm.travelplanner.domain.repository.RemotePhoto

/**
 * Local em edição num formulário: o que será gravado ([input]) e como mostrar a foto antes de
 * salvar ([photoModel] = URL da Wikimedia, caminho local ou arquivo da galeria, para o Coil).
 */
data class PlaceDraft(
    val input: PlaceInput,
    val photoModel: Any? = null,
    val photoCredit: String? = null,
) {
    val position: LatLng?
        get() = if (input.latitude != null && input.longitude != null) LatLng(input.latitude, input.longitude) else null

    companion object {
        fun of(place: Place?): PlaceDraft? = place?.let {
            PlaceDraft(PlaceInput.of(it), photoModel = it.photoPath, photoCredit = it.photoCredit)
        }

        /** Lugar escolhido na busca, com fuso sugerido pelas coordenadas (RF-13). */
        fun of(candidate: PlaceCandidate, photo: RemotePhoto?, openingHours: String? = candidate.openingHours): PlaceDraft = PlaceDraft(
            input = PlaceInput(
                name = candidate.name,
                address = candidate.address,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                countryCode = candidate.countryCode,
                city = candidate.city,
                timeZoneId = TimeZoneSuggester.suggest(candidate.countryCode, candidate.latitude, candidate.longitude),
                osmId = candidate.osmId,
                openingHours = openingHours,
                photo = photo?.let { PhotoInput.Remote(it) } ?: PhotoInput.Remove,
            ),
            photoModel = photo?.url,
            photoCredit = photo?.let { listOf(it.author, it.license, "Wikimedia Commons").filter(String::isNotBlank).joinToString(" · ") },
        )

        /** Digitado à mão, sem internet (RNF-02). */
        fun manual(name: String, address: String, previous: PlaceDraft?): PlaceDraft = PlaceDraft(
            input = PlaceInput(name = name, address = address, photo = if (previous == null) PhotoInput.Keep else previous.input.photo),
            photoModel = previous?.photoModel,
            photoCredit = previous?.photoCredit,
        )
    }
}
