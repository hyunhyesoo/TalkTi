package kr.ac.kopo.talkti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kr.ac.kopo.talkti.features.voice.VoiceToTextParser
import kr.ac.kopo.talkti.ui.theme.TalkTiTheme

class MainActivity : ComponentActivity() {

    // 음성 인식 헬퍼 클래스 초기화
    private val voiceParser by lazy {
        VoiceToTextParser(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TalkTiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceTestScreen(
                        voiceParser = voiceParser,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceTestScreen(voiceParser: VoiceToTextParser, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by voiceParser.state.collectAsState()

    // 마이크 권한 상태 관리
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 마이크 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!hasPermission) {
            // 권한이 없을 때
            Text(text = "음성 인식을 위해 마이크 권한이 필요합니다.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(text = "권한 요청하기")
            }
        } else {
            // 권한이 있을 때 STT 테스트 화면 표시
            Text(
                text = if (state.isSpeaking) "말씀하세요..." else "버튼을 눌러 음성 인식을 시작하세요",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = state.spokenText.ifEmpty { "음성 인식 결과가 여기에 표시됩니다." },
                style = MaterialTheme.typography.bodyLarge
            )

            // 에러 메시지가 있으면 빨간색으로 표시
            if (state.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (state.isSpeaking) {
                        voiceParser.stopListening()
                    } else {
                        voiceParser.startListening()
                    }
                }
            ) {
                Text(text = if (state.isSpeaking) "인식 중지" else "음성 인식 시작")
            }
        }
    }
}