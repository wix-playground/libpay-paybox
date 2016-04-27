package com.wix.pay.paybox

/**
 *
 * @param site   This is the site number (POS) typically provided by the bank to the merchant.
 *               Outside France this number is assigned to the Merchant by Paybox.
 *
 * @param rang   This is the rang number (or "machine") provided by the bank to the merchant.
 *               Outside France this number is assigned to the Merchant by Paybox.

 * @param cle    This field allows to authenticate the originator of the request and allows for additional security
 *               for the PPPS exchange of messages.
 *               The value for this parameter corresponds to the password for the merchant back-office that is
 *               provided by the technical support upon the creation of the merchant account on the Paybox platform.
 */
case class PayboxMerchant(site: String, rang: String, cle: String)
