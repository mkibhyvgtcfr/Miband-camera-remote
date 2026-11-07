package com.miband.camremote.band

import android.app.Activity
import android.content.Context
import com.miband.camremote.data.BandLinkState
import com.miband.camremote.data.CameraRepository
import com.miband.camremote.data.LogLevel
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import com.xiaomi.xms.wearable.service.ServiceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Bridge between the watch Vela app and this Android app using Xiaomi Wear's
 * official "interconnect" message API. The Vela package and this APK share the
 * same applicationId and signing certificate, which is required by Xiaomi Wear.
 */
class WearableBridge(
    private val context: Context,
    private val repository: CameraRepository,
    private val scope: CoroutineScope,
) {
    private val nodeApi: NodeApi by lazy { Wearable.getNodeApi(context.applicationContext) }
    private val authApi: AuthApi by lazy { Wearable.getAuthApi(context.applicationContext) }
    private val messageApi: MessageApi by lazy { Wearable.getMessageApi(context.applicationContext) }
    private val serviceApi: ServiceApi by lazy { Wearable.getServiceApi(context.applicationContext) }

    private var node: Node? = null
    private var listenerRegistered = false
    private var askedPermission = false
    private var connectJob: Job? = null
    private var stateJob: Job? = null
    private var initialized = false
    private var activityRef: WeakReference<Activity>? = null
    private var discoverAttempts = 0

    private val listener = OnMessageReceivedListener { nodeId, bytes ->
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrElse { "" }
        repository.log("手环 → ${text.take(120)}")
        scope.launch(Dispatchers.IO) {
            val reply = runCatching { repository.handleBandCommand(text) }
                .getOrElse {
                    """{"v":1,"type":"ack","ok":false,"message":"服务端错误"}"""
                }
            send(reply)
            delay(80)
            send(repository.bandStatusJson())
        }
    }

    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun start(activity: Activity? = null) {
        if (activity != null) bindActivity(activity)
        if (initialized) {
            if (node == null) discover()
            return
        }
        initialized = true
        runCatching {
            serviceApi.registerServiceConnectionListener(object : OnServiceConnectionListener {
                override fun onServiceConnected() {
                    repository.log("小米穿戴服务已连接")
                    discover()
                }

                override fun onServiceDisconnected() {
                    repository.log("小米穿戴服务已断开", LogLevel.WARN)
                    node = null
                    repository.updateBand(BandLinkState(message = "小米穿戴服务未运行"))
                }
            })
        }.onFailure {
            repository.log("无法监听小米穿戴服务：${it.message}", LogLevel.WARN)
        }
        discover()
        connectJob = scope.launch {
            while (true) {
                delay(5_000)
                if (node == null) discover()
            }
        }
    }

    /**
     * Explicit user action from the phone UI. Using a visible Activity is
     * important: Xiaomi Wear displays its permission sheet for the calling
     * Activity, not for an Application context.
     */
    fun authorize(activity: Activity) {
        bindActivity(activity)
        askedPermission = false
        start(activity)
        val target = node
        if (target == null) {
            repository.updateBand(BandLinkState(message = "正在查找手环，请保持小米运动健康已连接"))
            discover()
        } else {
            requestPermission(target, force = true)
        }
    }

    private fun discover() {
        discoverAttempts++
        runCatching {
            nodeApi.connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isNullOrEmpty()) {
                        if (discoverAttempts <= 1 || discoverAttempts % 6 == 0) {
                            repository.log("未发现已连接的小米手环（第 $discoverAttempts 次）", LogLevel.WARN)
                        }
                        repository.updateBand(BandLinkState(message = "等待手环连接（请打开小米运动健康）"))
                        return@addOnSuccessListener
                    }
                    val target = nodes.first()
                    bindTo(target)
                }
                .addOnFailureListener { e ->
                    repository.log("读取手环节点失败：${e.message}", LogLevel.WARN)
                    repository.updateBand(BandLinkState(message = "无法读取手环节点，请检查小米运动健康"))
                }
        }.onFailure {
            repository.log("穿戴 SDK 未就绪：${it.message}", LogLevel.WARN)
        }
    }

    private fun bindTo(target: Node) {
        if (node?.id == target.id && listenerRegistered) return
        node = target
        repository.log("发现手环：${target.name}")
        authApi.checkPermission(target.id, Permission.DEVICE_MANAGER)
            .addOnSuccessListener { granted ->
                if (granted) {
                    repository.updateBand(BandLinkState(connected = true, authorized = true, nodeName = target.name, message = "手环已连接"))
                    registerMessageListener(target)
                } else if (activityRef?.get() != null) {
                    requestPermission(target)
                } else {
                    repository.log("手环尚未授权，等待用户点击「请求授权」", LogLevel.WARN)
                    repository.updateBand(BandLinkState(nodeName = target.name, authorized = false, message = "请在手机上点击「请求手环授权」"))
                }
            }
            .addOnFailureListener { e ->
                repository.log("检查手环权限失败：${e.message}", LogLevel.WARN)
                repository.updateBand(BandLinkState(nodeName = target.name, message = "等待手环授权"))
            }
    }

    private fun requestPermission(target: Node, force: Boolean = false) {
        if (askedPermission && !force) return
        val activity = activityRef?.get()
        if (activity == null) {
            repository.updateBand(
                BandLinkState(nodeName = target.name, authorized = false, message = "请打开手机端并点击「请求手环授权」")
            )
            repository.log("没有前台 Activity，暂缓申请手环权限", LogLevel.WARN)
            return
        }
        askedPermission = true
        repository.updateBand(BandLinkState(nodeName = target.name, message = "请在小米运动健康中允许连接"))
        runCatching {
            Wearable.getAuthApi(activity)
                .requestPermission(target.id, Permission.DEVICE_MANAGER, Permission.NOTIFY)
                .addOnSuccessListener { permissions ->
                    val granted = permissions?.any { it.name == Permission.DEVICE_MANAGER.name } == true
                    if (granted) {
                        repository.updateBand(BandLinkState(connected = true, authorized = true, nodeName = target.name, message = "手环已连接"))
                        registerMessageListener(target)
                        repository.log("手环连接权限已授予", LogLevel.SUCCESS)
                    } else {
                        repository.log("手环连接权限未授予，请重试", LogLevel.WARN)
                        repository.updateBand(BandLinkState(nodeName = target.name, authorized = false, message = "授权被拒绝，可重试"))
                    }
                }
                .addOnFailureListener { e ->
                    repository.log("申请手环权限失败：${e.message}", LogLevel.ERROR)
                    repository.updateBand(BandLinkState(nodeName = target.name, message = "手环授权失败：${e.message}"))
                }
        }.onFailure { e ->
            repository.log("无法打开手环授权页：${e.message}", LogLevel.ERROR)
            repository.updateBand(BandLinkState(nodeName = target.name, message = "无法打开手环授权页"))
        }
    }

    private fun registerMessageListener(target: Node) {
        if (listenerRegistered) return
        runCatching {
            messageApi.addListener(target.id, listener)
                .addOnSuccessListener {
                    listenerRegistered = true
                    repository.log("手环消息通道已建立", LogLevel.SUCCESS)
                    repository.updateBand(BandLinkState(connected = true, authorized = true, nodeName = target.name, message = "手环已连接"))
                    startStatePublisher(target)
                    send(repository.bandStatusJson())
                }
                .addOnFailureListener { e ->
                    listenerRegistered = false
                    repository.log("建立手环消息通道失败：${e.message}", LogLevel.ERROR)
                    repository.updateBand(BandLinkState(nodeName = target.name, message = "手环消息通道不可用"))
                }
        }
    }

    private fun startStatePublisher(target: Node) {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            var last = ""
            repository.snapshot.collectLatest { snap ->
                delay(180)
                val json = repository.bandStatusJson()
                if (json != last) {
                    last = json
                    send(json)
                }
            }
        }
    }

    private fun send(text: String) {
        val target = node ?: return
        if (!listenerRegistered) return
        runCatching {
            messageApi.sendMessage(target.id, text.toByteArray(Charsets.UTF_8))
                .addOnFailureListener { e ->
                    repository.log("发送到手环失败：${e.message}", LogLevel.WARN)
                }
        }
    }

    fun stop() {
        connectJob?.cancel()
        stateJob?.cancel()
        runCatching {
            if (listenerRegistered) node?.let { messageApi.removeListener(it.id) }
        }
        listenerRegistered = false
        node = null
        initialized = false
        askedPermission = false
        discoverAttempts = 0
    }
}
