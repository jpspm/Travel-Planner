package com.jpspm.travelplanner.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** IDs de entidade são UUIDs, já pensando em sincronização futura (RNF-14). */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
