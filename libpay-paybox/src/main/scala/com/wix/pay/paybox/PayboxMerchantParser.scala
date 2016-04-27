package com.wix.pay.paybox

trait PayboxMerchantParser {
  def parse(merchantKey: String): PayboxMerchant
  def stringify(merchant: PayboxMerchant): String
}
