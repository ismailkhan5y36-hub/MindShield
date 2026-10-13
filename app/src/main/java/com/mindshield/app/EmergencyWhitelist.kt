package com.mindshield.app

object EmergencyWhitelist {

    private val whitelistedPackages = setOf(
        // Phone Dialers & Contacts
        "com.google.android.dialer",
        "com.android.dialer",
        "com.transsion.dialer",
        "com.samsung.android.dialer",
        "com.android.contacts",

        // SMS & Messaging
        "com.google.android.apps.messaging",
        "com.android.mms",

        // Banking & Finance (Pakistan)
        "pk.org.easypaisa.android",
        "com.techlogix.mobilinkcustomer",
        "com.nayapay.app",
        "com.sadapay.app",
        "com.hbl.mobilebanking",
        "com.mcb.mobilebanking",
        "com.bankalfalah.mobilebanking",
        "com.meezanbank.mbapp",

        // Utilities & Productivity
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.google.android.keep",
        "com.android.camera",
        "com.google.android.GoogleCamera",
        "com.transsion.camera",
        "com.google.android.apps.maps"
    )

    fun isWhitelisted(packageName: String): Boolean {
        return whitelistedPackages.contains(packageName)
    }
}
