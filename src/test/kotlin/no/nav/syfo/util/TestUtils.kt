package no.nav.syfo.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ExperimentalContracts
inline fun <reified T> Any?.shouldBeInstance() {
    contract {
        returns() implies (this@shouldBeInstance is T)
    }

    this is T
}
