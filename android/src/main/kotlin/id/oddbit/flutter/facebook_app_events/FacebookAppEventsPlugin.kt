package id.oddbit.flutter.facebook_app_events

import android.os.Bundle
import android.util.Log
import androidx.annotation.NonNull
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.Currency

/** FacebookAppEventsPlugin */
class FacebookAppEventsPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var appEventsLogger: AppEventsLogger
    private lateinit var anonymousId: String

    private val logTag = "FacebookAppEvents"

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter.oddbit.id/facebook_app_events")
        channel.setMethodCallHandler(this)
        appEventsLogger = AppEventsLogger.newLogger(flutterPluginBinding.applicationContext)
        anonymousId = AppEventsLogger.getAnonymousAppDeviceGUID(flutterPluginBinding.applicationContext)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "clearUserData" -> handleClearUserData(result)
            "setUserData" -> handleSetUserData(call, result)
            "clearUserID" -> handleClearUserId(result)
            "flush" -> handleFlush(result)
            "getApplicationId" -> handleGetApplicationId(result)
            "logEvent" -> handleLogEvent(call, result)
            "logPushNotificationOpen" -> handlePushNotificationOpen(call, result)
            "setUserID" -> handleSetUserId(call, result)
            "setAutoLogAppEventsEnabled" -> handleSetAutoLogAppEventsEnabled(call, result)
            "setDataProcessingOptions" -> handleSetDataProcessingOptions(call, result)
            "getAnonymousId" -> handleGetAnonymousId(result)
            "logPurchase" -> handlePurchased(call, result)
            "setAdvertiserTracking" -> handleSetAdvertiserTracking(result)
            else -> result.notImplemented()
        }
    }

    private fun handleClearUserData(result: Result) {
        AppEventsLogger.clearUserData()
        result.success(null)
    }

    private fun handleSetUserData(call: MethodCall, result: Result) {
        val parameters = call.argument<Map<String, Any?>>("parameters")
        val parameterBundle = createBundleFromMap(parameters)

        AppEventsLogger.setUserData(
            parameterBundle?.getString("email"),
            parameterBundle?.getString("firstName"),
            parameterBundle?.getString("lastName"),
            parameterBundle?.getString("phone"),
            parameterBundle?.getString("dateOfBirth"),
            parameterBundle?.getString("gender"),
            parameterBundle?.getString("city"),
            parameterBundle?.getString("state"),
            parameterBundle?.getString("zip"),
            parameterBundle?.getString("country")
        )
        result.success(null)
    }

    private fun handleClearUserId(result: Result) {
        AppEventsLogger.clearUserID()
        result.success(null)
    }

    private fun handleFlush(result: Result) {
        appEventsLogger.flush()
        result.success(null)
    }

    private fun handleGetApplicationId(result: Result) {
        result.success(appEventsLogger.applicationId)
    }

    private fun handleGetAnonymousId(result: Result) {
        result.success(anonymousId)
    }

    private fun handleSetAdvertiserTracking(result: Result) {
        // Androidda bu funksiya hozircha qo'llab-quvvatlanmaydi
        result.success(null)
    }

    private fun handleLogEvent(call: MethodCall, result: Result) {
        val eventName = call.argument<String>("name") ?: return result.error("INVALID_EVENT", "Event name is required", null)
        val parameters = call.argument<Map<String, Any?>>("parameters")
        val valueToSum = call.argument<Double>("_valueToSum")

        when {
            valueToSum != null && parameters != null -> appEventsLogger.logEvent(eventName, valueToSum, createBundleFromMap(parameters))
            valueToSum != null -> appEventsLogger.logEvent(eventName, valueToSum)
            parameters != null -> appEventsLogger.logEvent(eventName, createBundleFromMap(parameters))
            else -> appEventsLogger.logEvent(eventName)
        }
        result.success(null)
    }

    private fun handlePushNotificationOpen(call: MethodCall, result: Result) {
        val payload = call.argument<Map<String, Any?>>("payload") ?: return result.error("INVALID_PAYLOAD", "Payload is required", null)
        val action = call.argument<String>("action")
        val payloadBundle = createBundleFromMap(payload) ?: Bundle()

        if (action != null) {
            appEventsLogger.logPushNotificationOpen(payloadBundle, action)
        } else {
            appEventsLogger.logPushNotificationOpen(payloadBundle)
        }
        result.success(null)
    }

    private fun handleSetUserId(call: MethodCall, result: Result) {
        val id = call.arguments<String>() ?: return result.error("INVALID_ID", "User ID is required", null)
        AppEventsLogger.setUserID(id)
        result.success(null)
    }

    private fun handleSetAutoLogAppEventsEnabled(call: MethodCall, result: Result) {
        val enabled = call.arguments<Boolean>() ?: return result.error("INVALID_ARG", "Enabled flag is required", null)
        FacebookSdk.setAutoLogAppEventsEnabled(enabled)
        result.success(null)
    }

    private fun handleSetDataProcessingOptions(call: MethodCall, result: Result) {
        val options = call.argument<ArrayList<String>>("options") ?: arrayListOf()
        val country = call.argument<Int>("country") ?: 0
        val state = call.argument<Int>("state") ?: 0

        FacebookSdk.setDataProcessingOptions(options.toTypedArray(), country, state)
        result.success(null)
    }

    private fun handlePurchased(call: MethodCall, result: Result) {
        val amount = call.argument<Double>("amount")?.toBigDecimal() ?: return result.error("INVALID_AMOUNT", "Amount is required", null)
        val currencyCode = call.argument<String>("currency") ?: return result.error("INVALID_CURRENCY", "Currency is required", null)
        val parameters = call.argument<Map<String, Any?>>("parameters")
        val parameterBundle = createBundleFromMap(parameters) ?: Bundle()

        val currency = try {
            Currency.getInstance(currencyCode)
        } catch (e: IllegalArgumentException) {
            return result.error("INVALID_CURRENCY", "Invalid currency code: $currencyCode", null)
        }

        appEventsLogger.logPurchase(amount, currency, parameterBundle)
        result.success(null)
    }

    private fun createBundleFromMap(parameterMap: Map<String, Any?>?): Bundle? {
        if (parameterMap == null) return null

        val bundle = Bundle()
        for ((key, value) in parameterMap) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Map<*, *> -> bundle.putBundle(key, createBundleFromMap(value as Map<String, Any?>))
                else -> Log.w(logTag, "Unsupported parameter type for key $key: ${value?.javaClass}")
            }
        }
        return bundle
    }
}
