package org.archime.ime.engine

/** Small UI-independent contract implemented by the native Rime adapter. */
interface InputEngine : AutoCloseable {
    data class Candidate(val text: String, val comment: String = "")
    data class Update(
        val composingText: String,
        val candidates: List<Candidate>,
        val ready: Boolean,
        val commitText: String = ""
    )

    fun initialize(userDataDirectory: String): Boolean
    fun processKey(codePoint: Int): Update
    fun selectCandidate(index: Int): Update
    fun changePage(backward: Boolean): Update
    fun backspace(): Update
    fun clear(): Update
    override fun close()
}
