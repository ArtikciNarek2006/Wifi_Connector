package com.revio.wificonnector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revio.wificonnector.data.WifiParser
import com.revio.wificonnector.databinding.ActivityMainBinding
import com.revio.wificonnector.domain.WifiNetwork
import com.revio.wificonnector.ui.WifiAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val wifiParser = WifiParser()
    private val wifiAdapter = WifiAdapter { network ->
        handleConnectClick(network)
    }

    private var allNetworks = listOf<WifiNetwork>()

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { parseConfigFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchView()

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
        }

        loadSavedNetworks()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterNetworks(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterNetworks(newText)
                return true
            }
        })
    }

    private fun filterNetworks(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            allNetworks
        } else {
            allNetworks.filter { it.ssid.contains(query, ignoreCase = true) }
        }
        wifiAdapter.submitList(filtered)
    }

    private fun updateNetworkCount() {
        binding.tvCount.text = "${allNetworks.size} networks"
    }

    private fun parseConfigFile(uri: Uri) {
        lifecycleScope.launch {
            var newCount = 0
            var duplicateCount = 0
            
            val parsedNetworks = withContext(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        wifiParser.parse(content.byteInputStream())
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val filteredNew = parsedNetworks.filter { p ->
                allNetworks.none { it.ssid == p.ssid && it.preSharedKey == p.preSharedKey }
            }
            
            newCount = filteredNew.size
            duplicateCount = parsedNetworks.size - newCount
            
            if (newCount > 0) {
                allNetworks = (allNetworks + filteredNew).distinctBy { it.ssid to it.preSharedKey }
                withContext(Dispatchers.IO) {
                    saveAllNetworksToStorage()
                }
                wifiAdapter.submitList(allNetworks)
                updateNetworkCount()
            }
            
            if (newCount > 0) {
                Toast.makeText(this@MainActivity, "Imported $newCount new networks", Toast.LENGTH_SHORT).show()
            }
            if (duplicateCount > 0) {
                Toast.makeText(this@MainActivity, "$duplicateCount networks were already prefilled", Toast.LENGTH_SHORT).show()
            }
            if (newCount == 0 && duplicateCount == 0) {
                Toast.makeText(this@MainActivity, "No networks found in file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAllNetworksToStorage() {
        val content = allNetworks.joinToString("\n\n") { network ->
            """
            network={
              ConfigKey="${network.ssid}"WPA_PSK
              SSID="${network.ssid}"
              PreSharedKey="${network.preSharedKey}"
            }
            """.trimIndent()
        }
        try {
            openFileOutput("wifi_configs.txt", MODE_PRIVATE).use {
                it.write(content.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSavedNetworks() {
        lifecycleScope.launch {
            val networks = withContext(Dispatchers.IO) {
                try {
                    if (getFileStreamPath("wifi_configs.txt").exists()) {
                        openFileInput("wifi_configs.txt").use {
                            wifiParser.parse(it)
                        }
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            allNetworks = networks.distinctBy { it.ssid to it.preSharedKey }
            wifiAdapter.submitList(allNetworks)
            updateNetworkCount()
            if (allNetworks.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "Loaded ${allNetworks.size} networks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleConnectClick(network: WifiNetwork) {
        Toast.makeText(this, "Opening connection dialog for ${network.ssid}", Toast.LENGTH_SHORT).show()
        connectToWifi(network)
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun connectToWifi(network: WifiNetwork) {
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(network.ssid)
            .setWpa2Passphrase(network.preSharedKey)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                val suggestionsList = arrayListOf(suggestion)
                putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, suggestionsList)
            }
            startActivity(intent)
        } else {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiManager.addNetworkSuggestions(listOf(suggestion))
            Toast.makeText(this, "Network suggested to system", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
