package com.wix.pay.paybox

import org.json4s.DefaultFormats
import org.json4s.native.Serialization

class JsonPayboxMerchantParser() extends PayboxMerchantParser {
  implicit val formats = DefaultFormats

  override def parse(merchantKey: String): PayboxMerchant = {
    Serialization.read[PayboxMerchant](merchantKey)
  }

  override def stringify(merchant: PayboxMerchant): String = {
    Serialization.write(merchant)
  }
}
