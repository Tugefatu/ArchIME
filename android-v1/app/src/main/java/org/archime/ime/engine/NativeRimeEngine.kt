package org.archime.ime.engine

/** JNI adapter for the statically linked librime runtime. */
class NativeRimeEngine : InputEngine {
    private var handle: Long = 0L
    private var libraryLoaded = false

    override fun initialize(userDataDirectory: String): Boolean {
        if (handle != 0L) return true
        if (!libraryLoaded) {
            libraryLoaded = try {
                System.loadLibrary("archime_rime")
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
        if (!libraryLoaded) return false
        handle = try {
            nativeCreate(userDataDirectory)
        } catch (_: UnsatisfiedLinkError) {
            0L
        } catch (_: RuntimeException) {
            0L
        }
        return handle != 0L
    }

    override fun processKey(codePoint: Int) =
        if (handle != 0L) nativeProcessKey(handle, codePoint) else unavailable()

    override fun selectCandidate(index: Int) =
        if (handle != 0L) nativeSelectCandidate(handle, index) else unavailable()

    override fun changePage(backward: Boolean) =
        if (handle != 0L) nativeChangePage(handle, backward) else unavailable()

    override fun backspace() =
        if (handle != 0L) nativeBackspace(handle) else unavailable()

    override fun clear() =
        if (handle != 0L) nativeClear(handle) else unavailable()

    override fun close() {
        val oldHandle = handle
        handle = 0L
        if (oldHandle != 0L) {
            try { nativeDestroy(oldHandle) } catch (_: UnsatisfiedLinkError) { }
        }
    }

    private fun unavailable() = InputEngine.Update("", emptyList(), false, "")

    private external fun nativeCreate(userDataDirectory: String): Long
    private external fun nativeProcessKey(handle: Long, codePoint: Int): InputEngine.Update
    private external fun nativeSelectCandidate(handle: Long, index: Int): InputEngine.Update
    private external fun nativeChangePage(handle: Long, backward: Boolean): InputEngine.Update
    private external fun nativeBackspace(handle: Long): InputEngine.Update
    private external fun nativeClear(handle: Long): InputEngine.Update
    private external fun nativeDestroy(handle: Long)
}
