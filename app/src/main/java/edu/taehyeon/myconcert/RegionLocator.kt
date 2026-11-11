package edu.taehyeon.myconcert

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import edu.taehyeon.myconcert.data.Region
import edu.taehyeon.myconcert.data.Regions
import java.util.Locale
import java.util.concurrent.Executors

/** Foreground one-shot location. Coordinates are neither stored nor sent to KOPIS. */
class RegionLocator(context: Context) {
    private val app=context.applicationContext
    private val manager=app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val executor=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private val signals=mutableListOf<CancellationSignal>()
    private var generation=0
    private var resolving=false
    private var timeout:Runnable?=null
    fun hasPermission()=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun locate(onRegion:(Region)->Unit,onFailure:(String)->Unit) {
        cancel(); val token=generation
        if(!hasPermission()) { onFailure("위치 권한 없이도 지도에서 지역을 선택할 수 있어요.");return }
        val fine=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val providers=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER)
            .filter { (it!=LocationManager.GPS_PROVIDER || fine) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if(providers.isEmpty()) { onFailure("위치 서비스를 켜거나 지도에서 지역을 선택해 주세요.");return }
        fun failure(message:String) {
            if(generation==token) { cancel();onFailure(message) }
        }
        fun resolve(location:Location) {
            if(generation!=token || resolving) return
            resolving=true
            signals.forEach { it.cancel() };signals.clear()
            executor.execute {
                @Suppress("DEPRECATION")
                val region=runCatching {
                    val address=Geocoder(app,Locale.KOREA).getFromLocation(location.latitude,location.longitude,1)?.firstOrNull()
                    if(address?.countryCode!="KR") null else Regions.fromAddress(address.adminArea,address.getAddressLine(0))
                }.getOrNull()
                handler.post {
                    if(generation==token) {
                        if(region!=null) { cancel();onRegion(region) }
                        else failure("현재 위치의 시·도를 확인하지 못했어요. 지도에서 선택해 주세요.")
                    }
                }
            }
        }
        timeout=Runnable { failure("위치를 확인하지 못했어요. 기존 지역을 유지하며 지도에서 선택할 수 있어요.") }
            .also { handler.postDelayed(it,20_000) }
        val cached=providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis()-it.time in 0..300_000 }.maxByOrNull { it.time }
        if(cached!=null) { resolve(cached);return }
        var pending=providers.size
        providers.forEach { provider ->
            val signal=CancellationSignal();signals+=signal
            runCatching {
                LocationManagerCompat.getCurrentLocation(manager,provider,signal,ContextCompat.getMainExecutor(app)) { location ->
                    if(generation==token) {
                        pending--
                        if(location!=null) resolve(location)
                        else if(pending==0 && !resolving) failure("위치를 확인하지 못했어요. 지도에서 지역을 선택해 주세요.")
                    }
                }
            }.onFailure { pending-- }
        }
        if(pending==0 && !resolving) failure("위치 서비스를 사용할 수 없어요. 지도에서 지역을 선택해 주세요.")
    }
    fun cancel() {
        generation++;resolving=false
        signals.forEach { it.cancel() };signals.clear()
        timeout?.let(handler::removeCallbacks);timeout=null
    }
    fun close() { cancel();executor.shutdownNow() }
}
