package com.bluemob.app.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {
    private val history = listOf(
        AiPrompt.Turn(false, "Hey there! I'm Sky"),
        AiPrompt.Turn(true, "How do I purify water?"),
        AiPrompt.Turn(false, "Boil it for one minute."),
    )

    @Test fun qwenPromptHasChatTurnsAndEndsWithAssistant() {
        val p = AiPrompt.build(AiFamily.QWEN, history, "And without fire?", 1280)
        assertTrue(p.startsWith("<|im_start|>system\n" + AiPrompt.SYSTEM))
        // The chat starts with the user, so Sky's opening line is left out.
        assertFalse(p.contains("I'm Sky"))
        assertTrue(p.contains("<|im_start|>user\nHow do I purify water?<|im_end|>\n<|im_start|>assistant\nBoil it for one minute.<|im_end|>"))
        assertTrue(p.endsWith("<|im_start|>user\nAnd without fire?<|im_end|>\n<|im_start|>assistant\n"))
    }

    @Test fun gemmaPutsInstructionsInTheFirstUserTurn() {
        val p = AiPrompt.build(AiFamily.GEMMA, emptyList(), "Hi", 2048)
        assertEquals("<start_of_turn>user\n${AiPrompt.SYSTEM}\n\nHi<end_of_turn>\n<start_of_turn>model\n", p)
    }

    @Test fun oldChatIsDroppedToFitTheModel() {
        val long = (1..40).flatMap { listOf(AiPrompt.Turn(true, "question $it " + "x".repeat(300)), AiPrompt.Turn(false, "answer $it")) }
        val p = AiPrompt.build(AiFamily.QWEN, long, "latest?", 1280)
        assertTrue(p.length < AiPrompt.charsFor(1280))
        assertTrue(p.contains("question 40"))
        assertFalse(p.contains("question 1 "))
    }

    @Test fun cleanRemovesControlTokensAndInventedTurns() {
        assertEquals("Boil it.", AiPrompt.clean("Boil it.<|im_end|>"))
        assertEquals("Boil it.", AiPrompt.clean("Boil it.<end_of_turn>"))
        assertEquals("Sure.", AiPrompt.clean("Sure.\nUser: and then?"))
    }

    @Test fun familyFromFileName() {
        assertEquals(AiFamily.GEMMA, AiModels.familyOf("Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"))
        assertEquals(AiFamily.QWEN, AiModels.familyOf(AiModels.LITE.file))
        assertEquals(AiFamily.OTHER, AiModels.familyOf("phi.task"))
    }
}
