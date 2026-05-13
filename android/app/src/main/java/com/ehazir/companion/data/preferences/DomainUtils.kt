package com.ehazir.companion.data.preferences

import java.net.InetAddress

object DomainUtils {
    private const val API_PREFIX = "api."
    private const val EHAZIR_SUFFIX = ".ehazircloud.com"

    fun normalizeTenantDomain(input: String): String {
        val tenant = toTenant(input)
        return if (tenant.isBlank()) "" else "$API_PREFIX$tenant$EHAZIR_SUFFIX"
    }

    fun toTenant(input: String): String {
        return input
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .removePrefix(API_PREFIX)
            .removeSuffix(EHAZIR_SUFFIX)
            .trim()
    }

    fun validateDomainExists(host: String): Boolean {
        if (host.isBlank()) return false
        return try {
            InetAddress.getByName(host)
            true
        } catch (_: Exception) {
            false
        }
    }
}
