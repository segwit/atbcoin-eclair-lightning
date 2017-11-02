package fr.acinq.eclair.gui.utils

import java.text.DecimalFormat

object CoinFormat {
  /**
    * Always 5 decimals
    */
  val MILLI_ATB_PATTERN = "###,##0.00000"

  /**
    * Localized formatter for milli-bitcoin amounts. Uses `MILLI_BTC_PATTERN`.
    */
  val MILLI_ATB_FORMAT: DecimalFormat = new DecimalFormat(MILLI_ATB_PATTERN)
}
