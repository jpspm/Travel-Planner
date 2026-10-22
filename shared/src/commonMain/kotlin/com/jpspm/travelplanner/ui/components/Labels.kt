package com.jpspm.travelplanner.ui.components

import com.jpspm.travelplanner.domain.agenda.AgendaItem
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.model.ReservationField
import com.jpspm.travelplanner.domain.model.ReservationType
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.*
import org.jetbrains.compose.resources.StringResource

/** Rótulos de UI dos tipos e campos do domínio. Ficam na camada de UI para o domínio não depender de recursos. */

val ReservationType.label: StringResource
    get() = when (this) {
        ReservationType.FLIGHT -> Res.string.reservation_type_flight
        ReservationType.LODGING -> Res.string.reservation_type_lodging
        ReservationType.EVENT -> Res.string.reservation_type_event
        ReservationType.TRAIN_BUS -> Res.string.reservation_type_train_bus
        ReservationType.CAR_RENTAL -> Res.string.reservation_type_car_rental
        ReservationType.RESTAURANT -> Res.string.reservation_type_restaurant
        ReservationType.OTHER -> Res.string.reservation_type_other
    }

val ReservationType.emoji: String
    get() = when (this) {
        ReservationType.FLIGHT -> "✈️"
        ReservationType.LODGING -> "🏨"
        ReservationType.EVENT -> "🎟️"
        ReservationType.TRAIN_BUS -> "🚆"
        ReservationType.CAR_RENTAL -> "🚗"
        ReservationType.RESTAURANT -> "🍽️"
        ReservationType.OTHER -> "📌"
    }

val ReservationType.titleLabel: StringResource
    get() = when (this) {
        ReservationType.LODGING -> Res.string.field_lodging_name
        ReservationType.EVENT -> Res.string.field_event_name
        ReservationType.RESTAURANT -> Res.string.field_restaurant_name
        else -> Res.string.field_title
    }

val ReservationType.startLabel: StringResource
    get() = when (this) {
        ReservationType.FLIGHT, ReservationType.TRAIN_BUS -> Res.string.moment_departure
        ReservationType.LODGING -> Res.string.moment_check_in
        ReservationType.CAR_RENTAL -> Res.string.moment_pickup
        ReservationType.EVENT, ReservationType.RESTAURANT, ReservationType.OTHER -> Res.string.moment_date_time
    }

val ReservationType.endLabel: StringResource
    get() = when (this) {
        ReservationType.FLIGHT, ReservationType.TRAIN_BUS -> Res.string.moment_arrival
        ReservationType.LODGING -> Res.string.moment_check_out
        ReservationType.CAR_RENTAL -> Res.string.moment_dropoff
        else -> Res.string.moment_end
    }

val ReservationType.codeLabel: StringResource
    get() = when (this) {
        ReservationType.FLIGHT -> Res.string.field_locator
        else -> Res.string.field_code
    }

val ReservationType.placeLabel: StringResource
    get() = when (this) {
        ReservationType.LODGING -> Res.string.field_address
        else -> Res.string.field_place
    }

val ReservationField.label: StringResource
    get() = when (this) {
        ReservationField.AIRLINE -> Res.string.field_airline
        ReservationField.FLIGHT_NUMBER -> Res.string.field_flight_number
        ReservationField.ORIGIN -> Res.string.field_origin
        ReservationField.DESTINATION -> Res.string.field_destination
        ReservationField.TERMINAL -> Res.string.field_terminal
        ReservationField.GATE -> Res.string.field_gate
        ReservationField.SEAT -> Res.string.field_seat
        ReservationField.BAGGAGE -> Res.string.field_baggage
        ReservationField.ROOM_TYPE -> Res.string.field_room_type
        ReservationField.CONTACT -> Res.string.field_contact
        ReservationField.SECTOR_SEAT -> Res.string.field_sector_seat
        ReservationField.COMPANY -> Res.string.field_company
        ReservationField.SERVICE_NUMBER -> Res.string.field_service_number
        ReservationField.SEAT_COACH -> Res.string.field_seat_coach
        ReservationField.PICKUP_LOCATION -> Res.string.field_pickup_location
        ReservationField.DROPOFF_LOCATION -> Res.string.field_dropoff_location
        ReservationField.CAR_CATEGORY -> Res.string.field_car_category
        ReservationField.PEOPLE -> Res.string.field_people
    }

val AttachmentKind.label: StringResource
    get() = when (this) {
        AttachmentKind.QR -> Res.string.attachment_kind_qr
        AttachmentKind.PHOTO -> Res.string.attachment_kind_photo
        AttachmentKind.PDF -> Res.string.attachment_kind_pdf
    }

/** Rótulo do momento de uma reserva no roteiro: "Check-out", "Devolução" etc. */
val AgendaItem.ReservationItem.momentLabel: StringResource
    get() = if (role == AgendaItem.ReservationItem.Role.END) reservation.type.endLabel else reservation.type.startLabel
