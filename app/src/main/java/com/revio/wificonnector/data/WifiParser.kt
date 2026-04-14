package com.revio.wificonnector.data

import com.revio.wificonnector.domain.WifiNetwork
import java.io.InputStream
import java.util.regex.Pattern

class WifiParser {
    fun parse(inputStream: InputStream): List<WifiNetwork> {
        val content = inputStream.bufferedReader().use { it.readText() }
        val networks = mutableListOf<WifiNetwork>()
        
        // Match each network={ ... } block. 
        // We use Pattern.DOTALL to allow the dot to match newlines.
        val blockPattern = Pattern.compile("network=\\{(.+?)\\}", Pattern.DOTALL)
        val blockMatcher = blockPattern.matcher(content)
        
        while (blockMatcher.find()) {
            val block = blockMatcher.group(1) ?: continue
            
            // Extract SSID and Password (PreSharedKey) from the block.
            // These can appear in any order and with other fields in between.
            val ssidPattern = Pattern.compile("SSID=\"(.+?)\"")
            val ssidMatcher = ssidPattern.matcher(block)
            
            // Supports both 'PreSharedKey' and 'psk' labels
            val pskPattern = Pattern.compile("(?:PreSharedKey|psk)=\"(.+?)\"")
            val pskMatcher = pskPattern.matcher(block)
            
            if (ssidMatcher.find() && pskMatcher.find()) {
                val ssid = ssidMatcher.group(1)
                val psk = pskMatcher.group(1)
                if (ssid != null && psk != null) {
                    networks.add(WifiNetwork(ssid, psk))
                }
            }
        }
        return networks
    }
}
