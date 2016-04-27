package com.wix.pay.paybox.model

import java.math.{BigDecimal => JBigDecimal}
import java.util.{Calendar, Currency, TimeZone}

object Conversions {
  def toPayboxAmount(amount: Double): String = {
    val amountInt = JBigDecimal.valueOf(amount).movePointRight(2).intValueExact()
    f"$amountInt%010d"
  }

  def toPayboxCurrency(currency: String): String = {
    Currency.getInstance(currency).getNumericCode.toString
  }

  def toPayboxYearMonth(year: Int, month: Int): String = {
    f"$month%02d${year % 100}%02d"
  }

  def toPayboxDateTime(timestamp: Long): String = {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
    cal.setTimeInMillis(timestamp)

    f"${cal.get(Calendar.DAY_OF_MONTH)}%02d${cal.get(Calendar.MONTH) - Calendar.JANUARY + 1}%02d${cal.get(Calendar.YEAR)}%04d${cal.get(Calendar.HOUR_OF_DAY)}%02d${cal.get(Calendar.MINUTE)}%02d${cal.get(Calendar.SECOND)}%02d"
  }
}
