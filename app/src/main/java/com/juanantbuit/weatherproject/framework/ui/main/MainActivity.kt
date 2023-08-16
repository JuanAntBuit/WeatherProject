package com.juanantbuit.weatherproject.framework.ui.main

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.juanantbuit.weatherproject.R
import com.juanantbuit.weatherproject.databinding.ActivityMainBinding
import com.juanantbuit.weatherproject.domain.models.NextDayInfoModel
import com.juanantbuit.weatherproject.framework.ui.dailyDetails.DailyDetailsFragment
import com.juanantbuit.weatherproject.framework.ui.searchList.SearchListActivity
import com.juanantbuit.weatherproject.utils.GPS_REQUEST_CODE
import com.juanantbuit.weatherproject.utils.LANG
import com.juanantbuit.weatherproject.utils.Quadruple
import com.juanantbuit.weatherproject.utils.UNITS
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var firstAppStart by Delegates.notNull<Boolean>()

    private lateinit var dailyDetailsFragment: DailyDetailsFragment

    private lateinit var nextDaysInfo: MutableList<NextDayInfoModel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(this.layoutInflater)
        setContentView(binding.root)

        dailyDetailsFragment = DailyDetailsFragment()

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editor = prefs.edit()

        createListeners()
        createObservers()
    }

    private fun createListeners() {
        binding.citySearcher.setOnClickListener {
            if (viewModel.isNetworkAvailable(this)) {
                launchCitySearchActivity("none")
            } else {
                showNoInternetMessage()
            }
        }

        binding.gpsButton.setOnClickListener {
            if (viewModel.isNetworkAvailable(this)) {
                viewModel.checkGPSPermission(this)
            } else {
                showNoInternetMessage()
            }
        }

        val nextDayInfoComponents = listOf(
            Triple(binding.nextDayInfo1, binding.nextDayImage1, binding.nextDay1),
            Triple(binding.nextDayInfo2, binding.nextDayImage2, binding.nextDay2),
            Triple(binding.nextDayInfo3, binding.nextDayImage3, binding.nextDay3),
            Triple(binding.nextDayInfo4, binding.nextDayImage4, binding.nextDay4)
        )

        for ((nextDayInfo, nextDayImage, nextDayView) in nextDayInfoComponents) {
            nextDayInfo.setOnClickListener {
                val index = nextDayInfoComponents.indexOfFirst { it.first == nextDayInfo }
                showDailyDetails(
                    nextDayImage,
                    nextDayView,
                    nextDaysInfo[index].temperatures.toDoubleArray(),
                    nextDaysInfo[index].averageTemp
                )
            }
        }

        binding.swiperefresh.setOnRefreshListener {
            if (viewModel.isNetworkAvailable(this)) {
                if (firstAppStart) {
                    binding.swiperefresh.isRefreshing = false
                    showSpecialMessage()
                    binding.specialMessage.text = getString(R.string.firstStartText)
                } else {
                    if (prefs.getBoolean("lastSavesIsCoordinated", false)) {
                        setCoordinates()
                    }
                    setGeonameId()
                }
            } else {
                binding.swiperefresh.isRefreshing = false
                showNoInternetMessage()
            }
        }

        binding.sidePanel.languageDropdownMenuText.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> { //english
                    saveLanguageUsed("en")
                    changeLanguage("en")
                }

                1 -> { //spanish
                    saveLanguageUsed("es")
                    changeLanguage("es")
                }
            }
        }

        binding.sidePanel.metricDropdownMenuText.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> { //metric system
                    saveUnitUsed("metric")
                    changeUnits("metric")
                }

                1 -> { //imperial system
                    saveUnitUsed("imperial")
                    changeUnits("imperial")
                }

                2 -> { //standard system
                    saveUnitUsed("standard")
                    changeUnits("standard")
                }
            }
        }

        val saveLocationQuadruples = listOf(
            Quadruple(
                binding.sidePanel.firstSaveLocation, "firstSaveName", "firstSaveGeoId", "firstSave"
            ), Quadruple(
                binding.sidePanel.secondSaveLocation,
                "secondSaveName",
                "secondSaveGeoId",
                "secondSave"
            ), Quadruple(
                binding.sidePanel.thirdSaveLocation, "thirdSaveName", "thirdSaveGeoId", "thirdSave"
            )
        )

        for ((locationView, saveNameKey, geoIdKey, searchType) in saveLocationQuadruples) {
            locationView.setOnClickListener {
                handleLocationClickBehavior(saveNameKey, geoIdKey, searchType)
            }
        }

        val cancelButtonTriples = listOf(
            Triple("firstSaveName", binding.sidePanel.firstSaveLocation, binding.sidePanel.cancel1),
            Triple(
                "secondSaveName", binding.sidePanel.secondSaveLocation, binding.sidePanel.cancel2
            ),
            Triple("thirdSaveName", binding.sidePanel.thirdSaveLocation, binding.sidePanel.cancel3)
        )

        for ((saveName, saveLocation, cancelButton) in cancelButtonTriples) {
            cancelButton.setOnClickListener {
                removeSavedCity(saveName, saveLocation, cancelButton)
            }
        }

        binding.menuButton.setOnClickListener {
            binding.drawer.openDrawer(GravityCompat.START)
        }
    }

    private fun handleLocationClickBehavior(
        saveNameKey: String, geoIdKey: String, searchType: String
    ) {
        if (viewModel.isNetworkAvailable(this)) {
            val saveName = prefs.getString(saveNameKey, "none")

            if (saveName != "none") {
                getSavedCityInfo(geoIdKey)
            } else {
                launchCitySearchActivity(searchType)
            }
        } else {
            showNoInternetMessage()
        }
    }

    private fun launchCitySearchActivity(searchType: String) {
        val intent = Intent(this, SearchListActivity::class.java)
        intent.putExtra("searchType", searchType)
        intent.flags = FLAG_ACTIVITY_NO_ANIMATION
        startActivity(intent)
    }

    private fun getSavedCityInfo(geoIdKey: String) {
        binding.drawer.closeDrawers()

        viewModel.setGeonameId(
            prefs.getString(geoIdKey, "3117735")!!
        )
    }

    private fun createObservers() {
        viewModel.currentDay.observe(this) { currentDay ->
            if (currentDay != null) {
                val daysOfWeek: Array<String> = resources.getStringArray(R.array.days_of_week)

                //Dynamically selects the id of the next 4 days, searches for the text by
                //that identifier and adds it to the activity.
                binding.nextDay1.text = daysOfWeek[viewModel.getCorrectIndex(currentDay + 1)]
                binding.nextDay2.text = daysOfWeek[viewModel.getCorrectIndex(currentDay + 2)]
                binding.nextDay3.text = daysOfWeek[viewModel.getCorrectIndex(currentDay + 3)]
                binding.nextDay4.text = daysOfWeek[viewModel.getCorrectIndex(currentDay + 4)]
            }
        }

        viewModel.geonameId.observe(this) { geoId ->
            if (!geoId.isNullOrEmpty()) {
                editor.putString("lastGeoId", geoId)
                editor.apply()

                viewModel.getCityInfoByGeonameId(geoId)
                viewModel.getForecastResponseByGeonameId(geoId)
            }
            viewModel.getCurrentDay()
        }

        viewModel.coordinates.observe(this) { coordinates ->
            if (coordinates != null) {
                coordinates["latitude"]?.let { editor.putFloat("lastLatitude", it) }
                coordinates["longitude"]?.let { editor.putFloat("lastLongitude", it) }
                editor.putBoolean("lastSavesIsCoordinated", true)
                editor.apply()

                viewModel.getCityInfoByCoordinates(
                    coordinates["latitude"]!!, coordinates["longitude"]!!
                )
                viewModel.getForecastResponseByCoordinates(
                    coordinates["latitude"]!!, coordinates["longitude"]!!
                )
            }
            viewModel.getCurrentDay()
        }

        viewModel.cityInfo.observe(this) { cityInfo ->
            if (cityInfo != null) {
                loadIcon(viewModel.getImageUrl(cityInfo.iconId[0].idIcon), binding.currentImage)
                binding.cityName.text =
                    getString(R.string.city_name, cityInfo.name, cityInfo.country.countryId)
                binding.currentTemp.text =
                    getString(R.string.temperature, cityInfo.mainInfo.temp.toInt())
                binding.tempFeelsLike.text =
                    getString(R.string.temperature, cityInfo.mainInfo.thermalSensation.toInt())
                binding.humidityPercentage.text =
                    getString(R.string.humidity_template, cityInfo.mainInfo.humidity)

                if (UNITS == "imperial") {
                    binding.windVelocity.text =
                        getString(R.string.wind_speed_imperial, cityInfo.windVelocity.speed.toInt())
                } else {
                    binding.windVelocity.text =
                        getString(R.string.wind_speed, cityInfo.windVelocity.speed.toInt())
                }
            }
        }

        viewModel.forecastResponse.observe(this) { forecastResponse ->
            nextDaysInfo = viewModel.getNextDaysInfo(forecastResponse!!)

            binding.nextDayTemp1.text = getString(R.string.temperature, nextDaysInfo[0].averageTemp)
            binding.nextDayTemp2.text = getString(R.string.temperature, nextDaysInfo[1].averageTemp)
            binding.nextDayTemp3.text = getString(R.string.temperature, nextDaysInfo[2].averageTemp)
            binding.nextDayTemp4.text = getString(R.string.temperature, nextDaysInfo[3].averageTemp)

            loadIcon(viewModel.getImageUrl(nextDaysInfo[0].iconId), binding.nextDayImage1)
            loadIcon(viewModel.getImageUrl(nextDaysInfo[1].iconId), binding.nextDayImage2)
            loadIcon(viewModel.getImageUrl(nextDaysInfo[2].iconId), binding.nextDayImage3)
            loadIcon(viewModel.getImageUrl(nextDaysInfo[3].iconId), binding.nextDayImage4)

            hideProgressBar()
            binding.swiperefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()

        firstAppStart = prefs.getBoolean("firstAppStart", true)

        val languagesStringArray = resources.getStringArray(R.array.languages)
        (binding.sidePanel.languageDropdownMenu.editText as? MaterialAutoCompleteTextView)?.setSimpleItems(
            languagesStringArray
        )

        val metricUnitsStringArray = resources.getStringArray(R.array.metric_units)
        (binding.sidePanel.metricDropdownMenu.editText as? MaterialAutoCompleteTextView)?.setSimpleItems(
            metricUnitsStringArray
        )

        if (viewModel.isNetworkAvailable(this)) {
            if (firstAppStart) {
                //Selects the user's preferred language as default language
                AppCompatDelegate.getApplicationLocales()

                val languageCode = prefs.getString("language", "en")
                changeLanguage(languageCode!!)

                when (languageCode) {
                    "en" -> {
                        binding.sidePanel.languageDropdownMenuText.setText(
                            getString(R.string.english_language), false
                        )
                    }

                    else -> {
                        binding.sidePanel.languageDropdownMenuText.setText(
                            getString(R.string.spanish_language), false
                        )
                    }
                }
                showSpecialMessage()
                binding.specialMessage.text = getString(R.string.firstStartText)
            } else {
                UNITS = prefs.getString("units", "metric")!!
                LANG = prefs.getString("language", "en")!!

                changeLanguage(LANG)

                when (LANG) {
                    "en" -> {
                        binding.sidePanel.languageDropdownMenuText.setText(
                            getString(R.string.english_language), false
                        )
                    }

                    else -> {
                        binding.sidePanel.languageDropdownMenuText.setText(
                            getString(R.string.spanish_language), false
                        )
                    }
                }

                when (UNITS) {
                    "metric" -> {
                        binding.sidePanel.metricDropdownMenuText.setText(
                            getString(R.string.celsius), false
                        )
                    }

                    "imperial" -> {
                        binding.sidePanel.metricDropdownMenuText.setText(
                            getString(R.string.fahrenheit), false
                        )
                    }

                    else -> {
                        binding.sidePanel.metricDropdownMenuText.setText(
                            getString(R.string.kelvin), false
                        )
                    }
                }
                showProgressBar()

                if (prefs.getBoolean("lastSavesIsCoordinated", false)) {
                    setCoordinates()
                }
                setGeonameId()
                setSaveLocations()
            }
        } else {
            showNoInternetMessage()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            GPS_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    viewModel.getCoordinatesFromGPS(this)
                }
                return
            }
        }
    }

    /*************************PRIVATE FUNCTIONS*************************/

    private fun showSpecialMessage() {
        binding.cityName.visibility = View.GONE
        binding.principalCardView.visibility = View.GONE
        binding.next4Days.visibility = View.GONE
        binding.horizontalScrollView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE

        binding.specialMessage.visibility = View.VISIBLE
    }

    private fun showProgressBar() {
        binding.cityName.visibility = View.GONE
        binding.principalCardView.visibility = View.GONE
        binding.next4Days.visibility = View.GONE
        binding.horizontalScrollView.visibility = View.GONE
        binding.specialMessage.visibility = View.GONE

        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.cityName.visibility = View.VISIBLE
        binding.principalCardView.visibility = View.VISIBLE
        binding.next4Days.visibility = View.VISIBLE
        binding.horizontalScrollView.visibility = View.VISIBLE
        binding.specialMessage.visibility = View.GONE

        binding.progressBar.visibility = View.GONE
    }

    private fun setCoordinates() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editor = prefs.edit()

        viewModel.setCoordinates(
            prefs.getFloat("lastLatitude", 0.0f), prefs.getFloat("lastLongitude", 0.0f)
        )
    }

    private fun setGeonameId() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editor = prefs.edit()

        viewModel.setGeonameId(
            prefs.getString("lastGeoId", "3117735")!!
        )
    }

    private fun setSaveLocations() {
        val firstSaveName = prefs.getString("firstSaveName", "none")
        val secondSaveName = prefs.getString("secondSaveName", "none")
        val thirdSaveName = prefs.getString("thirdSaveName", "none")

        if (firstSaveName != "none") {
            binding.sidePanel.firstSaveLocation.text = firstSaveName
            binding.sidePanel.cancel1.visibility = View.VISIBLE
        }

        if (secondSaveName != "none") {
            binding.sidePanel.secondSaveLocation.text = secondSaveName
            binding.sidePanel.cancel2.visibility = View.VISIBLE
        }

        if (thirdSaveName != "none") {
            binding.sidePanel.thirdSaveLocation.text = thirdSaveName
            binding.sidePanel.cancel3.visibility = View.VISIBLE
        }

    }

    private fun loadIcon(url: String, imageView: ImageView) {
        Glide.with(this).load(url).error(R.drawable.ic_launcher_foreground)
            .diskCacheStrategy(DiskCacheStrategy.ALL).fitCenter().into(imageView)
    }

    private fun showDailyDetails(
        imageView: ImageView, textView: TextView, temperatures: DoubleArray, averageTemp: Int
    ) {
        val bundle = Bundle()
        val bitMap = (imageView.drawable as BitmapDrawable).bitmap
        saveImageFromBitmap(bitMap)

        bundle.putString("dayName", textView.text as String?)
        bundle.putDoubleArray("temperatures", temperatures)
        bundle.putInt("averageTemp", averageTemp)

        if (dailyDetailsFragment.isAdded) {
            dailyDetailsFragment.dismiss()
        }

        dailyDetailsFragment.arguments = bundle
        dailyDetailsFragment.show(supportFragmentManager, DailyDetailsFragment.TAG)
    }

    private fun saveImageFromBitmap(bitmap: Bitmap) {
        val fileName = "dayImage"

        try {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)

            val fo: FileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)

            fo.write(bytes.toByteArray())
            fo.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeSavedCity(saveName: String, saveLocation: TextView, cancelButton: View) {
        editor.remove(saveName)
        editor.apply()

        saveLocation.text = getString(R.string.touch_to_save_location)
        cancelButton.visibility = View.GONE
    }

    private fun saveLanguageUsed(language: String) {
        editor.putString("language", language)
        editor.apply()
    }

    private fun saveUnitUsed(unit: String) {
        editor.putString("units", unit)
        editor.apply()
    }

    private fun changeLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    private fun changeUnits(units: String) {
        UNITS = units
        recreate()
    }

    private fun showNoInternetMessage() {
        showSpecialMessage()
        binding.specialMessage.text = getString(R.string.noInternetMessage)
    }
}
