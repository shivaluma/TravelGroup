package com.ygaps.travelapp.view.tour

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.GoogleMap
import com.ygaps.travelapp.R

import com.google.android.gms.maps.SupportMapFragment


import android.location.Location

import com.google.android.gms.maps.CameraUpdateFactory

import android.Manifest.permission
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.transition.Slide
import android.util.JsonReader
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.devlomi.record_view.OnRecordListener
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.gson.JsonObject
import com.google.maps.android.PolyUtil
import com.ygaps.travelapp.ResponseGetTourNotice
import com.ygaps.travelapp.ResponseSendNotice
import com.ygaps.travelapp.manager.Constant
import com.ygaps.travelapp.manager.doAsync
import com.ygaps.travelapp.network.model.ApiServiceGetTourNotices
import com.ygaps.travelapp.network.model.ApiServiceSendTourNotice
import com.ygaps.travelapp.network.model.WebAccess
import com.ygaps.travelapp.notification
import kotlinx.android.synthetic.main.activity_tour_follow.*
import kotlinx.android.synthetic.main.activity_tour_follow.view.*
import kotlinx.android.synthetic.main.activity_tour_info.*
import kotlinx.android.synthetic.main.popup_chat.view.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback


class TourFollowActivity : AppCompatActivity(), OnMapReadyCallback {

    lateinit var mGoogleMap: GoogleMap
    lateinit var myLocation: Location
    lateinit var destinationLatLng: LatLng
    lateinit var polyLineToDestination : Polyline
    lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    var mUserId : Int = 0
    var mTourId : Int = 0
    var mToken : String = ""
    var mListChat = ArrayList<notification>()
    var mChatAdapter = ChatAdapter(mListChat)
    var isPopupOpen = false
    lateinit var mChatRecyclerView: RecyclerView
    lateinit var mMessageReceiver : BroadcastReceiver
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.hide()

        setContentView(R.layout.activity_tour_follow)

        mUserId = getSharedPreferences("logintoken", Context.MODE_PRIVATE).getInt("userId", 126)
        mTourId = intent.extras!!.getInt("tourId")
        mToken = intent.extras!!.getString("token")!!

        fusedLocationProviderClient = FusedLocationProviderClient(this.applicationContext)

        destinationLatLng = LatLng(intent.extras!!.getDouble("destinationLat", 10.7629)
            ,intent.extras!!.getDouble("destinationLng", 106.6822))

        mMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val onTourId = intent!!.extras!!.getString("tourId")
                if (onTourId!!.toInt() == mTourId) {
                    ApiRequestGetNotices()
                }
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
            IntentFilter("notify-new-message")
        )


        showLocationPrompt()


        fetchLocation()

        tourFollowChatBtn.setOnClickListener {
            popupChat()
        }

        ApiRequestGetNotices()

    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val PERMISSION_ID = 12

    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver)
        super.onDestroy()
    }

    override fun onMapReady(p0: GoogleMap?) {
        mGoogleMap = p0!!
        mGoogleMap.isMyLocationEnabled = true
        mGoogleMap.uiSettings.isMyLocationButtonEnabled = true

        var src = LatLng(myLocation.latitude,myLocation.longitude)
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(src, 15.0f))

        drawPath(src, destinationLatLng)

        mGoogleMap.setOnMapClickListener {

        }

    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            LocationRequest.PRIORITY_HIGH_ACCURACY -> {
                if (resultCode == Activity.RESULT_OK) {

                } else {
                    Log.e("Status: ","Off")
                }
            }
        }
    }

    private fun fetchLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                val task = fusedLocationProviderClient.lastLocation
                task.addOnSuccessListener { location ->
                    if (location != null) {
                        myLocation = location
                        val mapFragment =
                            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                        mapFragment.getMapAsync(this)
                    }
                    else {
                        requestNewLocationData()
                    }
                }
            }

        }
        else {
            requestPermissions()
        }
    }

    fun addMarker(ggMap: GoogleMap, pos: LatLng, name: String, drawable: Int): Marker {
        return ggMap.addMarker(
            MarkerOptions()
                .position(pos)
                .icon(bitmapDescriptorFromVector(applicationContext, drawable))
                .title(name)
        )
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap =
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }


    fun drawPath(src: LatLng, dest: LatLng) {
        addMarker(mGoogleMap,src,"My location",R.drawable.ic_startpoint)
        addMarker(mGoogleMap,dest,"Destination",R.drawable.ic_endpoint)
        doAsync {
            val path: MutableList<List<LatLng>> = ArrayList()
            val urlDirections = "https://maps.googleapis.com/maps/api/directions/json?origin=${src.latitude},${src.longitude}&destination=${dest.latitude},${dest.longitude}&key=${Constant.ggMapApiKey}"
            val directionsRequest = object : StringRequest(Request.Method.GET, urlDirections, Response.Listener<String> {
                    response ->
                val jsonResponse = JSONObject(response)
                // Get routes
                val routes = jsonResponse.getJSONArray("routes")
                val legs = routes.getJSONObject(0).getJSONArray("legs")
                val steps = legs.getJSONObject(0).getJSONArray("steps")
                for (i in 0 until steps.length()) {
                    val points = steps.getJSONObject(i).getJSONObject("polyline").getString("points")
                    path.add(PolyUtil.decode(points))
                }
                for (i in 0 until path.size) {
                    mGoogleMap.addPolyline(PolylineOptions().addAll(path[i]).color(Color.BLUE).width(5.0f))
                }
            }, Response.ErrorListener {
                    _ ->
            }){}
            val requestQueue = Volley.newRequestQueue(this)
            requestQueue.add(directionsRequest)
        }.execute()
    }


    fun isLocationEnabled(): Boolean {
        var locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            return true
        }
        return false
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_ID
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            myLocation = locationResult.lastLocation
            val mapFragment =
                supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync(this@TourFollowActivity)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationProviderClient!!.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }


    private fun showLocationPrompt() {
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        result.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                // All location settings are satisfied. The client can initialize location
                // requests here.
            } catch (exception: ApiException) {
                when (exception.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                            // Cast to a resolvable exception.
                            val resolvable: ResolvableApiException = exception as ResolvableApiException
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolvable.startResolutionForResult(
                                this, LocationRequest.PRIORITY_HIGH_ACCURACY
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            // Ignore the error.
                        } catch (e: ClassCastException) {
                            // Ignore, should be an impossible error.
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        // Location settings are not satisfied. But could be fixed by showing the
                        // user a dialog.

                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                    }
                }
            }
        }
    }


    fun popupChat() {
        val inflater: LayoutInflater =
            getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.popup_chat, null)

        isPopupOpen = true
        mChatRecyclerView = view.chatRecyclerView
        view.chatRecyclerView.adapter = mChatAdapter
        val layoutManager = LinearLayoutManager(applicationContext)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        layoutManager.stackFromEnd = true
        view.chatRecyclerView.layoutManager = layoutManager






        val displayMetrics =getResources().getDisplayMetrics()
        val screenWidthInDp = displayMetrics.heightPixels / displayMetrics.density

        val popupWindow = PopupWindow(
            view, // Custom view to show in popup window
            LinearLayout.LayoutParams.MATCH_PARENT, // Width of popup window
            (screenWidthInDp.toInt() - 100)*2, // Window height
            true
        )

        // Set an elevation for the popup window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.elevation = 10.0F
        }


        // If API level 23 or higher then execute the code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Create a new slide animation for popup window enter transition
            val slideIn = Slide()
            slideIn.slideEdge = Gravity.TOP
            popupWindow.enterTransition = slideIn

            // Slide animation for popup window exit transition
            val slideOut = Slide()
            slideOut.slideEdge = Gravity.TOP
            popupWindow.exitTransition = slideOut

        }

        view.chatSend.setOnClickListener {
            if (view.chatContent.text.toString().isNotEmpty()) {
                ApiRequestSendNotice(view.chatContent.text.toString())
                view.chatContent.setText("")
                view.chatContent.clearFocus()
            }
            else {
                view.chatContent.error = "Cannot be empty!!"
            }
        }



        // Set a dismiss listener for popup window
        popupWindow.setOnDismissListener {
            isPopupOpen = false
        }


        // Finally, show the popup window on app
        popupWindow.showAsDropDown(tourFollowChatBtn,0,20)
    }


    fun ApiRequestGetNotices() {
        doAsync {
            val service = WebAccess.retrofit.create(ApiServiceGetTourNotices::class.java)

            val call = service.getNotices(mToken , mTourId, 1, "999" )
            call.enqueue(object : Callback<ResponseGetTourNotice> {
                override fun onFailure(call: Call<ResponseGetTourNotice>, t: Throwable) {
                    Toast.makeText(applicationContext, t.message, Toast.LENGTH_LONG).show()
                }

                override fun onResponse(
                    call: Call<ResponseGetTourNotice>,
                    response: retrofit2.Response<ResponseGetTourNotice>
                ) {
                    if (response.code() != 200) {
                        Toast.makeText(applicationContext, response.errorBody().toString(), Toast.LENGTH_LONG).show()
                    } else {
                        Log.d("abab",response.body().toString())
                        mListChat.clear()
                        mListChat.addAll(response.body()!!.notiList)
                        mChatAdapter.notifyDataSetChanged()
                        if (isPopupOpen) {
                            mChatRecyclerView.smoothScrollToPosition(mListChat.size - 1)
                        }
                    }
                }
            })
        }.execute()
    }


    fun ApiRequestSendNotice(notice : String) {
        doAsync {
            val service = WebAccess.retrofit.create(ApiServiceSendTourNotice::class.java)
            val body = JsonObject()


            body.addProperty("tourId", mTourId)
            body.addProperty("userId", mUserId)
            body.addProperty("noti", notice)
            val call = service.sendNotice(mToken , body )
            call.enqueue(object : Callback<ResponseSendNotice> {
                override fun onFailure(call: Call<ResponseSendNotice>, t: Throwable) {
                    Toast.makeText(applicationContext, t.message, Toast.LENGTH_LONG).show()
                }

                override fun onResponse(
                    call: Call<ResponseSendNotice>,
                    response: retrofit2.Response<ResponseSendNotice>
                ) {
                    if (response.code() != 200) {
                        Toast.makeText(applicationContext, response.errorBody().toString(), Toast.LENGTH_LONG).show()
                    } else {

                        ApiRequestGetNotices()
                    }
                }
            })
        }.execute()
    }


    inner class ChatAdapter(data: ArrayList<notification>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var data = ArrayList<notification>()

        init {
            this.data = data
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(applicationContext)
            val itemView : View
            if (viewType == 1) {
                itemView = inflater.inflate(R.layout.item_send_message, parent, false)
                return RecyclerViewHolderSend(itemView)
            }
            else {
                itemView = inflater.inflate(R.layout.item_receive_message, parent, false)
                return RecyclerViewHolderRecv(itemView)
            }

        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = data.get(position)
            if (holder.itemViewType == 1) {
                var temp = holder as RecyclerViewHolderSend
                temp.bind(item)
            }
            else {
                var temp = holder as RecyclerViewHolderRecv
                temp.bind(item)
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun getItemViewType(position: Int): Int {
            if (mUserId == data[position].userId) {
                return 1
            }
            return 0
        }

        inner class RecyclerViewHolderRecv(itemView: View) : RecyclerView.ViewHolder(itemView) {
            internal var name: TextView
            //internal var time: TextView
            internal var body: TextView
            internal var avatar: ImageView

            init {
                name = itemView.findViewById(R.id.text_message_name)
                //time = itemView.findViewById(R.id.text_message_time)
                body = itemView.findViewById(R.id.text_message_body)
                avatar = itemView.findViewById(R.id.image_message_profile)
            }

            fun bind(message : notification) {
                body.text = message.notification
                name.text = message.name
            }
        }

        inner class RecyclerViewHolderSend(itemView: View) : RecyclerView.ViewHolder(itemView) {
            internal var body: TextView

            init {
                body = itemView.findViewById(R.id.text_message_body)
            }

            fun bind(message : notification) {
                body.text = message.notification
            }
        }
    }


}





