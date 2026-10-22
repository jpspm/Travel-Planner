package com.jpspm.travelplanner.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TripsRoute

@Serializable
data class TripEditorRoute(val tripId: String? = null)

@Serializable
data class TripDetailRoute(val tripId: String)

/** [cityKey] = [com.jpspm.travelplanner.domain.city.CityGroup.key]. */
@Serializable
data class CityRoute(val tripId: String, val cityKey: String)

@Serializable
data class DestinationEditorRoute(val tripId: String, val destinationId: String? = null)

/** [date] em ISO (`2026-10-22`) para já abrir no dia escolhido do roteiro. */
@Serializable
data class ActivityEditorRoute(val tripId: String, val activityId: String? = null, val date: String? = null)

/** [type] = nome do [com.jpspm.travelplanner.domain.model.ReservationType] ao criar; [date] em ISO já preenche o início. */
@Serializable
data class ReservationEditorRoute(
    val tripId: String,
    val reservationId: String? = null,
    val type: String? = null,
    val date: String? = null,
)

@Serializable
data class IdeaEditorRoute(val tripId: String, val ideaId: String? = null)

@Serializable
data class ReservationDetailRoute(val reservationId: String)

@Serializable
data class AttachmentViewerRoute(val attachmentId: String)
