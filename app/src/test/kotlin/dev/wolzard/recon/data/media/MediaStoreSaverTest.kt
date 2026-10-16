package dev.wolzard.recon.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreSaverTest {

    @Test
    fun cleanFileNameReplacesReservedCharacters() {
        assertEquals("My_Cool_Video", MediaStoreSaver.cleanFileName("My/Cool:Video"))
        assertEquals("video_1_2", MediaStoreSaver.cleanFileName("video*1?2"))
        assertEquals("clean_name_", MediaStoreSaver.cleanFileName("clean\"name|"))
        assertEquals("video", MediaStoreSaver.cleanFileName("   "))
        assertEquals("video", MediaStoreSaver.cleanFileName(""))
    }

    @Test
    fun mimeTypeForResolvesCorrectly() {
        assertEquals("video/mp4", MediaStoreSaver.mimeTypeFor("mp4"))
        assertEquals("video/webm", MediaStoreSaver.mimeTypeFor("webm"))
        assertEquals("video/x-matroska", MediaStoreSaver.mimeTypeFor("mkv"))
        assertEquals("video/mp4", MediaStoreSaver.mimeTypeFor("unknown"))
    }
}
