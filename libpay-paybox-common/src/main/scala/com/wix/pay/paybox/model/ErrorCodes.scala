package com.wix.pay.paybox.model

object ErrorCodes {
  /** Operation successful. */
  val SUCCESS = "00000"

  /** Cardholder number invalid. */
  val INVALID_CARDHOLDER_NUMBER = "00004"

  /** Access refused or combination site / rang not correct. */
  val NO_ACCESS = "00006"

  /** Expiry data not correct. */
  val INVALID_EXPIRATION = "00008"

  /** Card not authorized. */
  val UNAUTHORIZED_CARD = "00021"

  /** Country code filtered. */
  val UNAUTHORIZED_COUNTRY = "00024"
}
