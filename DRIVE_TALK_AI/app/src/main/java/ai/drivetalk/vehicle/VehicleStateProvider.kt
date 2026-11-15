package ai.drivetalk.vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VehicleState { UNKNOWN, DRIVING, PARKED }
data class VehicleStatus(val state: VehicleState = VehicleState.UNKNOWN, val demo: Boolean = false) { val parkedFeaturesAllowed: Boolean get() = state == VehicleState.PARKED }
interface VehicleStateProvider { val status: StateFlow<VehicleStatus> }
/** No supported vehicle service was installed; fail closed. Never infer parking from zero speed. */
class UnavailableVehicleStateProvider : VehicleStateProvider { override val status = MutableStateFlow(VehicleStatus()) }
class DemoVehicleStateProvider(private val allowSimulation: Boolean = true) : VehicleStateProvider {
    override val status = MutableStateFlow(VehicleStatus())
    fun simulate(state: VehicleState) { if(!allowSimulation) return; status.value = VehicleStatus(state, true) }
    fun reset() { status.value = VehicleStatus() }
}
