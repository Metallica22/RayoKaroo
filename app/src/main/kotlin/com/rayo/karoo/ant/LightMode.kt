package com.rayo.karoo.ant

enum class LightMode(
    val modeNumber: Int,
    val germanName: String,
    val englishName: String,
    val karooName: String,
    val aliases: Set<String> = emptySet()
) {
    OFF(0, "Aus", "Off", "OFF", setOf("LIGHT_MODE_OFF", "DISABLED")),
    STEADY_HIGH(4, "Dauerlicht Hell", "Steady High", "STEADY4", setOf("STEADY_HIGH", "HIGH", "LIGHT_MODE_STEADY_HIGH")),
    STEADY_MED(1, "Dauerlicht Mittel", "Steady Med", "STEADY1", setOf("STEADY_MEDIUM", "MEDIUM", "LIGHT_MODE_STEADY_MEDIUM", "STEADY2", "STEADY3")),
    STEADY_LOW(5, "Dauerlicht Spar", "Steady Low", "STEADY5", setOf("STEADY_LOW", "LOW", "LIGHT_MODE_STEADY_LOW")),
    FAST_FLASH(7, "Blinken Schnell", "Fast Flash", "FAST_FLASH", setOf("FLASH", "LIGHT_MODE_FAST_FLASH")),
    SLOW_FLASH(6, "Blinken Langsam", "Slow Flash", "SLOW_FLASH", setOf("DAY_PULSE", "NIGHT_FLASH", "LIGHT_MODE_SLOW_FLASH")),
    DAY_FLASH(8, "Tagfahrlicht", "Day Flash", "RANDOM_FLASH", setOf("DAY_FLASH", "LIGHT_MODE_RANDOM_FLASH")),
    AUTO(9, "Automatik", "Auto", "AUTO", setOf("AUTO_DAY", "AUTO_NIGHT", "SMART", "LIGHT_MODE_AUTO")),
    ;

    fun getLocalizedName(isGerman: Boolean): String = if (isGerman) germanName else englishName

    companion object {
        fun fromKarooName(name: String?): LightMode {
            if (name == null) return OFF
            val upper = name.uppercase()
            return entries.find { it.karooName == upper || upper in it.aliases } ?: OFF
        }

        val CYCLE_MODES = listOf(OFF, STEADY_HIGH, STEADY_MED, STEADY_LOW, DAY_FLASH, FAST_FLASH, SLOW_FLASH)
    }
}

data class AntLightInfo(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val isConnected: Boolean = false,
    val batteryPercent: Int? = null,
    val currentMode: LightMode = LightMode.OFF,
    val supportedModes: List<LightMode> = LightMode.CYCLE_MODES
)
