package com.rel.adbhelper.core.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * This app's single ADB identity: one RSA key pair and self-signed certificate, generated once
 * and persisted in app-private storage, used for every ADB connection this app makes. Built
 * with BouncyCastle rather than Android's internal `android.sun.security.x509.*` classes (what
 * the library's own sample app uses) — those are non-SDK/hidden APIs, increasingly restricted
 * on current Android versions, and BouncyCastle is a well-known, portable alternative for
 * exactly this "build a self-signed cert" task.
 */
class AdbKeyConnectionManager private constructor(
    private val privateKey: PrivateKey,
    private val certificate: Certificate,
) : AbsAdbConnectionManager() {

    /** The port of the target [connectTo] last succeeded against, if any — the library tracks
     * the connected host itself (`hostAddress`) but not the port, so we track both here to
     * detect "connected, but to a different target" and reconnect instead of reusing the
     * existing connection silently. */
    private var connectedPort: Int? = null

    init {
        setApi(Build.VERSION.SDK_INT)
        // The library's own default connect timeout is Long.MAX_VALUE — an unauthorized or
        // unreachable target would otherwise hang a connect attempt forever.
        setTimeout(15, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "ADB Helper"

    /** Guards against concurrent callers racing into [connect] at once — see [ConnectGate]'s doc
     * for why that's a real, previously-live bug and not just a theoretical one. Not a plain
     * `@Synchronized`: that would be a second place a thread can get stuck forever against a
     * target that accepts the raw TCP connection but never finishes the ADB handshake, since
     * `connect`'s underlying `java.net.Socket` connect is blocking with no suspension point for
     * `withTimeout` to cancel. A coroutine [Mutex] (inside [ConnectGate]) keeps every OTHER
     * caller's wait a real, cancellable suspension point instead. */
    private val connectGate = ConnectGate()

    /** Serializes [openStream] itself — live-reproduced: several `shell()` calls fired at once
     * (e.g. [com.rel.adbhelper.features.apps.AppsViewModel.load]'s three concurrent package-list
     * reads) opening streams on this one shared connection at the same instant silently
     * corrupted some of them — no exception, just a command that came back empty, which read as
     * "this device has no apps installed" instead of the real failure. The library's own
     * `openStream` has no documented thread-safety guarantee for concurrent calls on one
     * connection, matching the behavior actually observed. Only the brief act of opening is
     * serialized here; the read/write that follows on each resulting stream still runs
     * concurrently with the others, so this doesn't undo the latency win of running them in
     * parallel in the first place. */
    private val streamOpenMutex = Mutex()

    suspend fun openStreamSerialized(service: String): io.github.muntashirakon.adb.AdbStream =
        streamOpenMutex.withLock { openStream(service) }

    /**
     * Connects to [host]:[port], reconnecting first if already connected to a different target.
     * Reusing an existing connection to the wrong target would make every later command run
     * against the wrong device.
     */
    suspend fun connectTo(host: String, port: Int): Boolean = connectGate.ensureConnected(
        alreadyConnected = { isConnected && hostAddress == host && connectedPort == port },
        connect = {
            if (isConnected) disconnect()
            val connected = connect(host, port)
            connectedPort = if (connected) port else null
            connected
        },
    )

    companion object {
        @Volatile
        private var instance: AdbKeyConnectionManager? = null

        fun getInstance(context: Context): AdbKeyConnectionManager =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        /**
         * A fresh connection manager, independent of the [getInstance] singleton — for scan
         * verification, where several LAN candidates must each get a real ADB handshake at once
         * without one hijacking the app's single real session connection (the singleton holds
         * exactly one active connection; that's correct for the app's actual session, but wrong
         * for probing N candidates concurrently). Reuses the same persisted key — identity is
         * per-app, not per-connection — but owns its own connect/stream state, so several of
         * these can run truly in parallel. Caller is responsible for `disconnect()`ing it.
         */
        fun createDisposable(context: Context): AdbKeyConnectionManager {
            val (privateKey, certificate) = loadOrGenerateKeyPair(context.applicationContext)
            return AdbKeyConnectionManager(privateKey, certificate)
        }

        private fun create(context: Context): AdbKeyConnectionManager {
            val (privateKey, certificate) = loadOrGenerateKeyPair(context)
            return AdbKeyConnectionManager(privateKey, certificate)
        }

        private fun loadOrGenerateKeyPair(context: Context): Pair<PrivateKey, Certificate> {
            val keyFile = File(context.filesDir, "adb_private.key")
            val certFile = File(context.filesDir, "adb_cert.pem")
            if (keyFile.exists() && certFile.exists()) {
                val privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream())
                return privateKey to certificate
            }
            val (privateKey, certificate) = generateKeyPair()
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
            return privateKey to certificate
        }

        private fun generateKeyPair(): Pair<PrivateKey, Certificate> {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048, SecureRandom())
            }.generateKeyPair()
            val certificate = selfSignedCertificate(keyPair.public, keyPair.private)
            return keyPair.private to certificate
        }

        private fun selfSignedCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
            val subject = X500Name("CN=ADB Helper")
            val now = System.currentTimeMillis()
            val serial = BigInteger.valueOf(now)
            val notBefore = Date(now)
            val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000)
            val builder = X509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject,
                org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(publicKey.encoded),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}
