package com.dopdop.bopdop

import com.dopdop.bopdop.data.saavn.Des
import org.junit.Assert.assertEquals
import org.junit.Test

class DesTest {
    @Test fun decryptsSaavnMediaUrl() {
        val enc = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyryhkSYK5IH2E7FCO52VR6yhNbcEbes5iCcja4+W8xhE0SwtCJToN4Bw7tS9a8Gtq"
        assertEquals("https://aac.saavncdn.com/871/c2febd353f3a076a406fa37510f31f9f_96.mp4", Des.decryptSaavn(enc))
    }
}
