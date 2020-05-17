package dirkraft.resql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

internal val mapper = ObjectMapper().registerModule(KotlinModule())