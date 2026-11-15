package ai.drivetalk
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.drivetalk.ui.*

class MainActivity : ComponentActivity() {
    private val model: DriveViewModel by viewModels()
    private var voiceCommand = true
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) model.listen(voiceCommand) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { DriveApp(model) { command -> voiceCommand=command; if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) model.listen(command) else permission.launch(Manifest.permission.RECORD_AUDIO) } } }
    override fun onStop() { super.onStop(); model.hidden() }
}
