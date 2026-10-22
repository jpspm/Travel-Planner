package com.jpspm.travelplanner.domain.model

/** Campo específico de um tipo de reserva (ver ADR 3.4.1). O rótulo exibido fica na camada de UI. */
enum class ReservationField(val key: String) {
    AIRLINE("airline"),
    FLIGHT_NUMBER("flightNumber"),
    ORIGIN("origin"),
    DESTINATION("destination"),
    TERMINAL("terminal"),
    GATE("gate"),
    SEAT("seat"),
    BAGGAGE("baggage"),
    ROOM_TYPE("roomType"),
    CONTACT("contact"),
    SECTOR_SEAT("sectorSeat"),
    COMPANY("company"),
    SERVICE_NUMBER("serviceNumber"),
    SEAT_COACH("seatCoach"),
    PICKUP_LOCATION("pickupLocation"),
    DROPOFF_LOCATION("dropoffLocation"),
    CAR_CATEGORY("carCategory"),
    PEOPLE("people"),
}

/**
 * Tipos de reserva e o que cada um tem: campos específicos, se usa local, se tem horário de fim
 * e se o fim aparece como item próprio no roteiro (check-out, devolução).
 */
enum class ReservationType(
    val fields: List<ReservationField>,
    /** Se o título é digitado (nome do hotel, evento etc.) ou montado a partir dos campos (voo, trem). */
    val hasTitleField: Boolean,
    val hasPlace: Boolean,
    val hasEnd: Boolean,
    val endIsSeparateAgendaItem: Boolean,
) {
    FLIGHT(
        fields = listOf(
            ReservationField.AIRLINE, ReservationField.FLIGHT_NUMBER,
            ReservationField.ORIGIN, ReservationField.DESTINATION,
            ReservationField.TERMINAL, ReservationField.GATE, ReservationField.SEAT, ReservationField.BAGGAGE,
        ),
        hasTitleField = false, hasPlace = false, hasEnd = true, endIsSeparateAgendaItem = false,
    ),
    LODGING(
        fields = listOf(ReservationField.ROOM_TYPE, ReservationField.CONTACT),
        hasTitleField = true, hasPlace = true, hasEnd = true, endIsSeparateAgendaItem = true,
    ),
    EVENT(
        fields = listOf(ReservationField.SECTOR_SEAT),
        hasTitleField = true, hasPlace = true, hasEnd = false, endIsSeparateAgendaItem = false,
    ),
    TRAIN_BUS(
        fields = listOf(
            ReservationField.COMPANY, ReservationField.SERVICE_NUMBER,
            ReservationField.ORIGIN, ReservationField.DESTINATION, ReservationField.SEAT_COACH,
        ),
        hasTitleField = false, hasPlace = false, hasEnd = true, endIsSeparateAgendaItem = false,
    ),
    CAR_RENTAL(
        fields = listOf(
            ReservationField.COMPANY, ReservationField.CAR_CATEGORY,
            ReservationField.PICKUP_LOCATION, ReservationField.DROPOFF_LOCATION,
        ),
        hasTitleField = false, hasPlace = false, hasEnd = true, endIsSeparateAgendaItem = true,
    ),
    RESTAURANT(
        fields = listOf(ReservationField.PEOPLE),
        hasTitleField = true, hasPlace = true, hasEnd = false, endIsSeparateAgendaItem = false,
    ),
    OTHER(
        fields = emptyList(),
        hasTitleField = true, hasPlace = true, hasEnd = true, endIsSeparateAgendaItem = false,
    );

    /**
     * Título exibido nas listas. Para tipos sem campo de título, é montado dos campos:
     * `LATAM LA8084 · GRU → FCO`, `Trenitalia 9512 · Roma → Firenze`, `Hertz · Grupo C`.
     */
    fun buildTitle(typedTitle: String, details: Map<String, String>): String {
        if (hasTitleField) return typedTitle.trim()
        fun d(field: ReservationField) = details[field.key].orEmpty().trim()
        val route = listOf(d(ReservationField.ORIGIN), d(ReservationField.DESTINATION))
            .takeIf { it.all(String::isNotEmpty) }?.joinToString(" → ")
        val head = when (this) {
            FLIGHT -> listOf(d(ReservationField.AIRLINE), d(ReservationField.FLIGHT_NUMBER))
            TRAIN_BUS -> listOf(d(ReservationField.COMPANY), d(ReservationField.SERVICE_NUMBER))
            CAR_RENTAL -> listOf(d(ReservationField.COMPANY))
            else -> emptyList()
        }.filter(String::isNotEmpty).joinToString(" ")
        val tail = if (this == CAR_RENTAL) d(ReservationField.CAR_CATEGORY).ifEmpty { null } else route
        return listOfNotNull(head.ifEmpty { null }, tail).joinToString(" · ")
    }
}
