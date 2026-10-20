package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.equalizer.AutoEqRepository
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.shared.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun autoEqRepository(store: KeyValueStore, equalizer: dev.cmdrchara.songlass.core.equalizer.Equalizer) = AutoEqRepository(
    readCatalog = { withContext(Dispatchers.Default) { Res.readBytes("files/autoeq/catalog.tsv").decodeToString() } },
    download = ::downloadAutoEqProfile,
    store = store,
    savedCorrection = equalizer::catalogCorrection,
)

internal expect suspend fun downloadAutoEqProfile(url: String): String
