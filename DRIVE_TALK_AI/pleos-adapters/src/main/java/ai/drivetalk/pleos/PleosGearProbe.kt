package ai.drivetalk.pleos
import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
/** Gear alone never authorizes parked UI. Pair with fresh speed, parking brake and UX restrictions. */
class PleosGearProbe(context: Context) {
    private val vehicle=Vehicle(context)
    fun initialize() = vehicle.initialize()
    fun read(onGear: (VehicleGear?)->Unit, onFailure: (Exception)->Unit) {
        vehicle.getGear().getCurrentGearSelectionState(onGear,onFailure)
    }
    fun close() = vehicle.release()
}
