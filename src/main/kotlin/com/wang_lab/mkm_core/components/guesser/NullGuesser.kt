package com.wang_lab.mkm_core.components.guesser

import kotlinx.serialization.json.JsonObject
import com.wang_lab.mkm_core.ReactionModel

class NullGuesser(model: ReactionModel, par: JsonObject): Guesser(model, par)