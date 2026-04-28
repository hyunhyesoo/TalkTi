package kr.ac.kopo.talkti.data.model

data class AgentCommandResponse(
    val status: String,
    val target_id: String,
    val target_index: Int,
    val target_text: String,
    val tts_message: String
)
