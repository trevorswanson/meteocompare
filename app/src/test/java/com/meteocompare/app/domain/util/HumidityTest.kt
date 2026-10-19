package com.meteocompare.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HumidityTest {

    @Test
    fun `point de rosee de reference`() {
        // Tables psychrométriques : 20 °C / 50 % → 9,3 °C ; 30 °C / 70 % → 23,9 °C ; 0 °C / 55 % → −8,0 °C.
        assertEquals(9.3, dewPointC(20.0, 50)!!, 0.2)
        assertEquals(23.9, dewPointC(30.0, 70)!!, 0.2)
        assertEquals(-8.0, dewPointC(0.0, 55)!!, 0.2)
    }

    @Test
    fun `a saturation le point de rosee egale la temperature`() {
        assertEquals(12.0, dewPointC(12.0, 100)!!, 1e-9)
    }

    @Test
    fun `entrees invalides`() {
        assertNull(dewPointC(null, 50))
        assertNull(dewPointC(20.0, null))
        assertNull(dewPointC(20.0, 0))
        assertNull(dewPointC(Double.NaN, 50))
    }
}
