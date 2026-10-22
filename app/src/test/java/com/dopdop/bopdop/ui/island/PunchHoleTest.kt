package com.dopdop.bopdop.ui.island

import org.junit.Assert.assertEquals
import org.junit.Test

class PunchHoleTest {
    @Test fun tallBoxFromTheTopEdgeBecomesTheRoundHoleAtItsFoot() {
        // vivo / iQOO: a 90 px camera reported as 90 x 120 from the top of the screen.
        assertEquals(30, holeTop(585, 0, 675, 120))
    }

    @Test fun roundOrWideCutoutsStayAsTheyAre() {
        assertEquals(20, holeTop(600, 20, 660, 80))
        assertEquals(0, holeTop(400, 0, 860, 90))
    }
}
